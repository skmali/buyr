package com.malison.catalogservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Stores the raw bytes for a single product's image, keyed by the owning
 * product's id (shared primary key, one-to-one). Kept in its own table
 * rather than as a column on {@link Product} so the lightweight product
 * list/lookup queries (which are Redis-cached) never have to carry image
 * bytes along for the ride.
 */
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductImage {

    @Id
    private Long productId;

    @Column(columnDefinition = "bytea", nullable = false)
    private byte[] data;

    @Column(nullable = false)
    private String contentType;

    /** "UPLOAD" or "GENERATED" */
    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private Instant createdAt;
}
