package com.ecm.sync.repository;

import com.ecm.sync.entity.ProductDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, String> {
}
