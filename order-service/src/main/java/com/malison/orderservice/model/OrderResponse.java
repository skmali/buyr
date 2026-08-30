package com.malison.orderservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private long id;
    private long productId;
    private int quantity;
    private double price;
    private double totalAmount;
    private Instant orderDate;
    private String orderStatus;
}
