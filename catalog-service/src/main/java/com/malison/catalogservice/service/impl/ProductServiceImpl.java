package com.malison.catalogservice.service.impl;

import com.malison.catalogservice.entity.Product;
import com.malison.catalogservice.exception.ProductServiceException;
import com.malison.catalogservice.model.ProductRequest;
import com.malison.catalogservice.model.ProductResponse;
import com.malison.catalogservice.model.UpcLookupResponse;
import com.malison.catalogservice.model.external.UpcItemDbResponse;
import com.malison.catalogservice.repository.ProductRepository;
import com.malison.catalogservice.service.ProductService;
import com.malison.catalogservice.pubsub.ProductMessagePublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import static org.springframework.beans.BeanUtils.*;

@Service
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductMessagePublisher productMessagePublisher;
    private final RestTemplate restTemplate;
    private final String upcLookupUrl;

    @Autowired
    public ProductServiceImpl(ProductRepository productRepository,
                               ProductMessagePublisher productMessagePublisher,
                               RestTemplate restTemplate,
                               @Value("${upc-lookup.url:https://api.upcitemdb.com/prod/trial/lookup}") String upcLookupUrl) {
        this.productRepository = productRepository;
        this.productMessagePublisher = productMessagePublisher;
        this.restTemplate = restTemplate;
        this.upcLookupUrl = upcLookupUrl;
    }


    @Override
    public long addProduct(ProductRequest productRequest) {
        log.info("Adding product to the database");
        Product product = Product.builder()
                .name(productRequest.getName())
                .price(productRequest.getPrice())
                .quantity(productRequest.getQuantity())
                .description(productRequest.getDescription())
                .build();
        productRepository.save(product);
        log.info("Product added to the database");
        
        // Publish Redis message
        try {
            productMessagePublisher.publish("Product added: [ID=" + product.getId() + ", Name=" + product.getName() + "]");
        } catch (Exception e) {
            log.error("Failed to publish message to Redis: {}", e.getMessage());
        }
        
        return product.getId();
    }

    @Override
    @Cacheable(value = "product", key = "#id")
    public ProductResponse getProductById(Long id) {
        log.info("Getting product by id: {} (Cache Miss)", id);
        Product product = productRepository.findById(id).orElseThrow(() ->
                new ProductServiceException("Product with id: " + id + " not found", "PRODUCT_NOT_FOUND"));
        ProductResponse productResponse = new ProductResponse();
        copyProperties(product, productResponse);
        return productResponse;
    }

    @Override
    public java.util.List<ProductResponse> getAllProducts() {
        log.info("Getting all products from database");
        return productRepository.findAll().stream()
                .map(product -> {
                    ProductResponse response = new ProductResponse();
                    copyProperties(product, response);
                    return response;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    @org.springframework.cache.annotation.CacheEvict(value = "product", key = "#id")
    public void reduceQuantity(long id, int quantity) {
        log.info("Reducing quantity {} for product ID: {}", quantity, id);
        Product product = productRepository.findById(id).orElseThrow(() ->
                new ProductServiceException("Product with id: " + id + " not found", "PRODUCT_NOT_FOUND"));
        
        if (product.getQuantity() < quantity) {
            throw new ProductServiceException("Product does not have sufficient quantity", "INSUFFICIENT_QUANTITY");
        }
        
        product.setQuantity(product.getQuantity() - quantity);
        productRepository.save(product);
        log.info("Product quantity reduced successfully. Remaining: {}", product.getQuantity());
        
        // Publish updates to Redis pub/sub
        try {
            productMessagePublisher.publish("Product stock updated: [ID=" + product.getId() + ", NewStock=" + product.getQuantity() + "]");
        } catch (Exception e) {
            log.error("Failed to publish stock update to Redis: {}", e.getMessage());
        }
    }

    @Override
    public UpcLookupResponse lookupUpc(String code) {
        log.info("Looking up UPC {} against external product database", code);
        try {
            String url = upcLookupUrl + "?upc=" + code;
            UpcItemDbResponse response = restTemplate.getForObject(url, UpcItemDbResponse.class);
            if (response != null && "OK".equals(response.getCode())
                    && response.getItems() != null && !response.getItems().isEmpty()) {
                UpcItemDbResponse.Item item = response.getItems().get(0);
                String description = (item.getDescription() != null && !item.getDescription().isBlank())
                        ? item.getDescription()
                        : (item.getBrand() != null ? item.getBrand() : "");
                double price = item.getLowestRecordedPrice() != null ? item.getLowestRecordedPrice() : 0.0;

                log.info("UPC lookup found a match for {}: {}", code, item.getTitle());
                return UpcLookupResponse.builder()
                        .found(true)
                        .name(truncate(item.getTitle(), 255))
                        .description(truncate(description, 2000))
                        .price(price)
                        .quantity(10)
                        .build();
            }
            log.info("UPC lookup found no match for {}", code);
        } catch (Exception e) {
            log.error("UPC lookup failed for {}: {}", code, e.getMessage());
        }
        return UpcLookupResponse.builder().found(false).build();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
