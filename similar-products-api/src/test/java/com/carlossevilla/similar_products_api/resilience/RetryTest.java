package com.carlossevilla.similar_products_api.resilience;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "external.product-service.url=http://localhost:8089",
    "external.similar-ids-service.url=http://localhost:8089",
    "resilience4j.retry.instances.productService.maxAttempts=3",
    "resilience4j.retry.instances.productService.waitDuration=100ms",
    "resilience4j.retry.instances.productService.exponentialBackoffMultiplier=2",
    "resilience4j.circuitbreaker.instances.productService.minimumNumberOfCalls=1000"  // Disable circuit breaker
})
class RetryTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext context;

    private WebTestClient webTestClient;

    private WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        wireMockServer = new WireMockServer(8089);
        wireMockServer.start();
        WireMock.configureFor("localhost", 8089);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    @DisplayName("Should retry and succeed after transient failures")
    void shouldRetryAndSucceedAfterTransientFailures() {
        // Given - Mock that fails twice, then succeeds on 3rd attempt
        stubFor(get(urlEqualTo("/product/1/similarids"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("[\"2\"]")));

        // Product 2 fails twice (500), then succeeds
        stubFor(get(urlEqualTo("/product/2"))
            .inScenario("Retry Scenario")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("Internal Server Error"))
            .willSetStateTo("First Retry"));

        stubFor(get(urlEqualTo("/product/2"))
            .inScenario("Retry Scenario")
            .whenScenarioStateIs("First Retry")
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("Internal Server Error"))
            .willSetStateTo("Second Retry"));

        stubFor(get(urlEqualTo("/product/2"))
            .inScenario("Retry Scenario")
            .whenScenarioStateIs("Second Retry")
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"2\",\"name\":\"Shirt\",\"price\":9.99,\"availability\":true}")));

        // When/Then - Should eventually succeed after retries
        webTestClient
            .mutate()
            .responseTimeout(Duration.ofSeconds(10))
            .build()
            .get()
            .uri("/product/1/similar")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(Object.class)
            .hasSize(1);

        // Verify that 3 attempts were made
        verify(exactly(3), getRequestedFor(urlEqualTo("/product/2")));
    }

    @Test
    @DisplayName("Should fail after max retry attempts exceeded")
    void shouldFailAfterMaxRetryAttemptsExceeded() {
        // Given - Mock that always fails (even after 3 retries)
        stubFor(get(urlEqualTo("/product/5/similarids"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("[\"1\",\"2\",\"6\"]")));

        stubFor(get(urlEqualTo("/product/1"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"1\",\"name\":\"Shirt\",\"price\":9.99,\"availability\":true}")));

        stubFor(get(urlEqualTo("/product/2"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"2\",\"name\":\"Longsleeve\",\"price\":19.99,\"availability\":true}")));

        // Product 6 always returns 500 (server error)
        stubFor(get(urlEqualTo("/product/6"))
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("Internal Server Error")));

        // When/Then - Should return partial results (graceful degradation)
        // Returns products 1 and 2, excludes product 6 after max retries
        webTestClient
            .mutate()
            .responseTimeout(Duration.ofSeconds(10))
            .build()
            .get()
            .uri("/product/5/similar")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(Object.class)
            .hasSize(2); // Only 2 products (product 6 failed after retries)

        // Verify that 3 retry attempts were made for product 6
        verify(exactly(3), getRequestedFor(urlEqualTo("/product/6")));
    }

    @Test
    @DisplayName("Should retry with exponential backoff")
    void shouldRetryWithExponentialBackoff() {
        // Given - Mock that fails first time, succeeds second time
        stubFor(get(urlEqualTo("/product/1/similarids"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("[\"3\"]")));

        stubFor(get(urlEqualTo("/product/3"))
            .inScenario("Backoff Scenario")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("Temporary failure"))
            .willSetStateTo("Success"));

        stubFor(get(urlEqualTo("/product/3"))
            .inScenario("Backoff Scenario")
            .whenScenarioStateIs("Success")
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"3\",\"name\":\"Jacket\",\"price\":29.99,\"availability\":true}")));

        // When
        long startTime = System.currentTimeMillis();

        webTestClient
            .mutate()
            .responseTimeout(Duration.ofSeconds(10))
            .build()
            .get()
            .uri("/product/1/similar")
            .exchange()
            .expectStatus().isOk();

        long duration = System.currentTimeMillis() - startTime;

        // Then - Verify backoff delay occurred (at least 100ms for first retry)
        assertThat(duration).isGreaterThanOrEqualTo(100);

        // Verify 2 attempts were made (initial + 1 retry)
        verify(exactly(2), getRequestedFor(urlEqualTo("/product/3")));
    }
}
