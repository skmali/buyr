package com.malison.catalogui.controller;

import com.malison.catalogui.model.Cart;
import com.malison.catalogui.model.CartItem;
import com.malison.catalogui.model.OrderRequest;
import com.malison.catalogui.model.OrderResponse;
import com.malison.catalogui.model.ProductRequest;
import com.malison.catalogui.model.ProductResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/")
@Slf4j
public class ProductUiController {

    private final RestTemplate restTemplate;
    private final String backendUrl;
    private final String cartServiceUrl;
    private final String orderServiceUrl;
    private static final String CART_ID = "user-cart-1";

    @Autowired
    public ProductUiController(RestTemplate restTemplate,
                               @Value("${catalog-service.url:http://CatalogService/api/product}") String backendUrl,
                               @Value("${cart-service.url:http://CartService/api/cart}") String cartServiceUrl,
                               @Value("${order-service.url:http://OrderService/api/order}") String orderServiceUrl) {
        this.restTemplate = restTemplate;
        this.backendUrl = backendUrl;
        this.cartServiceUrl = cartServiceUrl;
        this.orderServiceUrl = orderServiceUrl;
    }

    @GetMapping
    public String viewCatalog(Model model) {
        log.info("UI request: fetch all products from backend URL: {}", backendUrl);
        List<ProductResponse> products = new ArrayList<>();
        try {
            ResponseEntity<List<ProductResponse>> response = restTemplate.exchange(
                    backendUrl,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<ProductResponse>>() {}
            );
            if (response.getBody() != null) {
                products = response.getBody();
            }
        } catch (Exception e) {
            log.error("Failed to fetch products from backend: {}", e.getMessage());
            model.addAttribute("errorMessage", "Could not reach backend product catalog service.");
        }
        
        // Fetch cart to show cart badge count
        Cart cart = getCartFromService();
        model.addAttribute("cart", cart);

        // Fetch order history
        List<OrderResponse> orders = getOrderHistoryFromService();
        model.addAttribute("orders", orders);

        model.addAttribute("products", products);
        return "products";
    }

    @PostMapping("/products")
    public String addProduct(@ModelAttribute ProductRequest request, Model model) {
        log.info("UI request: add product through backend: {}", request);
        try {
            Long productId = restTemplate.postForObject(backendUrl, request, Long.class);
            if (productId != null) {
                ProductResponse product = restTemplate.getForObject(backendUrl + "/" + productId, ProductResponse.class);
                model.addAttribute("product", product);
                return "fragments/product-row :: product-row";
            }
        } catch (Exception e) {
            log.error("Failed to add product: {}", e.getMessage());
        }
        return "fragments/product-row :: error-toast";
    }

    @PostMapping("/cart/add/{productId}")
    public String addToCart(@PathVariable long productId, @RequestParam(value = "quantity", defaultValue = "1") int quantity, Model model) {
        log.info("UI request: add product {} to cart with qty {}", productId, quantity);
        try {
            // 1. Fetch product details from CatalogService
            ProductResponse product = restTemplate.getForObject(backendUrl + "/" + productId, ProductResponse.class);
            if (product != null) {
                // 2. Build CartItem and post to CartService
                CartItem item = CartItem.builder()
                        .productId(productId)
                        .productName(product.getName())
                        .price(product.getPrice())
                        .quantity(quantity)
                        .build();

                restTemplate.postForObject(cartServiceUrl + "/" + CART_ID + "/add", item, Cart.class);
            }
        } catch (Exception e) {
            log.error("Failed to add item to cart: {}", e.getMessage());
        }

        // Return updated cart fragment
        Cart updatedCart = getCartFromService();
        model.addAttribute("cart", updatedCart);
        return "fragments/product-row :: cart-sidebar";
    }

    @DeleteMapping("/cart/remove/{productId}")
    public String removeFromCart(@PathVariable long productId, Model model) {
        log.info("UI request: remove product {} from cart", productId);
        try {
            restTemplate.delete(cartServiceUrl + "/" + CART_ID + "/remove/" + productId);
        } catch (Exception e) {
            log.error("Failed to remove item from cart: {}", e.getMessage());
        }

        Cart updatedCart = getCartFromService();
        model.addAttribute("cart", updatedCart);
        return "fragments/product-row :: cart-sidebar";
    }

    @PostMapping("/cart/checkout")
    public String checkout(Model model) {
        log.info("UI request: checkout cart {}", CART_ID);
        try {
            // 1. Fetch cart contents
            Cart cart = getCartFromService();
            if (cart != null && !cart.getItems().isEmpty()) {
                // 2. For each item in the cart, place an order via OrderService
                for (CartItem item : cart.getItems()) {
                    OrderRequest orderRequest = OrderRequest.builder()
                            .productId(item.getProductId())
                            .quantity(item.getQuantity())
                            .price(item.getPrice())
                            .build();

                    restTemplate.postForObject(orderServiceUrl, orderRequest, OrderResponse.class);
                }

                // 3. Clear cart
                restTemplate.delete(cartServiceUrl + "/" + CART_ID);
            }
        } catch (Exception e) {
            log.error("Checkout failed: {}", e.getMessage());
        }

        // Return order placement success response with updated blank cart and updated order history
        Cart updatedCart = getCartFromService();
        List<OrderResponse> orders = getOrderHistoryFromService();
        
        model.addAttribute("cart", updatedCart);
        model.addAttribute("orders", orders);
        
        return "fragments/product-row :: checkout-response";
    }

    private Cart getCartFromService() {
        try {
            Cart cart = restTemplate.getForObject(cartServiceUrl + "/" + CART_ID, Cart.class);
            if (cart != null) {
                return cart;
            }
        } catch (Exception e) {
            log.error("Failed to fetch cart from service: {}", e.getMessage());
        }
        return Cart.builder().cartId(CART_ID).items(new ArrayList<>()).build();
    }

    private List<OrderResponse> getOrderHistoryFromService() {
        List<OrderResponse> orders = new ArrayList<>();
        try {
            ResponseEntity<List<OrderResponse>> response = restTemplate.exchange(
                    orderServiceUrl,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<OrderResponse>>() {}
            );
            if (response.getBody() != null) {
                orders = response.getBody();
            }
        } catch (Exception e) {
            log.error("Failed to fetch orders from service: {}", e.getMessage());
        }
        return orders;
    }
}
