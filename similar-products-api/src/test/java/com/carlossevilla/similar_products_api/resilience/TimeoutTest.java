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

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import java.time.Duration;

import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "external.product-service.url=http://localhost:8089",
    "external.similar-ids-service.url=http://localhost:8089",
    "resilience4j.timelimiter.instances.productService.timeoutDuration=2s"
})
class TimeoutTest {

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
    @DisplayName("Should timeout and exclude slow products (graceful degradation)")
    void shouldTimeoutSlowProducts() {
        // Given - Mock API responses
        // Similar IDs for product 2: [3, 100, 1000]
        stubFor(get(urlEqualTo("/product/2/similarids"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("[\"3\",\"100\",\"1000\"]")));

        // Product 3: responds quickly
        stubFor(get(urlEqualTo("/product/3"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"3\",\"name\":\"Jacket\",\"price\":29.99,\"availability\":true}")));

        // Product 100: responds in 1 second (within timeout)
        stubFor(get(urlEqualTo("/product/100"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"100\",\"name\":\"Trousers\",\"price\":39.99,\"availability\":true}")
                .withFixedDelay(1000)));

        // Product 1000: responds in 5 seconds (exceeds 2s timeout)
        stubFor(get(urlEqualTo("/product/1000"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"1000\",\"name\":\"Coat\",\"price\":99.99,\"availability\":true}")
                .withFixedDelay(5000)));

        // When/Then - Should return only products 3 and 100 (1000 times out)
        webTestClient
            .mutate()
            .responseTimeout(Duration.ofSeconds(10))
            .build()
            .get()
            .uri("/product/2/similar")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(Object.class)
            .hasSize(2); // Only 2 products (product 1000 excluded due to timeout)
    }

    @Test
    @DisplayName("Should handle very slow products - extreme timeout scenario")
    void shouldHandleVerySlowProducts() {
        // Given - Product 10000 takes 50 seconds (extremely slow)
        stubFor(get(urlEqualTo("/product/3/similarids"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("[\"100\",\"1000\",\"10000\"]")));

        stubFor(get(urlEqualTo("/product/100"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"100\",\"name\":\"Trousers\",\"price\":39.99,\"availability\":true}")
                .withFixedDelay(1000)));

        stubFor(get(urlEqualTo("/product/1000"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"1000\",\"name\":\"Coat\",\"price\":99.99,\"availability\":true}")
                .withFixedDelay(5000)));

        stubFor(get(urlEqualTo("/product/10000"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"10000\",\"name\":\"Luxury Jacket\",\"price\":499.99,\"availability\":true}")
                .withFixedDelay(50000)));

        // When/Then - Should return only product 100 (others timeout)
        webTestClient
            .mutate()
            .responseTimeout(Duration.ofSeconds(10))
            .build()
            .get()
            .uri("/product/3/similar")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(Object.class)
            .hasSize(1); // Only 1 product (100), others timeout
    }
}
