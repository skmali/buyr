package com.malison.catalogservice.service.impl;

import com.malison.catalogservice.entity.Product;
import com.malison.catalogservice.entity.ProductImage;
import com.malison.catalogservice.exception.ImageServiceException;
import com.malison.catalogservice.exception.ProductServiceException;
import com.malison.catalogservice.imagegen.ImageGenerationClient;
import com.malison.catalogservice.imagegen.ImageGenerationException;
import com.malison.catalogservice.pubsub.ProductMessagePublisher;
import com.malison.catalogservice.repository.ProductImageRepository;
import com.malison.catalogservice.repository.ProductRepository;
import com.malison.catalogservice.service.ProductImageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;

@Service
@Slf4j
public class ProductImageServiceImpl implements ProductImageService {

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024; // 5 MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ImageGenerationClient imageGenerationClient;
    private final ProductMessagePublisher productMessagePublisher;

    @Autowired
    public ProductImageServiceImpl(ProductRepository productRepository,
                                    ProductImageRepository productImageRepository,
                                    ImageGenerationClient imageGenerationClient,
                                    ProductMessagePublisher productMessagePublisher) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.imageGenerationClient = imageGenerationClient;
        this.productMessagePublisher = productMessagePublisher;
    }

    @Override
    @CacheEvict(value = "product", key = "#productId")
    public void uploadImage(long productId, MultipartFile file) {
        requireProductExists(productId);

        if (file == null || file.isEmpty()) {
            throw new ImageServiceException("No image file was provided", "INVALID_IMAGE", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new ImageServiceException("Image exceeds the 5MB size limit", "INVALID_IMAGE", HttpStatus.BAD_REQUEST);
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ImageServiceException("Could not read uploaded image", "INVALID_IMAGE", HttpStatus.BAD_REQUEST);
        }

        // Never trust the client-supplied Content-Type header alone - sniff the real format from the bytes.
        String sniffedType = sniffContentType(bytes);
        if (sniffedType == null || !ALLOWED_CONTENT_TYPES.contains(sniffedType)) {
            throw new ImageServiceException(
                    "Unsupported image format - only PNG, JPEG, and WebP are allowed", "INVALID_IMAGE", HttpStatus.BAD_REQUEST);
        }

        saveImage(productId, bytes, sniffedType, "UPLOAD");
        log.info("Stored uploaded image for product ID: {} ({} bytes, {})", productId, bytes.length, sniffedType);
    }

    @Override
    @CacheEvict(value = "product", key = "#productId")
    public void generateImage(long productId) {
        Product product = productRepository.findById(productId).orElseThrow(() ->
                new ProductServiceException("Product with id: " + productId + " not found", "PRODUCT_NOT_FOUND"));

        String description = product.getDescription();
        if (description == null || description.isBlank()) {
            throw new ImageServiceException(
                    "Product has no description to generate an image from", "IMAGE_GENERATION_FAILED", HttpStatus.BAD_REQUEST);
        }

        if (!imageGenerationClient.isConfigured()) {
            throw new ImageServiceException(
                    "Image generation is not configured on this server (missing API key)",
                    "IMAGE_GENERATION_NOT_CONFIGURED", HttpStatus.SERVICE_UNAVAILABLE);
        }

        String prompt = "Product photo of: " + truncate(description, 800);

        byte[] bytes;
        try {
            bytes = imageGenerationClient.generate(prompt);
        } catch (ImageGenerationException e) {
            log.error("Image generation failed for product ID {}: {}", productId, e.getMessage());
            throw new ImageServiceException(
                    "Image generation failed: " + e.getMessage(), "IMAGE_GENERATION_FAILED", HttpStatus.BAD_GATEWAY);
        }

        String contentType = sniffContentType(bytes);
        if (contentType == null) {
            contentType = "image/png"; // providers return valid images; default if sniffing is inconclusive
        }

        saveImage(productId, bytes, contentType, "GENERATED");
        log.info("Stored AI-generated image for product ID: {} ({} bytes)", productId, bytes.length);
    }

    @Override
    public ProductImage getImage(long productId) {
        return productImageRepository.findById(productId).orElseThrow(() ->
                new ImageServiceException("No image found for product " + productId, "IMAGE_NOT_FOUND", HttpStatus.NOT_FOUND));
    }

    private void requireProductExists(long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ProductServiceException("Product with id: " + productId + " not found", "PRODUCT_NOT_FOUND");
        }
    }

    private void saveImage(long productId, byte[] bytes, String contentType, String source) {
        ProductImage image = ProductImage.builder()
                .productId(productId)
                .data(bytes)
                .contentType(contentType)
                .source(source)
                .createdAt(Instant.now())
                .build();
        productImageRepository.save(image);

        try {
            productMessagePublisher.publish("Product image " + source.toLowerCase() + ": [ID=" + productId + "]");
        } catch (Exception e) {
            log.error("Failed to publish image update to Redis: {}", e.getMessage());
        }
    }

    /** Sniffs the real image format from its magic bytes; returns null if none of the allowed formats match. */
    private static String sniffContentType(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        if (startsWith(bytes, 0x89, 0x50, 0x4E, 0x47)) {
            return "image/png";
        }
        if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (bytes.length >= 12
                && startsWith(bytes, 'R', 'I', 'F', 'F')
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[i] & 0xFF) != (signature[i] & 0xFF)) {
                return false;
            }
        }
        return true;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
