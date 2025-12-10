package com.carlossevilla.similar_products_api.domain.port;

import com.carlossevilla.similar_products_api.domain.model.Product;
import reactor.core.publisher.Mono;

public interface ProductRepository {

    /**
     * Retrieves product details by product ID
     * @param productId the product identifier
     * @return Mono emitting the product or error if not found
     */
    Mono<Product> findById(String productId);

}
