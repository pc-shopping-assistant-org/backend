package com.ecm.search.service;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.NumberRangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.CommonErrorCode;
import com.ecm.common.response.PageResponse;
import com.ecm.search.dto.request.ProductSearchFilterRequest;
import com.ecm.search.dto.response.ProductSearchResponse;
import com.ecm.search.entity.ProductDocument;
import com.ecm.search.mapper.ProductDocumentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Product search by name (UC-CAT-003), narrowed by category, brand and price range (UC-CAT-004). A search by need
 * ("a laptop for design work") is the semantic search of UC-AI-002.
 */
@Service
@RequiredArgsConstructor
public class ProductSearchService {

    private static final int MAX_QUERY_LENGTH = 200;
    private static final int MAX_PAGE_SIZE = 50;
    private static final String FUZZINESS = "AUTO";
    /** Up to two words must all match ("rtx 4070" does not return a 4060); beyond that, three quarters of them. */
    private static final String MINIMUM_SHOULD_MATCH = "2<75%";
    private static final float PREFIX_BOOST = 2f;

    private final ElasticsearchOperations operations;
    private final ProductDocumentMapper mapper;

    public PageResponse<ProductSearchResponse> search(String text, ProductSearchFilterRequest filter, int page, int size) {
        // 1. A query is needed, a page has to make sense, and a price range has to be a range
        if (text == null || text.isBlank() || text.length() > MAX_QUERY_LENGTH || page < 0 || size < 1 || size > MAX_PAGE_SIZE
                || !validPrices(filter)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }

        // 2. Words of the name weigh most, then brand and category; typos and half-typed words still match
        String query = text.trim();
        Query words = MultiMatchQuery.of(match -> match.query(query)
                .fields("name^3", "brandName^2", "categoryName^1.5", "description^0.5")
                .fuzziness(FUZZINESS)
                .minimumShouldMatch(MINIMUM_SHOULD_MATCH))._toQuery();
        Query prefix = MatchQuery.of(match -> match.field("name.prefix").query(query)
                .operator(Operator.And).boost(PREFIX_BOOST))._toQuery();
        PageRequest pageable = PageRequest.of(page, size);
        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(BoolQuery.of(bool -> bool.should(words).should(prefix).minimumShouldMatch("1").filter(filters(filter)))._toQuery())
                .withPageable(pageable)
                .withTrackTotalHits(true)
                .build();

        // 3. Best match first
        SearchHits<ProductDocument> hits = operations.search(nativeQuery, ProductDocument.class);
        List<ProductSearchResponse> content = hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(mapper::toResponse)
                .toList();
        return PageResponse.of(new PageImpl<>(content, pageable, hits.getTotalHits()));
    }

    private static boolean validPrices(ProductSearchFilterRequest filter) {
        if (filter == null) {
            return true;
        }
        Long min = filter.minPrice();
        Long max = filter.maxPrice();
        return (min == null || min >= 0) && (max == null || max >= 0) && (min == null || max == null || min <= max);
    }

    /** What a hit must meet whatever its rank: the category (with its sub-categories), the brand, and a variant inside the price range. */
    private static List<Query> filters(ProductSearchFilterRequest filter) {
        List<Query> filters = new ArrayList<>();
        if (filter == null) {
            return filters;
        }
        if (filter.categoryId() != null) {
            filters.add(Query.of(query -> query.term(term -> term.field("categoryIds").value(filter.categoryId().toString()))));
        }
        if (filter.brandId() != null) {
            filters.add(Query.of(query -> query.term(term -> term.field("brandId").value(filter.brandId().toString()))));
        }
        if (filter.minPrice() != null || filter.maxPrice() != null) {
            filters.add(Query.of(query -> query.range(range -> range.number(NumberRangeQuery.of(number -> {
                number.field("prices");
                if (filter.minPrice() != null) {
                    number.gte(filter.minPrice().doubleValue());
                }
                if (filter.maxPrice() != null) {
                    number.lte(filter.maxPrice().doubleValue());
                }
                return number;
            })))));
        }
        return filters;
    }
}
