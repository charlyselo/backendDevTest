package com.carlossevilla.similar_products_api.application.service;

import com.carlossevilla.similar_products_api.domain.exception.ProductNotFoundException;
import com.carlossevilla.similar_products_api.domain.model.Product;
import com.carlossevilla.similar_products_api.domain.port.ProductRepository;
import com.carlossevilla.similar_products_api.domain.port.SimilarIdsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class SimilarProductsService {

    private final ProductRepository productRepository;
    private final SimilarIdsRepository similarIdsRepository;

    /**
     * Retrieves detailed information of similar products for a given product ID
     * Applies graceful degradation: if individual product fetch fails,
     * continues with remaining products
     */
    public Flux<Product> getSimilarProducts(String productId) {
        log.info("Fetching similar products for productId: {}", productId);

        return similarIdsRepository.findSimilarIds(productId)
                .doOnSuccess(ids -> log.info("Found {} similar IDs for product {}",
                        ids.size(), productId))
                .flatMapMany(Flux::fromIterable)
                .flatMap(this::getProductWithFallback,
                        Integer.MAX_VALUE) // maxConcurrency: unlimited for parallel calls
                .doOnComplete(() -> log.info("Completed fetching similar products"));
    }

    /**
     * Fetches product with error handling
     * Returns empty Mono on failure to enable graceful degradation
     */
    private Mono<Product> getProductWithFallback(String productId) {
        return productRepository.findById(productId)
                .doOnSuccess(product -> log.debug("Successfully fetched product {}",
                        productId))
                .onErrorResume(ProductNotFoundException.class, ex -> {
                    log.warn("Product {} not found, excluding from results", productId);
                    return Mono.empty();
                })
                .onErrorResume(throwable -> {
                    log.error("Error fetching product {}: {}",
                            productId, throwable.getMessage());
                    return Mono.empty();
                });
    }

}
