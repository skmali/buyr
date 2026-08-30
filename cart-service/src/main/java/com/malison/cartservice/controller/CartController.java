package com.malison.cartservice.controller;

import com.malison.cartservice.model.Cart;
import com.malison.cartservice.model.CartItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;

@RestController
@RequestMapping("/api/cart")
@Slf4j
public class CartController {

    private final RedisTemplate<String, Cart> redisTemplate;
    private static final String CART_PREFIX = "cart:";

    @Autowired
    public CartController(RedisTemplate<String, Cart> redisTemplate) {
        this.redisTemplate = redisTemplate;
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
