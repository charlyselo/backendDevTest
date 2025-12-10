package com.carlossevilla.similar_products_api.infrastructure.adapter.client;

import com.carlossevilla.similar_products_api.domain.exception.ExternalServiceException;
import com.carlossevilla.similar_products_api.domain.exception.ProductNotFoundException;
import com.carlossevilla.similar_products_api.domain.model.Product;
import com.carlossevilla.similar_products_api.domain.port.ProductRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class ProductServiceClient implements ProductRepository {

    private final WebClient webClient;

    public ProductServiceClient(@Value("${external.product-service.url}") String baseUrl, WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .build();
    }

    @Override
    @CircuitBreaker(name = "productService")
    @TimeLimiter(name = "productService")
    @Retry(name = "productService")
    public Mono<Product> findById(String productId) {
        log.debug("Calling external API to fetch product {}", productId);

        return webClient.get()
                .uri("/product/{id}", productId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> {
                    if (response.statusCode().value() == 404) {
                        return Mono.error(new ProductNotFoundException(productId));
                    }
                    return Mono.error(new ExternalServiceException(
                            "Client error: " + response.statusCode()));
                })
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        Mono.error(new ExternalServiceException(
                                "Server error: " + response.statusCode())))
                .bodyToMono(Product.class)
                .doOnSuccess(product -> log.debug("Successfully retrieved product {}",
                        productId))
                .doOnError(error -> log.error("Error fetching product {}: {}",
                        productId, error.getMessage()));
    }

}
