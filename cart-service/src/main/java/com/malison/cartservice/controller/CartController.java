package com.malison.cartservice.controller;

import com.malison.cartservice.model.Cart;
import com.malison.cartservice.model.CartItem;
import com.malison.cartservice.model.ProductResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;

@RestController
@RequestMapping("/api/cart")
@Slf4j
public class CartController {

    private final RedisTemplate<String, Cart> redisTemplate;
    private final RestTemplate restTemplate;
    private final String catalogServiceUrl;
    private static final String CART_PREFIX = "cart:";

    @Autowired
    public CartController(RedisTemplate<String, Cart> redisTemplate,
                           RestTemplate restTemplate,
                           @Value("${catalog-service.url:http://CatalogService/api/product}") String catalogServiceUrl) {
        this.redisTemplate = redisTemplate;
        this.restTemplate = restTemplate;
        this.catalogServiceUrl = catalogServiceUrl;
    }

    @GetMapping("/{cartId}")
    public ResponseEntity<Cart> getCart(@PathVariable String cartId) {
        log.info("Fetching cart with ID: {}", cartId);
        String key = CART_PREFIX + cartId;
        Cart cart = redisTemplate.opsForValue().get(key);
        if (cart == null) {
            cart = Cart.builder().cartId(cartId).items(new ArrayList<>()).build();
        }
        return ResponseEntity.ok(cart);
    }

    @PostMapping("/{cartId}/add")
    public ResponseEntity<Cart> addToCart(@PathVariable String cartId, @RequestBody CartItem newItem) {
        log.info("Adding item to cart {}: {}", cartId, newItem);

        ProductResponse product;
        try {
            product = restTemplate.getForObject(catalogServiceUrl + "/" + newItem.getProductId(), ProductResponse.class);
        } catch (RestClientException e) {
            log.error("Failed to fetch product {} from CatalogService: {}", newItem.getProductId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (product == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        newItem.setProductName(product.getName());
        newItem.setPrice(product.getPrice());

        String key = CART_PREFIX + cartId;
        Cart cart = redisTemplate.opsForValue().get(key);
        if (cart == null) {
            cart = Cart.builder().cartId(cartId).items(new ArrayList<>()).build();
        }

        boolean found = false;
        for (CartItem item : cart.getItems()) {
            if (item.getProductId() == newItem.getProductId()) {
                item.setQuantity(item.getQuantity() + newItem.getQuantity());
                found = true;
                break;
            }
        }

        if (!found) {
            cart.getItems().add(newItem);
        }

        redisTemplate.opsForValue().set(key, cart);
        return ResponseEntity.ok(cart);
    }

    @DeleteMapping("/{cartId}/remove/{productId}")
    public ResponseEntity<Cart> removeFromCart(@PathVariable String cartId, @PathVariable long productId) {
        log.info("Removing product {} from cart {}", productId, cartId);
        String key = CART_PREFIX + cartId;
        Cart cart = redisTemplate.opsForValue().get(key);
        if (cart != null) {
            cart.getItems().removeIf(item -> item.getProductId() == productId);
            redisTemplate.opsForValue().set(key, cart);
        } else {
            cart = Cart.builder().cartId(cartId).items(new ArrayList<>()).build();
        }
        return ResponseEntity.ok(cart);
    }

    @DeleteMapping("/{cartId}")
    public ResponseEntity<Void> clearCart(@PathVariable String cartId) {
        log.info("Clearing cart: {}", cartId);
        String key = CART_PREFIX + cartId;
        redisTemplate.delete(key);
        return ResponseEntity.ok().build();
    }
}
