package com.malison.catalogservice.imagegen;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Default {@link ImageGenerationClient}: calls Pollinations.ai
 * (https://pollinations.ai), a free, unauthenticated text-to-image API - a
 * single GET request that returns image bytes directly. No API key, no
 * account, no billing, which is why it's the default for this demo app;
 * swap in a different {@link ImageGenerationClient} implementation if you
 * need a specific model/provider instead.
 */
@Component
@Slf4j
public class PollinationsImageGenerationClient implements ImageGenerationClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    @Autowired
    public PollinationsImageGenerationClient(@Qualifier("imageGenRestTemplate") RestTemplate restTemplate,
                                              @Value("${image-generation.base-url:https://image.pollinations.ai/prompt}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    @Override
    public boolean isConfigured() {
        return true; // free public API - nothing to configure
    }

    @Override
    public byte[] generate(String prompt) {
        String encodedPrompt = URLEncoder.encode(prompt, StandardCharsets.UTF_8).replace("+", "%20");
        // nologo=true drops the Pollinations watermark; a fixed size keeps product images consistent on the catalog card.
        String url = baseUrl + "/" + encodedPrompt + "?width=1024&height=1024&nologo=true";

        try {
            byte[] bytes = restTemplate.getForObject(url, byte[].class);
            if (bytes == null || bytes.length == 0) {
                throw new ImageGenerationException("Image generation provider returned no image data");
            }
            return bytes;
        } catch (RestClientException e) {
            log.error("Image generation call failed: {}", e.getMessage());
            throw new ImageGenerationException("Image generation provider call failed: " + e.getMessage(), e);
        }
    }
}
