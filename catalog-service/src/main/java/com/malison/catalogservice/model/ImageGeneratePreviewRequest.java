package com.malison.catalogservice.model;

import lombok.Data;

/** Request body for the stateless "generate a preview image from this description" endpoint. */
@Data
public class ImageGeneratePreviewRequest {
    private String description;
}
