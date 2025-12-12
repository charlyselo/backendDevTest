package com.carlossevilla.similar_products_api.infrastructure.adapter.client;

import com.carlossevilla.similar_products_api.domain.exception.ExternalServiceException;
import com.carlossevilla.similar_products_api.domain.exception.ProductNotFoundException;
import com.carlossevilla.similar_products_api.domain.model.Product;
import com.carlossevilla.similar_products_api.domain.port.ProductRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Component
public class ProductServiceClient implements ProductRepository {

    private final WebClient webClient;
    private final io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker;
    private final io.github.resilience4j.retry.Retry retry;
    private final io.github.resilience4j.timelimiter.TimeLimiter timeLimiter;

    public ProductServiceClient(
            @Value("${external.product-service.url}") String baseUrl,
            WebClient.Builder webClientBuilder,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            TimeLimiterRegistry timeLimiterRegistry) {
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .build();
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("productService");
        this.retry = retryRegistry.retry("productService");
        this.timeLimiter = timeLimiterRegistry.timeLimiter("productService");
    }

    @Override
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
                .transformDeferred(TimeLimiterOperator.of(timeLimiter))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retry))
                .doOnSuccess(product -> log.debug("Successfully retrieved product {}",
                        productId))
                .doOnError(error -> log.error("Error fetching product {}: {}",
                        productId, error.getMessage()));
    }

}
