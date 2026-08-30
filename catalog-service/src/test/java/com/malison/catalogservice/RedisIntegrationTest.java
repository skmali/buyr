package com.malison.catalogservice;

import com.malison.catalogservice.model.ProductRequest;
import com.malison.catalogservice.model.ProductResponse;
import com.malison.catalogservice.pubsub.ProductMessageSubscriber;
import com.malison.catalogservice.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "eureka.client.enabled=false"
})
class RedisIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductMessageSubscriber productMessageSubscriber;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void setUp() {
        productMessageSubscriber.clearMessages();
        // Clear redis keys related to product cache to ensure clean tests
        try {
            java.util.Set<String> keys = redisTemplate.keys("product::*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            // Ignore if Redis is unreachable but let it fail the test if required
        }
    }

    @Test
    void testProductCreationPublishesMessageAndCachesProduct() throws InterruptedException {
        // 1. Add product using the service
        ProductRequest request = new ProductRequest();
        request.setName("Integration Test Product");
        request.setDescription("A product for testing Redis cache and pub/sub");
        request.setPrice(99.99);
        request.setQuantity(5);

        long productId = productService.addProduct(request);
        assertThat(productId).isGreaterThan(0);

        // 2. Verify Pub/Sub: Wait up to 3 seconds for subscriber to receive the event message
        boolean messageReceived = false;
        for (int i = 0; i < 30; i++) {
            List<String> received = productMessageSubscriber.getMessages();
            if (!received.isEmpty()) {
                assertThat(received.get(0)).contains("Product added")
                        .contains("ID=" + productId)
                        .contains("Name=Integration Test Product");
                messageReceived = true;
                break;
            }
            Thread.sleep(100);
        }
        assertThat(messageReceived).isTrue();

        // 3. Verify Cacheable behavior: Fetch product from service (Cache Miss)
        ProductResponse fetchedProduct1 = productService.getProductById(productId);
        assertThat(fetchedProduct1).isNotNull();
        assertThat(fetchedProduct1.getName()).isEqualTo("Integration Test Product");

        // Verify it is now present in the Redis cache
        String cacheKey = "product::" + productId;
        Boolean hasKey = redisTemplate.hasKey(cacheKey);
        assertThat(hasKey).isTrue();

        // Fetch product from service again (Cache Hit - doesn't hit DB)
        ProductResponse fetchedProduct2 = productService.getProductById(productId);
        assertThat(fetchedProduct2).isNotNull();
        assertThat(fetchedProduct2.getName()).isEqualTo("Integration Test Product");
    }
}
