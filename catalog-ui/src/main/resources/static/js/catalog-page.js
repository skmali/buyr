// Page glue for the catalog layout: the "New product" sheet, header search, revealing the cart,
// acknowledging add-to-cart, and the two-step "Place order" confirmation.

const productSheet = UiMotion.createSheet("product-sheet");

function openProductSheet() {
    if (!productSheet) {
        return;
    }
    const wasOpen = productSheet.isOpen;
    productSheet.open();
    if (!wasOpen) {
        const nameField = document.getElementById("name");
        if (nameField) nameField.focus({ preventScroll: true });
    }
}

function closeProductSheet() {
    if (productSheet) productSheet.close();
}

// The server sends "HX-Trigger: product-added" only when the product was really created (both
// outcomes are a 200). On failure the form is left intact so nothing typed is lost.
document.body.addEventListener("product-added", () => {
    const form = document.getElementById("add-product-form");
    const name = document.getElementById("name").value.trim();
    form.reset();
    resetImagePanel();
    closeProductSheet();
    showToast(name ? `"${name}" added to the catalog.` : "Product added to the catalog.", "success");
});

// ---- Search ----

function filterProducts() {
    filterCatalogWithQuery(document.getElementById("product-search-input").value);
}

function filterCatalogWithQuery(rawQuery) {
    const query = (rawQuery || "").trim().toLowerCase();
    const cards = document.querySelectorAll("#product-grid-container > div");
    let visible = 0;

    cards.forEach((card) => {
        const title = (card.querySelector("h3") || {}).textContent || "";
        const desc = (card.querySelector("p") || {}).textContent || "";
        const matches = !query || title.toLowerCase().includes(query) || desc.toLowerCase().includes(query);
        card.style.display = matches ? "" : "none";
        if (matches) visible++;
    });

    const noResults = document.getElementById("catalog-no-results");
    if (noResults) {
        noResults.classList.toggle("hidden", !(query && cards.length && visible === 0));
        document.getElementById("catalog-no-results-query").textContent = rawQuery.trim();
    }

    const clearBtn = document.getElementById("search-clear-btn");
    if (clearBtn) clearBtn.classList.toggle("hidden", !query);

    updateCatalogCount(query ? visible : null);
}

function clearSearch() {
    const input = document.getElementById("product-search-input");
    input.value = "";
    filterProducts();
    input.focus();
}

function updateCatalogCount(visible) {
    const label = document.getElementById("catalog-count");
    if (!label) {
        return;
    }
    const total = document.querySelectorAll("#product-grid-container > div").length;
    const noun = total === 1 ? "product" : "products";
    label.textContent = visible === null || visible === undefined
        ? `${total} ${noun}`
        : `${visible} of ${total} ${noun}`;
}

function smoothScrollToCatalog() {
    const anchor = document.getElementById("catalog-section-anchor");
    if (anchor) {
        anchor.scrollIntoView({ behavior: UiMotion.reducedMotion.matches ? "auto" : "smooth" });
    }
}

// "/" jumps to search from anywhere that isn't already a text field
document.addEventListener("keydown", (e) => {
    if (e.key !== "/" || e.metaKey || e.ctrlKey || e.altKey) {
        return;
    }
    const target = e.target;
    if (target.closest && target.closest("input, textarea, select, [contenteditable='true']")) {
        return;
    }
    e.preventDefault();
    document.getElementById("product-search-input").focus();
});

// ---- Cart ----

// The header cart button: the cart is always in the right rail on desktop and below the catalog
// on small screens - bring it into view and briefly highlight it either way.
function revealCart() {
    const cart = document.getElementById("cart-sidebar-container");
    if (!cart) {
        return;
    }
    cart.scrollIntoView({ behavior: UiMotion.reducedMotion.matches ? "auto" : "smooth", block: "nearest" });
    cart.classList.remove("cart-flash");
    void cart.offsetWidth; // restart the highlight if it's already running
    cart.classList.add("cart-flash");
}

// Swap "Add" for a check for a moment, right on the button that was pressed
function acknowledgeAddToCart(button, successful) {
    if (!successful) {
        return;
    }
    const label = button.querySelector("[data-label]");
    const icon = button.querySelector("i");
    clearTimeout(button._ackTimer);
    button.dataset.added = "true";
    if (label) label.textContent = "Added";
    if (icon) icon.className = "fa-solid fa-check";
    button._ackTimer = setTimeout(() => {
        delete button.dataset.added;
        if (label) label.textContent = "Add";
        if (icon) icon.className = "fa-solid fa-cart-plus";
    }, 1400);
}

// Pop the header badge when the cart count goes up, so the effect is tied to its cause
let lastCartCount = null;
function syncCartBadge() {
    const badge = document.getElementById("cart-badge-count");
    if (!badge) {
        return;
    }
    const count = parseInt(badge.textContent, 10) || 0;
    if (lastCartCount !== null && count > lastCartCount && !UiMotion.reducedMotion.matches) {
        badge.animate(
            [{ transform: "scale(1)" }, { transform: "scale(1.35)" }, { transform: "scale(1)" }],
            { duration: 320, easing: "cubic-bezier(0.32, 0.72, 0, 1)" }
        );
    }
    lastCartCount = count;
}
document.addEventListener("htmx:afterSwap", syncCartBadge);
syncCartBadge();

// For actions that can't be undone (placing an order, deleting a product): the first press arms
// the button and the second commits by firing "confirmed", which the button's hx-trigger listens for.
// The armed state reverts on its own if the user walks away.
function confirmAction(button) {
    const label = button.querySelector("[data-label]");
    if (button.dataset.confirming === "true") {
        clearTimeout(button._confirmTimer);
        delete button.dataset.confirming;
        label.textContent = button._originalLabel;
        htmx.trigger(button, "confirmed");
        return;
    }
    button.dataset.confirming = "true";
    button._originalLabel = label.textContent;
    label.textContent = button.dataset.confirmLabel || "Tap again to confirm";
    button._confirmTimer = setTimeout(() => {
        delete button.dataset.confirming;
        label.textContent = button._originalLabel;
    }, 4000);
}

// ---- Delete product ----

// Sent by the server (HX-Trigger) only once the product is really gone, so the card stays put on failure
document.body.addEventListener("product-deleted", (e) => {
    const card = document.getElementById(`product-card-${e.detail.id}`);
    if (!card) {
        return;
    }
    // Cards rendered with the page sit in a wrapper div; cards added later are direct grid children
    const cell = card.closest("#product-grid-container > div") || card;
    const name = ((card.querySelector("h3") || {}).textContent || "").trim();

    let finished = false;
    const finish = () => {
        if (finished) return;
        finished = true;
        cell.remove();
        filterProducts();
        const emptyState = document.getElementById("empty-catalog-state");
        if (emptyState) {
            emptyState.classList.toggle("hidden", document.querySelectorAll("#product-grid-container > div").length > 0);
        }
        showToast(name ? `"${name}" deleted.` : "Product deleted.", "success");
    };

    if (UiMotion.reducedMotion.matches) {
        finish();
        return;
    }
    cell.style.pointerEvents = "none";
    cell.animate(
        [{ opacity: 1, transform: "scale(1)" }, { opacity: 0, transform: "scale(0.94)" }],
        { duration: 200, easing: "cubic-bezier(0.4, 0, 1, 1)", fill: "forwards" }
    ).finished.then(finish, finish);
    // Animation promises wait for a rendered frame, which never comes in a background tab
    setTimeout(finish, 300);
});

// After a new product is swapped into the grid, the count (and any active filter) needs a refresh
document.addEventListener("htmx:afterSwap", (e) => {
    if (e.detail.target && e.detail.target.id === "product-grid-container") {
        filterProducts();
    }
});
