package com.carlossevilla.similar_products_api.infrastructure.adapter.client;

import com.carlossevilla.similar_products_api.domain.exception.ExternalServiceException;
import com.carlossevilla.similar_products_api.domain.exception.ProductNotFoundException;
import com.carlossevilla.similar_products_api.domain.port.SimilarIdsRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
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

    public SimilarIdsServiceClient(
            @Value("${external.similar-ids-service.url}") String baseUrl,
            WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .build();
    }

    @Override
    @CircuitBreaker(name = "productService")
    @TimeLimiter(name = "productService")
    @Retry(name = "productService")
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
                .doOnSuccess(ids -> log.debug("Retrieved {} similar IDs for product {}",
                        ids.size(), productId))
                .doOnError(error -> log.error("Error fetching similar IDs for {}: {}",
                        productId, error.getMessage()));
    }

}
