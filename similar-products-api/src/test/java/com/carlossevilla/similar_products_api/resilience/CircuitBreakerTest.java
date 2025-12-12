package com.carlossevilla.similar_products_api.resilience;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.carlossevilla.similar_products_api.domain.port.ProductRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import reactor.core.publisher.Mono;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
    "external.product-service.url=http://localhost:8089",
    "external.similar-ids-service.url=http://localhost:8089",
    "resilience4j.retry.instances.productService.maxAttempts=1"  // Disable retry to test circuit breaker
})
class CircuitBreakerTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(8089);
        wireMockServer.start();
        WireMock.configureFor("localhost", 8089);

        // Mock to return 500 error
        stubFor(get(urlEqualTo("/product/failing-id"))
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("Internal Server Error")));
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    @DisplayName("Circuit breaker should open after failure threshold")
    void circuitBreaker_OpensAfterFailures() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry
            .circuitBreaker("productService");

        // Simulate multiple failures (10 calls with 500 errors)
        // Don't use onErrorResume - let errors propagate so circuit breaker can count them
        for (int i = 0; i < 10; i++) {
            try {
                productRepository.findById("failing-id").block();
            } catch (Exception e) {
                // Expected - errors will trigger circuit breaker
            }
        }

        // Verify circuit is open or half-open
        assertThat(circuitBreaker.getState())
            .isIn(CircuitBreaker.State.OPEN, CircuitBreaker.State.HALF_OPEN);
    }
}
