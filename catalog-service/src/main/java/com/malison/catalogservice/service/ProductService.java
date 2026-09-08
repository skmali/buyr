package com.malison.catalogservice.service;

import com.malison.catalogservice.model.ProductRequest;
import com.malison.catalogservice.model.ProductResponse;
import com.malison.catalogservice.model.UpcLookupResponse;
import java.util.List;

public interface ProductService {
    long addProduct(ProductRequest productRequest);

    ProductResponse getProductById(Long id);

    List<ProductResponse> getAllProducts();

    void reduceQuantity(long id, int quantity);

    UpcLookupResponse lookupUpc(String code);
}
