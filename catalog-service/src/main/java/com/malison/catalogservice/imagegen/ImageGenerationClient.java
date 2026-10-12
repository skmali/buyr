package com.malison.catalogservice.imagegen;

/**
 * Abstraction over whichever AI image-generation provider is configured.
 * Keeping this as a narrow interface means swapping providers (OpenAI,
 * Stability AI, etc.) later is a new implementation + config change, not
 * a redesign of {@code ProductImageService}.
 */
public interface ImageGenerationClient {

    /** Whether this client has everything it needs (e.g. an API key) to actually call the provider. */
    boolean isConfigured();

    /**
     * @param prompt text description of the image to generate
     * @return the raw image bytes (PNG/JPEG)
     * @throws ImageGenerationException if the provider call fails, times out,
     *                                   or isn't configured
     */
    byte[] generate(String prompt);
}
