package com.carlossevilla.similar_products_api.infrastructure.adapter.rest;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.carlossevilla.similar_products_api.application.service.SimilarProductsService;
import com.carlossevilla.similar_products_api.domain.model.Product;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class SimilarProductsController {

    private final SimilarProductsService similarProductsService;

    public Flux<Product> getSimilarProducts(@PathVariable String productId) {
        log.info("Received request for similar products of productId: {}", productId);
        return similarProductsService.getSimilarProducts(productId);
    }

}
