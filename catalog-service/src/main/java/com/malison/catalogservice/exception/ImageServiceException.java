package com.malison.catalogservice.exception;

import lombok.Data;
import org.springframework.http.HttpStatus;

/**
 * Raised for any image upload/generation/fetch failure. Unlike
 * {@link ProductServiceException} (always mapped to 404 today), this
 * carries its own {@link HttpStatus} since image errors span several
 * distinct cases (bad upload -> 400, no image set -> 404, generation
 * unavailable -> 503, generation failed -> 502).
 */
@Data
public class ImageServiceException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    public ImageServiceException(String message, String errorCode, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}
