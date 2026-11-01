package com.ecm.search.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.time.Duration;

/**
 * Calls an OpenAI-compatible embeddings endpoint (default model BAAI/bge-m3, 1024 dimensions).
 * Fails open by returning null when unconfigured or unreachable so lexical search continues.
 */
@Slf4j
@Component
public class EmbeddingClient {

    public static final int EMBEDDING_DIMS = 1024;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final RestClient restClient;

    @Autowired
    public EmbeddingClient(
            @Value("${app.embedding.api-url:}") String apiUrl,
            @Value("${app.embedding.api-key:}") String apiKey,
            @Value("${app.embedding.model:BAAI/bge-m3}") String model) {
        this(apiUrl, apiKey, model, boundedClient());
    }

    private static RestClient boundedClient() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder().requestFactory(factory).build();
    }

    public EmbeddingClient(String apiUrl, String apiKey, String model, RestClient restClient) {
        this.apiUrl = apiUrl == null ? "" : apiUrl.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = (model == null || model.isBlank()) ? "BAAI/bge-m3" : model.trim();
        this.restClient = restClient;
    }

    public boolean isEnabled() {
        return !apiUrl.isBlank();
    }

    public List<Float> embed(String text) {
        if (!isEnabled() || text == null || text.isBlank()) {
            return null;
        }
        try {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri(resolveEndpoint(apiUrl))
                    .contentType(MediaType.APPLICATION_JSON);
            if (!apiKey.isBlank()) {
                request.header("Authorization", "Bearer " + apiKey);
            }
            EmbeddingApiResponse response = request
                    .body(Map.of("model", model, "input", text))
                    .retrieve()
                    .body(EmbeddingApiResponse.class);
            if (response == null) {
                return null;
            }
            List<Float> vector = response.firstVector();
            if (!isValidVector(vector)) {
                log.warn("Embedding endpoint returned invalid vector (size={})", vector == null ? "null" : vector.size());
                return null;
            }
            return vector;
        } catch (Exception ex) {
            log.warn("Embedding request failed for model {}: {}", model, ex.getMessage());
            return null;
        }
    }

    public static boolean isValidVector(List<Float> vector) {
        if (vector == null || vector.size() != EMBEDDING_DIMS) {
            return false;
        }
        for (Float value : vector) {
            if (value == null || !Float.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    static String resolveEndpoint(String rawUrl) {
        String trimmed = rawUrl.trim();
        if (trimmed.endsWith("/v1")) {
            return trimmed + "/embeddings";
        }
        if (trimmed.endsWith("/v1/")) {
            return trimmed + "embeddings";
        }
        return trimmed;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EmbeddingApiResponse(List<EmbeddingData> data, List<Float> embedding) {
        public List<Float> firstVector() {
            if (data != null && !data.isEmpty() && data.getFirst() != null && data.getFirst().embedding() != null) {
                return data.getFirst().embedding();
            }
            return embedding;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EmbeddingData(List<Float> embedding) {
    }
}
