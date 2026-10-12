package com.malison.catalogui.controller;

import com.malison.catalogui.model.Cart;
import com.malison.catalogui.model.CartItem;
import com.malison.catalogui.model.OrderRequest;
import com.malison.catalogui.model.OrderResponse;
import com.malison.catalogui.model.ProductRequest;
import com.malison.catalogui.model.ProductResponse;
import com.malison.catalogui.model.UpcLookupResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
    public String addProduct(@ModelAttribute ProductRequest request,
                              @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                              @RequestParam(value = "imageSource", required = false, defaultValue = "none") String imageSource,
                              Model model) {
        log.info("UI request: add product through backend: {}", request);
        try {
            Long productId = restTemplate.postForObject(backendUrl, request, Long.class);
            if (productId != null) {
                attachImage(productId, imageSource, imageFile);
                ProductResponse product = restTemplate.getForObject(backendUrl + "/" + productId, ProductResponse.class);
                model.addAttribute("product", product);
                return "fragments/product-row :: product-row";
            }
        } catch (Exception e) {
            log.error("Failed to add product: {}", e.getMessage());
        }
        return "fragments/product-row :: error-toast";
    }

    /**
     * Best-effort: the product itself is already created at this point, so a failed
     * image upload/generation never fails the whole "add product" request - it just
     * leaves the product without an image (card falls back to the placeholder).
     */
    private void attachImage(long productId, String imageSource, MultipartFile imageFile) {
        try {
            if ("upload".equals(imageSource) && imageFile != null && !imageFile.isEmpty()) {
                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                body.add("file", new ByteArrayResource(imageFile.getBytes()) {
                    @Override
                    public String getFilename() {
                        return imageFile.getOriginalFilename();
                    }
                });
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                restTemplate.postForObject(backendUrl + "/" + productId + "/image", new HttpEntity<>(body, headers), ProductResponse.class);
            } else if ("generate".equals(imageSource)) {
                restTemplate.postForObject(backendUrl + "/" + productId + "/image/generate", null, ProductResponse.class);
            }
        } catch (IOException e) {
            log.error("Failed to read uploaded image bytes for product {}: {}", productId, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to attach image ({}) to product {}: {}", imageSource, productId, e.getMessage());
        }
    }

    /** Streams a product's image bytes from CatalogService to the browser. */
    @GetMapping("/products/{id}/image")
    public ResponseEntity<byte[]> productImage(@PathVariable long id) {
        try {
            ResponseEntity<byte[]> response = restTemplate.getForEntity(backendUrl + "/" + id + "/image", byte[].class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(response.getHeaders().getContentType() != null
                            ? response.getHeaders().getContentType() : MediaType.APPLICATION_OCTET_STREAM)
                    .body(response.getBody());
        } catch (Exception e) {
            log.error("Failed to fetch image for product {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
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

    @GetMapping("/products/lookup-upc/{code}")
    @ResponseBody
    public UpcLookupResponse lookupUpc(@PathVariable String code) {
        log.info("UI request: look up UPC {} via CatalogService", code);
        try {
            UpcLookupResponse result = restTemplate.getForObject(backendUrl + "/lookup-upc/" + code, UpcLookupResponse.class);
            if (result != null) {
                return result;
            }
        } catch (Exception e) {
            log.error("UPC lookup proxy failed for {}: {}", code, e.getMessage());
        }
        return UpcLookupResponse.builder().found(false).build();
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
