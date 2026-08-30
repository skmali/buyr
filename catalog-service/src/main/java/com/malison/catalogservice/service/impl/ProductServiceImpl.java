package com.malison.catalogservice.service.impl;

import com.malison.catalogservice.entity.Product;
import com.malison.catalogservice.exception.ProductServiceException;
import com.malison.catalogservice.model.ProductRequest;
import com.malison.catalogservice.model.ProductResponse;
import com.malison.catalogservice.repository.ProductRepository;
import com.malison.catalogservice.service.ProductService;
import com.malison.catalogservice.pubsub.ProductMessagePublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import static org.springframework.beans.BeanUtils.*;

@Service
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductMessagePublisher productMessagePublisher;

    @Autowired
    public ProductServiceImpl(ProductRepository productRepository, ProductMessagePublisher productMessagePublisher) {
        this.productRepository = productRepository;
        this.productMessagePublisher = productMessagePublisher;
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
}
