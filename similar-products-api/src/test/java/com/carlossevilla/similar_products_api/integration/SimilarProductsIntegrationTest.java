package com.carlossevilla.similar_products_api.integration;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.carlossevilla.similar_products_api.domain.model.Product;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.context.ApplicationContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "external.product-service.url=http://localhost:8089",
    "external.similar-ids-service.url=http://localhost:8089"
})
class SimilarProductsIntegrationTest {

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
    @DisplayName("Integration test: Should return similar products successfully")
    void integrationTest_GetSimilarProducts() {
        // Given - Mock external APIs
        stubFor(get(urlEqualTo("/product/1/similarids"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("[\"2\",\"3\",\"4\"]")));

        stubFor(get(urlEqualTo("/product/2"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"2\",\"name\":\"Shirt\",\"price\":9.99,\"availability\":true}")));

        stubFor(get(urlEqualTo("/product/3"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"3\",\"name\":\"Longsleeve\",\"price\":19.99,\"availability\":false}")));

        stubFor(get(urlEqualTo("/product/4"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"4\",\"name\":\"Jacket\",\"price\":59.99,\"availability\":true}")));

        // When/Then
        webTestClient.get()
            .uri("/product/1/similar")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(Product.class)
            .hasSize(3)
            .consumeWith(response -> {
                List<Product> products = response.getResponseBody();
                assertThat(products.get(0).id()).isEqualTo("2");
                assertThat(products.get(1).id()).isEqualTo("3");
                assertThat(products.get(2).id()).isEqualTo("4");
            });
    }

    @Test
    @DisplayName("Integration test: Should handle 404 gracefully")
    void integrationTest_ProductNotFound() {
        // Given
        stubFor(get(urlEqualTo("/product/999/similarids"))
            .willReturn(aResponse()
                .withStatus(404)));

        // When/Then
        webTestClient.get()
            .uri("/product/999/similar")
            .exchange()
            .expectStatus().isNotFound();
    }
}
