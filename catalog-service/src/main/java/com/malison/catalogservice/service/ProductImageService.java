package com.malison.catalogservice.service;

import com.malison.catalogservice.entity.ProductImage;
import org.springframework.web.multipart.MultipartFile;

public interface ProductImageService {

    /** Stores the given file as the product's image, tagged with the given source ("UPLOAD" or "GENERATED"). */
    void uploadImage(long productId, MultipartFile file, String source);

    default void uploadImage(long productId, MultipartFile file) {
        uploadImage(productId, file, "UPLOAD");
    }

    /** Generates an image from the product's own description and persists it against that product directly. */
    void generateImage(long productId);

    /**
     * Stateless preview: generates an image from the given description but does NOT persist
     * anything. Used so the UI can show the user what the AI produced and let them confirm it
     * (or regenerate) before it's actually attached to a product.
     */
    byte[] previewGenerate(String description);

    ProductImage getImage(long productId);
}
