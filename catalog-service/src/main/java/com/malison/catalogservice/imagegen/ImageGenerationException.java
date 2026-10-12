package com.malison.catalogservice.imagegen;

/** Internal failure signal from an {@link ImageGenerationClient}; translated to an HTTP response by the caller. */
public class ImageGenerationException extends RuntimeException {

    public ImageGenerationException(String message) {
        super(message);
    }

    public ImageGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
