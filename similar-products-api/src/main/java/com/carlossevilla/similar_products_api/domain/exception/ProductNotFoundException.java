package com.carlossevilla.similar_products_api.domain.exception;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String productId) {
        super("Product not found: " + productId);
    }

    public ProductNotFoundException(String productId, Throwable cause) {
        super("Product not found: " + productId, cause);
    }

}
