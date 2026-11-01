package com.ecm.search.service;

import co.elastic.clients.elasticsearch._types.KnnSearch;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.NestedQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.NumberRangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.CommonErrorCode;
import com.ecm.common.response.PageResponse;
import com.ecm.search.client.EmbeddingClient;
import com.ecm.search.dto.request.ProductSearchFilterRequest;
import com.ecm.search.dto.response.ProductSearchResponse;
import com.ecm.search.entity.ProductDocument;
import com.ecm.search.mapper.ProductDocumentMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
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
public class ProductSearchService {

    private static final int MAX_QUERY_LENGTH = 200;
    private static final int MAX_SEMANTIC_QUERY_LENGTH = 1000;
    private static final int MAX_PAGE_SIZE = 50;
    private static final String FUZZINESS = "AUTO";
    /** Up to two words must all match ("rtx 4070" does not return a 4060); beyond that, three quarters of them. */
    private static final String MINIMUM_SHOULD_MATCH = "2<75%";
    private static final float PREFIX_BOOST = 2f;

    private final ElasticsearchOperations operations;
    private final ProductDocumentMapper mapper;
    private final EmbeddingClient embeddingClient;
    private final SearchResultCache searchCache;

    public ProductSearchService(ElasticsearchOperations operations, ProductDocumentMapper mapper) {
        this(operations, mapper, null, null);
    }

    @Autowired
    public ProductSearchService(
            ElasticsearchOperations operations,
            ProductDocumentMapper mapper,
            @Autowired(required = false) EmbeddingClient embeddingClient,
            @Autowired(required = false) SearchResultCache searchCache) {
        this.operations = operations;
        this.mapper = mapper;
        this.embeddingClient = embeddingClient;
        this.searchCache = searchCache;
    }

    public PageResponse<ProductSearchResponse> search(String text, ProductSearchFilterRequest filter, int page, int size) {
        String lexicalQuery = text == null ? "" : text.trim();
        String semanticQuery = (filter == null || filter.semanticQuery() == null) ? "" : filter.semanticQuery().trim();

        // 1. A query or catalog filter is required; pagination and price bounds must be valid.
        if ((lexicalQuery.isEmpty() && semanticQuery.isEmpty() && !hasFilterCriteria(filter))
                || lexicalQuery.length() > MAX_QUERY_LENGTH
                || semanticQuery.length() > MAX_SEMANTIC_QUERY_LENGTH
                || page < 0 || size < 1 || size > MAX_PAGE_SIZE
                || !validPrices(filter)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }

        PageRequest pageable = PageRequest.of(page, size);

        // 2. Redis Top-500 Window Cache + In-Memory Pagination
        if (searchCache != null && searchCache.isEnabled() && (long) (page + 1) * size <= searchCache.getWindowSize()) {
            String cacheKey = SearchResultCache.buildKey(lexicalQuery, filter);
            SearchResultCache.CachedSearchWindow cached = searchCache.getWindow(cacheKey);
            if (cached != null && cached.items() != null) {
                List<ProductSearchResponse> sliced = slicePage(cached.items(), page, size);
                return PageResponse.of(new PageImpl<>(sliced, pageable, cached.totalElements()));
            }

            PageRequest windowRequest = PageRequest.of(0, searchCache.getWindowSize());
            SearchHits<ProductDocument> windowHits = executeSearch(lexicalQuery, semanticQuery, filter, windowRequest);
            List<ProductSearchResponse> windowItems = windowHits.getSearchHits().stream()
                    .map(SearchHit::getContent)
                    .map(mapper::toResponse)
                    .toList();
            searchCache.putWindow(cacheKey, new SearchResultCache.CachedSearchWindow(windowHits.getTotalHits(), windowItems));

            List<ProductSearchResponse> sliced = slicePage(windowItems, page, size);
            return PageResponse.of(new PageImpl<>(sliced, pageable, windowHits.getTotalHits()));
        }

        // 3. Direct Elasticsearch query (when cache is disabled or deep page > windowSize)
        SearchHits<ProductDocument> hits = executeSearch(lexicalQuery, semanticQuery, filter, pageable);
        List<ProductSearchResponse> content = hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(mapper::toResponse)
                .toList();
        return PageResponse.of(new PageImpl<>(content, pageable, hits.getTotalHits()));
    }

    private SearchHits<ProductDocument> executeSearch(
            String lexicalQuery,
            String semanticQuery,
            ProductSearchFilterRequest filter,
            Pageable pageable) {
        List<Query> filterQueries = filters(filter);
        List<Float> queryVector = (!semanticQuery.isEmpty() && embeddingClient != null)
                ? embeddingClient.embed(semanticQuery)
                : null;

        String effectiveLexical = lexicalQuery;
        if (effectiveLexical.isEmpty() && queryVector == null) {
            effectiveLexical = semanticQuery;
        }

        NativeQueryBuilder builder = NativeQuery.builder()
                .withPageable(pageable)
                .withTrackTotalHits(true);

        if (!effectiveLexical.isEmpty()) {
            String q = effectiveLexical;
            Query words = MultiMatchQuery.of(match -> match.query(q)
                    .fields("name^3", "brandName^2", "categoryName^1.5", "specificationsText^1.0", "description^0.5")
                    .fuzziness(FUZZINESS)
                    .minimumShouldMatch(MINIMUM_SHOULD_MATCH))._toQuery();
            Query prefix = MatchQuery.of(match -> match.field("name.prefix").query(q)
                    .operator(Operator.And).boost(PREFIX_BOOST))._toQuery();
            builder.withQuery(BoolQuery.of(bool -> bool
                    .should(words)
                    .should(prefix)
                    .minimumShouldMatch("1")
                    .filter(filterQueries))._toQuery());
        } else if (queryVector == null && !filterQueries.isEmpty()) {
            builder.withQuery(BoolQuery.of(bool -> bool.filter(filterQueries))._toQuery());
        }

        if (queryVector != null) {
            int k = Math.max(pageable.getPageSize(), (pageable.getPageNumber() + 1) * pageable.getPageSize());
            int numCandidates = Math.max(100, k * 2);
            KnnSearch knnSearch = KnnSearch.of(knn -> knn
                    .field("embedding")
                    .queryVector(queryVector)
                    .k(k)
                    .numCandidates(numCandidates)
                    .filter(filterQueries));
            builder.withKnnSearches(knnSearch);
        }

        return operations.search(builder.build(), ProductDocument.class);
    }

    private static List<ProductSearchResponse> slicePage(List<ProductSearchResponse> items, int page, int size) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        int fromIndex = page * size;
        if (fromIndex >= items.size()) {
            return List.of();
        }
        int toIndex = Math.min(fromIndex + size, items.size());
        return items.subList(fromIndex, toIndex);
    }

    private static boolean validPrices(ProductSearchFilterRequest filter) {
        if (filter == null) {
            return true;
        }
        Long min = filter.minPrice();
        Long max = filter.maxPrice();
        return (min == null || min >= 0) && (max == null || max >= 0) && (min == null || max == null || min <= max);
    }

    private static boolean hasFilterCriteria(ProductSearchFilterRequest filter) {
        return filter != null && (filter.categoryId() != null || filter.brandId() != null
                || (filter.category() != null && !filter.category().isBlank())
                || (filter.brand() != null && !filter.brand().isBlank())
                || filter.minPrice() != null || filter.maxPrice() != null
                || Boolean.TRUE.equals(filter.inStockOnly()));
    }

    /** What a hit must meet whatever its rank: the category (with its sub-categories), the brand, and a variant inside the price/stock requirements. */
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
        if (filter.category() != null && !filter.category().isBlank()) {
            String cat = filter.category().trim();
            filters.add(Query.of(query -> query.match(match -> match.field("categoryName").query(cat).operator(Operator.And))));
        }
        if (filter.brand() != null && !filter.brand().isBlank()) {
            String br = filter.brand().trim();
            filters.add(Query.of(query -> query.match(match -> match.field("brandName").query(br).operator(Operator.And))));
        }
        boolean hasPriceFilter = filter.minPrice() != null || filter.maxPrice() != null;
        boolean hasStockFilter = Boolean.TRUE.equals(filter.inStockOnly());
        if (hasPriceFilter || hasStockFilter) {
            List<Query> variantClauses = new ArrayList<>();
            List<Query> flatFallbackClauses = new ArrayList<>();
            if (hasPriceFilter) {
                variantClauses.add(Query.of(query -> query.range(range -> range.number(NumberRangeQuery.of(number -> {
                    number.field("variants.price");
                    if (filter.minPrice() != null) {
                        number.gte(filter.minPrice().doubleValue());
                    }
                    if (filter.maxPrice() != null) {
                        number.lte(filter.maxPrice().doubleValue());
                    }
                    return number;
                })))));
                flatFallbackClauses.add(Query.of(query -> query.range(range -> range.number(NumberRangeQuery.of(number -> {
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
            if (hasStockFilter) {
                variantClauses.add(Query.of(query -> query.term(term -> term.field("variants.inStock").value(true))));
                flatFallbackClauses.add(Query.of(query -> query.term(term -> term.field("inStock").value(true))));
            }
            Query nestedVariantQuery = NestedQuery.of(nested -> nested
                    .path("variants")
                    .scoreMode(ChildScoreMode.None)
                    .ignoreUnmapped(true)
                    .query(BoolQuery.of(b -> b.filter(variantClauses))._toQuery()))._toQuery();
            // Nested fields are not visible to root-level exists queries.
            Query hasNestedVariants = Query.of(q -> q.nested(n -> n.path("variants")
                    .ignoreUnmapped(true).query(child -> child.matchAll(all -> all))));
            Query flatFallbackQuery = BoolQuery.of(b -> b
                    .filter(flatFallbackClauses).mustNot(hasNestedVariants))._toQuery();
            filters.add(BoolQuery.of(b -> b
                    .should(nestedVariantQuery)
                    .should(flatFallbackQuery)
                    .minimumShouldMatch("1"))._toQuery());
        }
        return filters;
    }
}
