package com.carlossevilla.similar_products_api.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

public record Product (
    String id,
    String name,
    BigDecimal price,
    boolean availability
) {
    public Product {
        Objects.requireNonNull(id, "Product id cannot be null");
        Objects.requireNonNull(name, "Product name cannot be null");
        Objects.requireNonNull(price, "Product price cannot be null");
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
    }
}
