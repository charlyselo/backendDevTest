package com.carlossevilla.similar_products_api.domain.port;

import java.util.List;
import reactor.core.publisher.Mono;

public interface SimilarIdsRepository {

    /**
     * Retrieves similar product IDs for a given product
     * @param productId the product identifier
     * @return Mono emitting list of similar product IDs ordered by similarity
     */
    Mono<List<String>> findSimilarIds(String productId);

}
