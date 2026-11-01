package com.ecm.search.service;

import com.ecm.search.dto.request.ProductSearchFilterRequest;
import com.ecm.search.dto.response.ProductSearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Redis Top-N search result window cache.
 * Caches up to {@code windowSize} (default 500) ranked search results per query+filter combination
 * so pagination (page 0, 1, 2...) and repeated searches slice directly in memory without hitting
 * Elasticsearch or the embedding endpoint again. Fails open when Redis is unreachable.
 */
@Slf4j
@Component
public class SearchResultCache {

    public static final int DEFAULT_WINDOW_SIZE = 500;
    private static final String KEY_PREFIX = "search:v1:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;
    private final int windowSize;

    @Autowired
    public SearchResultCache(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            @Autowired(required = false) ObjectMapper objectMapper,
            @Value("${app.search.cache-ttl:5m}") Duration ttl,
            @Value("${app.search.cache-window-size:500}") int windowSize) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.ttl = ttl != null ? ttl : Duration.ofMinutes(5);
        this.windowSize = windowSize > 0 ? windowSize : DEFAULT_WINDOW_SIZE;
    }

    public boolean isEnabled() {
        return redisTemplate != null && windowSize > 0 && !ttl.isZero() && !ttl.isNegative();
    }

    public int getWindowSize() {
        return windowSize;
    }

    public CachedSearchWindow getWindow(String key) {
        if (!isEnabled() || key == null) {
            return null;
        }
        try {
            String raw = redisTemplate.opsForValue().get(key);
            if (raw == null || raw.isBlank()) {
                return null;
            }
            return objectMapper.readValue(raw, CachedSearchWindow.class);
        } catch (Exception ex) {
            log.warn("Search Redis cache read failed for key {}: {}", key, ex.getMessage());
            return null;
        }
    }

    public void putWindow(String key, CachedSearchWindow window) {
        if (!isEnabled() || key == null || window == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(window);
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (Exception ex) {
            log.warn("Search Redis cache write failed for key {}: {}", key, ex.getMessage());
        }
    }

    public static String buildKey(String lexicalQuery, ProductSearchFilterRequest filter) {
        String normalizedLexical = normalize(lexicalQuery);
        String semantic = filter == null ? "" : normalize(filter.semanticQuery());
        String categoryId = (filter == null || filter.categoryId() == null) ? "" : filter.categoryId().toString();
        String brandId = (filter == null || filter.brandId() == null) ? "" : filter.brandId().toString();
        String category = filter == null ? "" : normalize(filter.category());
        String brand = filter == null ? "" : normalize(filter.brand());
        String minPrice = (filter == null || filter.minPrice() == null) ? "" : filter.minPrice().toString();
        String maxPrice = (filter == null || filter.maxPrice() == null) ? "" : filter.maxPrice().toString();
        String inStockOnly = (filter == null || filter.inStockOnly() == null) ? "" : filter.inStockOnly().toString();

        String raw = String.join("|",
                normalizedLexical, semantic, categoryId, brandId, category, brand, minPrice, maxPrice, inStockOnly);
        return KEY_PREFIX + sha256Hex(raw);
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public record CachedSearchWindow(long totalElements, List<ProductSearchResponse> items) {
    }
}
