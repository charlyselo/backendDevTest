package com.carlossevilla.similar_products_api.infrastructure.adapter.client;

import com.carlossevilla.similar_products_api.domain.exception.ExternalServiceException;
import com.carlossevilla.similar_products_api.domain.exception.ProductNotFoundException;
import com.carlossevilla.similar_products_api.domain.port.SimilarIdsRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
public class SimilarIdsServiceClient implements SimilarIdsRepository {

    private final WebClient webClient;
    private final io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker;
    private final io.github.resilience4j.retry.Retry retry;
    private final io.github.resilience4j.timelimiter.TimeLimiter timeLimiter;

    public SimilarIdsServiceClient(
            @Value("${external.similar-ids-service.url}") String baseUrl,
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
    public Mono<List<String>> findSimilarIds(String productId) {
        log.debug("Calling external API to fetch similar IDs for product {}",
                productId);

        return webClient.get()
                .uri("/product/{id}/similarids", productId)
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
                .bodyToMono(new ParameterizedTypeReference<List<String>>() {})
                .transformDeferred(TimeLimiterOperator.of(timeLimiter))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retry))
                .doOnSuccess(ids -> log.debug("Retrieved {} similar IDs for product {}",
                        ids.size(), productId))
                .doOnError(error -> log.error("Error fetching similar IDs for {}: {}",
                        productId, error.getMessage()));
    }

}
