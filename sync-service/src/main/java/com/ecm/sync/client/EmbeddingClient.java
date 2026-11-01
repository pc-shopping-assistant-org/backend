package com.ecm.sync.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Calls the embeddings endpoint (ai-service /api/v1/embeddings envelope or OpenAI /v1/embeddings format,
 * default model BAAI/bge-m3, 1024 dimensions).
 * Fails open by returning null when unconfigured or unreachable so catalog sync continues.
 */
@Slf4j
@Component
public class EmbeddingClient {

    public static final int EMBEDDING_DIMS = 1024;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

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

    public float[] embed(String text) {
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
            JsonNode response = request
                    .body(Map.of("model", model, "input", text))
                    .retrieve()
                    .body(JsonNode.class);
            List<Float> vector = extractVector(response);
            if (vector == null || vector.size() != EMBEDDING_DIMS) {
                log.warn("Embedding endpoint returned unexpected dimension: {}", vector == null ? "null" : vector.size());
                return null;
            }
            float[] result = new float[EMBEDDING_DIMS];
            for (int i = 0; i < EMBEDDING_DIMS; i++) {
                Float value = vector.get(i);
                if (value == null || !Float.isFinite(value)) {
                    return null;
                }
                result[i] = value;
            }
            return result;
        } catch (Exception ex) {
            log.warn("Embedding request failed for model {}: {}", model, ex.getMessage());
            return null;
        }
    }

    public static List<Float> extractVector(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode dataNode = node.get("data");
        if (dataNode != null && dataNode.isObject()) {
            List<Float> nested = extractVector(dataNode);
            if (nested != null) {
                return nested;
            }
        }
        if (dataNode != null && dataNode.isArray() && !dataNode.isEmpty()) {
            JsonNode first = dataNode.get(0);
            if (first != null && first.isObject()) {
                List<Float> fromFirst = toFloatList(first.get("embedding"));
                if (fromFirst != null) {
                    return fromFirst;
                }
            }
        }
        return toFloatList(node.get("embedding"));
    }

    private static List<Float> toFloatList(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray() || arrayNode.isEmpty()) {
            return null;
        }
        List<Float> values = new ArrayList<>(arrayNode.size());
        for (JsonNode element : arrayNode) {
            if (element == null || !element.isNumber()) {
                return null;
            }
            float val = element.floatValue();
            if (!Float.isFinite(val)) {
                return null;
            }
            values.add(val);
        }
        return values;
    }

    public static boolean isValidVector(float[] vector) {
        if (vector == null || vector.length != EMBEDDING_DIMS) {
            return false;
        }
        for (float v : vector) {
            if (!Float.isFinite(v)) {
                return false;
            }
        }
        return true;
    }

    static String resolveEndpoint(String rawUrl) {
        String trimmed = rawUrl.trim();
        if (trimmed.endsWith("/api/v1") || trimmed.endsWith("/v1")) {
            return trimmed + "/embeddings";
        }
        if (trimmed.endsWith("/api/v1/") || trimmed.endsWith("/v1/")) {
            return trimmed + "embeddings";
        }
        return trimmed;
    }
}
