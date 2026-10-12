package com.malison.catalogservice.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * Dedicated RestTemplate for calls to the image-generation provider.
     * AI image generation is slow and external (the free Pollinations.ai API
     * in particular can take a while under load); a bounded timeout keeps a
     * provider outage/slowdown from hanging the generate-image request
     * indefinitely.
     */
    @Bean
    public RestTemplate imageGenRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(20))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();
    }
}
