package com.malison.orderservice.controller;

import com.malison.orderservice.entity.Order;
import com.malison.orderservice.model.OrderRequest;
import com.malison.orderservice.model.OrderResponse;
import com.malison.orderservice.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/order")
@Slf4j
public class OrderController {

    private final OrderRepository orderRepository;
    private final RestTemplate restTemplate;
    private final String catalogServiceUrl;

    @Autowired
    public OrderController(OrderRepository orderRepository,
                           RestTemplate restTemplate,
                           @Value("${catalog-service.url:http://CatalogService/api/product}") String catalogServiceUrl) {
        this.orderRepository = orderRepository;
        this.restTemplate = restTemplate;
        this.catalogServiceUrl = catalogServiceUrl;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@RequestBody OrderRequest orderRequest) {
        log.info("Placing order for product: {}, quantity: {}", orderRequest.getProductId(), orderRequest.getQuantity());

        // 1. Call CatalogService to reduce stock quantity
        try {
            String reduceUrl = catalogServiceUrl + "/reduceQuantity/" + orderRequest.getProductId() + "?quantity=" + orderRequest.getQuantity();
            log.info("Calling CatalogService URL: {}", reduceUrl);
            restTemplate.put(reduceUrl, null);
        } catch (Exception e) {
            log.error("Failed to reduce stock for product {}: {}", orderRequest.getProductId(), e.getMessage());
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        // 2. Compute total amount
        double totalAmount = orderRequest.getPrice() * orderRequest.getQuantity();

        // 3. Save order to database
        Order order = Order.builder()
                .productId(orderRequest.getProductId())
                .quantity(orderRequest.getQuantity())
                .price(orderRequest.getPrice())
                .totalAmount(totalAmount)
                .orderDate(Instant.now())
                .orderStatus("PLACED")
                .build();

        orderRepository.save(order);
        log.info("Order saved successfully with ID: {}", order.getId());

        OrderResponse response = OrderResponse.builder()
                .id(order.getId())
                .productId(order.getProductId())
                .quantity(order.getQuantity())
                .price(order.getPrice())
                .totalAmount(order.getTotalAmount())
                .orderDate(order.getOrderDate())
                .orderStatus(order.getOrderStatus())
                .build();

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        log.info("Fetching all orders");
        List<OrderResponse> orders = orderRepository.findAll().stream()
                .map(order -> OrderResponse.builder()
                        .id(order.getId())
                        .productId(order.getProductId())
                        .quantity(order.getQuantity())
                        .price(order.getPrice())
                        .totalAmount(order.getTotalAmount())
                        .orderDate(order.getOrderDate())
                        .orderStatus(order.getOrderStatus())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(orders);
    }
}
