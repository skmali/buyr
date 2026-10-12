package com.malison.catalogservice.service;

import com.malison.catalogservice.entity.ProductImage;
import org.springframework.web.multipart.MultipartFile;

public interface ProductImageService {

    void uploadImage(long productId, MultipartFile file);

    void generateImage(long productId);

    ProductImage getImage(long productId);
}
