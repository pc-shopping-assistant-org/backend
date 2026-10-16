package com.ecm.sync.config;

import com.ecm.sync.entity.ProductDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * Spring Data only creates the mapping when the index does not exist yet, so a field added to {@link ProductDocument}
 * later would be mapped by guesswork (a UUID as analysed text). Adding the declared fields to an existing index is safe.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchIndexMappingRunner implements ApplicationRunner {

    private final ElasticsearchOperations operations;

    @Override
    public void run(ApplicationArguments args) {
        try {
            IndexOperations index = operations.indexOps(ProductDocument.class);
            if (index.exists()) {
                index.putMapping();
            }
        } catch (RuntimeException ex) {
            log.warn("The mapping of the product index could not be brought up to date: {}", ex.getMessage());
        }
    }
}
