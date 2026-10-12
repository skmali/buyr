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

    /** Deletes the product and its image. Orders keep their own copy of the price, so they are unaffected. */
    void deleteProduct(long id);
}
