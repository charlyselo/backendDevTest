package com.carlossevilla.similar_products_api.unit.application.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.carlossevilla.similar_products_api.application.service.SimilarProductsService;
import com.carlossevilla.similar_products_api.domain.exception.ProductNotFoundException;
import com.carlossevilla.similar_products_api.domain.model.Product;
import com.carlossevilla.similar_products_api.domain.port.ProductRepository;
import com.carlossevilla.similar_products_api.domain.port.SimilarIdsRepository;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
public class SimilarProductsServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private SimilarIdsRepository similarIdsRepository;
    @InjectMocks
    private SimilarProductsService similarProductsService;

    @Test
    @DisplayName("Should return similar products when all services respond successfully")
    void getSimilarProducts_Success() {
        // Given
        String productId = "1";
        List<String> similarIds = List.of("2", "3", "4");

        when(similarIdsRepository.findSimilarIds(productId))
            .thenReturn(Mono.just(similarIds));
        when(productRepository.findById("2"))
            .thenReturn(Mono.just(createProduct("2", "Shirt")));
        when(productRepository.findById("3"))
            .thenReturn(Mono.just(createProduct("3", "Longsleeve")));
        when(productRepository.findById("4"))
            .thenReturn(Mono.just(createProduct("4", "Jacket")));

        // When
        StepVerifier.create(similarProductsService.getSimilarProducts(productId))
        // Then
            .expectNextMatches(p -> p.id().equals("2"))
            .expectNextMatches(p -> p.id().equals("3"))
            .expectNextMatches(p -> p.id().equals("4"))
            .verifyComplete();

        verify(productRepository, times(3)).findById(anyString());
    }

    @Test
    @DisplayName("Should handle gracefully when one product fails (404)")
    void getSimilarProducts_OneProductNotFound() {
        // Given
        String productId = "1";
        List<String> similarIds = List.of("2", "3", "4");

        when(similarIdsRepository.findSimilarIds(productId))
            .thenReturn(Mono.just(similarIds));

        when(productRepository.findById("2"))
            .thenReturn(Mono.just(createProduct("2", "Shirt")));
        when(productRepository.findById("3"))
            .thenReturn(Mono.just(createProduct("3", "Longsleeve")));
        when(productRepository.findById("4"))
            .thenReturn(Mono.error(new ProductNotFoundException("4")));

        // When
        StepVerifier.create(similarProductsService.getSimilarProducts(productId))
        // Then
            .expectNextMatches(p -> p.id().equals("2"))
            .expectNextMatches(p -> p.id().equals("3"))
            .verifyComplete();
    }

    @Test
    @DisplayName("Should handle gracefully when one product times out")
    void getSimilarProducts_OneProductTimesOut() {
        // Given
        String productId = "1";
        List<String> similarIds = List.of("2", "3", "4");

        when(similarIdsRepository.findSimilarIds(productId))
            .thenReturn(Mono.just(similarIds));

        when(productRepository.findById("2"))
            .thenReturn(Mono.just(createProduct("2", "Shirt")));
        when(productRepository.findById("3"))
            .thenReturn(Mono.just(createProduct("3", "Longsleeve")));
        when(productRepository.findById("4"))
            .thenReturn(Mono.error(new TimeoutException("Request timeout")));

        // When
        StepVerifier.create(similarProductsService.getSimilarProducts(productId))
        // Then
            .expectNextCount(2)
            .verifyComplete();
    }

    private Product createProduct(String id, String name) {
        return new Product(id, name, new BigDecimal("9.99"), true);
    }

}
