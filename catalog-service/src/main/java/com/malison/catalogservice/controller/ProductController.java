package com.malison.catalogservice.controller;

import com.malison.catalogservice.entity.ProductImage;
import com.malison.catalogservice.model.ImageGeneratePreviewRequest;
import com.malison.catalogservice.model.ProductRequest;
import com.malison.catalogservice.model.ProductResponse;
import com.malison.catalogservice.model.UpcLookupResponse;
import com.malison.catalogservice.service.ProductImageService;
import com.malison.catalogservice.service.ProductService;
import com.malison.catalogservice.util.ImageTypeDetector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

@RestController
@RequestMapping("/api/product")
public class ProductController {


    private final ProductService productService;
    private final ProductImageService productImageService;

    @Autowired
    public ProductController(ProductService productService, ProductImageService productImageService) {
        this.productService = productService;
        this.productImageService = productImageService;
    }

    @PostMapping
    public ResponseEntity<Long> addProduct(@RequestBody ProductRequest productRequest) {
        long productId = productService.addProduct(productRequest);
        return new ResponseEntity<>(productId, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<java.util.List<ProductResponse>> getAllProducts() {
        java.util.List<ProductResponse> products = productService.getAllProducts();
        return new ResponseEntity<>(products, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable("id") Long id) {
        ProductResponse productResponse =
                productService.getProductById(id);
        return new ResponseEntity<>(productResponse, HttpStatus.OK);
    }

    @PutMapping("/reduceQuantity/{id}")
    public ResponseEntity<Void> reduceQuantity(@PathVariable("id") Long id, @RequestParam("quantity") int quantity) {
        productService.reduceQuantity(id, quantity);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping("/lookup-upc/{code}")
    public ResponseEntity<UpcLookupResponse> lookupUpc(@PathVariable("code") String code) {
        UpcLookupResponse result = productService.lookupUpc(code);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @PostMapping("/{id}/image")
    public ResponseEntity<ProductResponse> uploadImage(@PathVariable("id") Long id,
                                                         @RequestParam("file") MultipartFile file,
                                                         @RequestParam(value = "source", required = false, defaultValue = "UPLOAD") String source) {
        productImageService.uploadImage(id, file, source);
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @PostMapping("/{id}/image/generate")
    public ResponseEntity<ProductResponse> generateImage(@PathVariable("id") Long id) {
        productImageService.generateImage(id);
        return ResponseEntity.ok(productService.getProductById(id));
    }

    /**
     * Stateless preview: generates an image from the given description without persisting
     * anything or requiring a product to already exist. Lets the UI show the user what the AI
     * produced and get their confirmation before it's attached to a product.
     */
    @PostMapping("/image/generate")
    public ResponseEntity<byte[]> generatePreview(@RequestBody ImageGeneratePreviewRequest request) {
        byte[] bytes = productImageService.previewGenerate(request.getDescription());
        String contentType = ImageTypeDetector.detect(bytes);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType != null ? contentType : "image/png"))
                .body(bytes);
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> getImage(@PathVariable("id") Long id) {
        ProductImage image = productImageService.getImage(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                .body(image.getData());
    }
}
