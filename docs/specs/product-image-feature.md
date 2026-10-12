# Spec: Product Images (Upload + AI-Generated)

| | |
|---|---|
| **Status** | Draft — for review |
| **Author** | Claude Code (drafted with shravan.mali@gmail.com) |
| **Date** | 2026-10-11 |
| **Services touched** | `catalog-service`, `catalog-ui` |

## 1. Problem / Goal

Products in the catalog currently have no visual representation — the
product card (`fragments/product-row.html`) shows only name, description,
price, and quantity. We want to let a user add an image to a new product
in one of two ways:

1. **Upload** an image file directly in the "New Product" form.
2. **Generate** an image automatically from the product's `description`
   text, via an AI image-generation API, with one click.

Either path should end with the product having a displayable image, shown
on the catalog card and the image persisted so it survives restarts.

## 2. User stories

- As a user filling out the "New Product" form, I can attach a photo of
  the product before submitting, and see it on the resulting catalog card.
- As a user who doesn't have a photo, I can click **"Generate image"**
  after typing a description, see a preview, and either accept it or
  regenerate, before submitting the product.
- As a user browsing the catalog, I see each product's image (or a
  neutral placeholder if none was ever set) on its card.

## 3. Scope

**In scope**
- Single image per product (no galleries).
- Manual upload (JPEG/PNG/WebP) on product creation.
- AI-generated image from `description` on product creation.
- Serving/display of the image on the catalog UI.
- Replacing an image is **not** required for v1 (products have no edit
  flow today — only create). Noted as a follow-up in §9.

**Out of scope (v1)**
- Editing/replacing the image of an existing product.
- Multiple images / image galleries.
- Content moderation pipeline beyond basic provider-side safety filters.
- Image CDN / external object storage (S3, etc.) — see §5.3 for why.

## 4. Current architecture (relevant parts)

- `catalog-service` owns `Product` (JPA entity, Postgres, `ddl-auto: update`
  so new nullable columns migrate automatically — no Flyway/Liquibase in
  this repo).
- `getProductById` is `@Cacheable(value = "product")`; `reduceQuantity`
  evicts that cache key. Any new field returned by `GET /api/product/{id}`
  must be in `ProductResponse` or the cached payload will be stale/incomplete.
- `catalog-ui` never calls `catalog-service` from the browser directly —
  all calls are server-side via `RestTemplate` + Eureka logical names
  (`http://CatalogService/...`), and the UI exposes its own proxy
  endpoints (e.g. `GET /products/lookup-upc/{code}` proxies to
  `catalog-service`'s `/api/product/lookup-upc/{code}`). The existing UPC
  autofill feature is the closest precedent for "call an external API,
  show a preview, let the user accept it before submit" — the image
  features follow the same shape.
- No object storage, no static-file volume shared between containers.

## 5. Design

### 5.1 Data model

Add one nullable column to `Product`:

```java
@Column(length = 2000)
private String imageUrl;   // nullable; path the browser can request, e.g. "/api/product/42/image"
```

We do **not** store image bytes in a new BLOB-mapped entity field read
eagerly with the product row — see §5.3 for the actual byte storage
design. `imageUrl` here is really "does this product have an image" plus
the path to fetch it; it's populated by `catalog-service` itself after a
successful upload/generate, not supplied by the client.

`ProductRequest` / `ProductResponse` gain:

```java
// ProductResponse
private boolean hasImage;   // derived, not a DB column — true if image bytes exist
```

(We expose `hasImage` rather than a full URL in the JSON so `catalog-ui`
builds the URL itself, consistent with how it already builds its own
proxy routes.)

### 5.2 New entity: `ProductImage`

```java
@Entity
@Table(name = "product_image")
public class ProductImage {
    @Id
    private long productId;        // shared PK / FK to Product.id, one-to-one

    @Lob
    @Column(columnDefinition = "bytea")
    private byte[] data;

    private String contentType;    // "image/jpeg", "image/png", "image/webp"
    private String source;         // "UPLOAD" | "GENERATED"
    private Instant createdAt;
}
```

Keeping image bytes in a **separate table**, not a column on `Product`,
matters because `getAllProducts()` and `getProductById()` already return
lightweight `ProductResponse` objects and are cached in Redis — we don't
want multi-hundred-KB blobs riding along on every catalog list fetch or
sitting in the Redis cache. The image is fetched by its own endpoint,
on demand, only when the `<img>` tag actually requests it.

### 5.3 Storage decision: Postgres `bytea`, not a file volume or S3

Options considered:

| Option | Verdict |
|---|---|
| Shared filesystem volume between `catalog-service` and `catalog-ui` containers | Rejected — adds a new docker-compose volume just for this, and `catalog-ui` would need direct filesystem access, breaking the "everything crosses service boundaries over HTTP/Eureka" pattern the rest of the app follows. |
| S3 / object storage | Rejected for v1 — real infra dependency, new credentials, overkill for a demo app with no deployment target configured for it yet. Worth revisiting if this goes to a real cloud deployment. |
| **Postgres `bytea` in a dedicated table, streamed via an endpoint** | **Chosen.** No new infra — Postgres is already a dependency. Keeps the microservice boundary intact: the image is just another resource `catalog-service` owns and serves over HTTP, exactly like every other piece of product data. |

### 5.4 API: `catalog-service`

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/product` | *(existing, unchanged)* creates the product row, no image yet |
| `POST` | `/api/product/{id}/image` | Upload: `multipart/form-data`, field `file`. Validates type/size (§7), stores as `ProductImage`, sets `hasImage=true`. |
| `POST` | `/api/product/{id}/image/generate` | Generate: no body (uses the product's stored `description`). Calls the AI image provider (§5.5), stores result as `ProductImage` with `source=GENERATED`. |
| `GET` | `/api/product/{id}/image` | Streams the bytes with the stored `Content-Type`. `404` if no image. `Cache-Control: max-age=3600` since images are immutable once set in v1. |

Both image-write endpoints evict the `product` Redis cache key for that
id (same pattern `reduceQuantity` already uses), since `hasImage` on
`ProductResponse` changes.

### 5.5 AI image generation

New interface so the provider is swappable, mirroring how
`upc-lookup.url` is externalized today:

```java
public interface ImageGenerationClient {
    byte[] generate(String prompt); // returns PNG/JPEG bytes, throws ImageGenerationException
}
```

**Provider (v1, decided): Pollinations.ai** (`image.pollinations.ai/prompt/{prompt}`)
— a free, unauthenticated text-to-image API: a single `GET` request
returns image bytes directly, no API key, account, or billing required.
(§9 originally left this open with OpenAI as the default recommendation;
revisited and switched to Pollinations.ai specifically so this feature
needs no secret to work out of the box — reasonable for a demo app. Any
provider works behind the `ImageGenerationClient` interface above;
swapping to a paid provider for better/more controllable output later is
a config + one new class, not a redesign.)

- **Config** (new, externalized like `upc-lookup.url`):
  ```yaml
  image-generation:
    base-url: https://image.pollinations.ai/prompt
  ```
  No secret, so no env var/`.env` plumbing is needed for this provider.
  `isConfigured()` on `ImageGenerationClient` always returns `true` for
  it; the method still exists on the interface for a future provider that
  *does* need a key, in which case the generate endpoint returns `503`
  with a clear error rather than failing confusingly deep in a
  `RestClientException`.
- **Prompt construction**: `"Product photo of: " + description`,
  truncated to a safe length. No user-supplied prompt field in v1 — the
  description is the only input, which also sidesteps needing a separate
  prompt-injection/moderation review for freeform prompts.
- **Timeout**: explicit `RestTemplate` timeout (20s connect / 60s read —
  Pollinations.ai is a free, best-effort service and can be slow under
  load) — image generation is slow, and the UI's synchronous "Generate"
  click needs a bound so it doesn't hang indefinitely.
- **Failure handling**: on timeout/error, the endpoint returns a
  structured error (`{"errorCode":"IMAGE_GENERATION_FAILED"}`, `502`);
  the UI shows a toast (reusing the existing `error-toast` fragment
  pattern) and the user can retry or fall back to uploading manually.
- **Cost control**: this is a demo app behind no auth — add a simple
  per-request guard (e.g. reject if `description` is blank) and note in
  §9 that there's no rate limiting; acceptable for local/demo use, called
  out explicitly as a gap if this is ever exposed publicly.

### 5.6 `catalog-ui` changes

**Form** (`products.html`, "New Product" panel): add, below the
description field,

- A file input (`<input type="file" name="imageFile" accept="image/png,image/jpeg,image/webp">`) for upload, with a small live `<img>` preview (plain JS `FileReader`, no new JS deps needed since `scanner.js` already does similar canvas/DOM work).
- A **"Generate from description"** button (disabled until `description` is non-empty) that calls the new proxy endpoint and swaps in a preview `<img>`, with a spinner while the HTMX request is in flight (`hx-indicator`, same mechanism already usable with HTMX).
- Only one of the two image sources applies per product; picking one clears/disables the other in the form (simple JS toggle, same style as `scanner.js`'s tab switching).

**Submit flow**: product creation becomes two requests from
`ProductUiController.addProduct`, in sequence, mirroring how
`checkout()` already does multi-step orchestration:
1. `POST` the product fields to `catalog-service` (unchanged, returns `id`).
2. If an image file was attached: `POST multipart/form-data` to
   `/api/product/{id}/image`. If "Generate" was used instead: `POST`
   to `/api/product/{id}/image/generate` (re-running generation server
   side so the final stored image is authoritative — the form preview
   from step 5.6 is just a UX preview, not necessarily byte-identical,
   unless we instead submit the already-previewed bytes; see Open
   Question in §9).
3. Return the `product-row` fragment as today — now rendering an `<img>`.

New UI proxy endpoints (same pass-through pattern as `lookupUpc`):
- `POST /products/{id}/image` (upload passthrough)
- `POST /products/{id}/image/generate` (generate passthrough)
- `GET /products/{id}/image` (stream passthrough — the browser's `<img src>` hits this, which proxies bytes from `catalog-service`; keeps the browser never talking to `catalog-service` directly, consistent with every other feature).

**Product card** (`fragments/product-row.html`): add an `<img>` above
the title, `th:src="@{'/products/' + ${product.id} + '/image'}"`, with an
`onerror` fallback to a static placeholder
(`/img/placeholder-product.png`, new static asset) so a product created
before this feature — or one whose generation failed — still renders
cleanly.

## 6. Sequence (generate-from-description path)

```mermaid
sequenceDiagram
    participant Browser
    participant UI as catalog-ui
    participant Catalog as catalog-service
    participant AI as Image Gen API

    Browser->>UI: POST /products (name, description, price, qty)
    UI->>Catalog: POST /api/product
    Catalog-->>UI: 201 {id}
    Browser->>UI: click "Generate from description"
    UI->>Catalog: POST /api/product/{id}/image/generate
    Catalog->>AI: generate(prompt from description)
    AI-->>Catalog: image bytes
    Catalog->>Catalog: store ProductImage, evict cache
    Catalog-->>UI: 200 {hasImage:true}
    UI-->>Browser: product-row fragment with <img src="/products/{id}/image">
    Browser->>UI: GET /products/{id}/image
    UI->>Catalog: GET /api/product/{id}/image
    Catalog-->>UI: image bytes
    UI-->>Browser: image bytes
```

## 7. Non-functional requirements

- **File validation (upload)**: content-type whitelist (`image/png`,
  `image/jpeg`, `image/webp`), max size **5 MB**, rejected with `400` +
  structured error, reusing the `ProductServiceException` pattern
  (`errorCode: "INVALID_IMAGE"`).
- **Multipart config**: Spring Boot default max multipart size (1 MB) is
  too small — raise `spring.servlet.multipart.max-file-size` /
  `max-request-size` to `5MB` in both `catalog-service` and `catalog-ui`.
- **Timeouts**: as noted in §5.5, bound the AI call explicitly.
- **Security**: no new attack surface beyond standard file-upload
  hygiene (type/size validation, never trust the client-supplied
  `Content-Type` header alone — sniff magic bytes server-side too). API
  key stays server-side only, never sent to the browser.
- **Backward compatibility**: existing products (no `ProductImage` row)
  simply get `hasImage=false` / `404` on the image endpoint → UI falls
  back to the placeholder. No backfill needed.

## 8. Testing plan

- Unit: `ProductServiceImpl` — image upload validation (type/size
  rejection), cache eviction on image write, `ImageGenerationClient`
  failure → `502` mapping.
- Integration: upload round-trip (`POST` then `GET` the same bytes
  back); generate round-trip with a mocked `ImageGenerationClient`.
- Manual/E2E (via the `run` skill once implemented): create a product
  with an uploaded image, create one with a generated image, confirm
  both render on the catalog card and survive a `docker compose restart`.

## 9. Open questions

1. **Re-generate vs. reuse preview bytes**: should the "Generate" button
   in the form generate once and submit those exact bytes with the
   product (fewer AI calls, but needs temp storage before the product
   `id` exists), or regenerate server-side after product creation as
   described in §5.6 (simpler, costs a second AI call if the user
   regenerated a few times while drafting)? **Recommendation**: start
   with regenerate-on-submit (simpler); revisit if AI cost/latency is a
   problem.
2. ~~**Which AI provider**~~ — **Resolved**: Pollinations.ai (§5.5), a
   free unauthenticated API, specifically so the feature needs no API key
   to work. Revisit if its output quality/reliability isn't good enough
   — the `ImageGenerationClient` interface makes swapping providers a
   small, contained change.
3. **Editing an existing product's image** is explicitly out of scope
   (§3) — confirm that's acceptable given the app has no edit flow at
   all today.

## 10. Rollout plan

1. **Phase 1 — Upload only**: `ProductImage` entity, upload/fetch
   endpoints, UI form + card changes, placeholder fallback. Ships value
   independently and de-risks the storage/serving design before adding
   the AI dependency.
2. **Phase 2 — AI generation**: `ImageGenerationClient`, generate
   endpoint, UI "Generate" button + preview. Additive — Phase 1 continues
   to work unchanged.
