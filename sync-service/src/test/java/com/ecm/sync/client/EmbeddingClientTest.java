package com.ecm.sync.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddingClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("extractVector unwraps both ai-service ApiResponse envelope and OpenAI format")
    void extractVectorUnwrapsEnvelopeAndOpenAiFormat() throws Exception {
        List<Float> expected = Collections.nCopies(EmbeddingClient.EMBEDDING_DIMS, 0.25f);

        String envelopeJson = objectMapper.writeValueAsString(Map.of(
                "data", Map.of(
                        "model", "BAAI/bge-m3",
                        "data", List.of(Map.of("index", 0, "embedding", expected)),
                        "embedding", expected
                ),
                "message", "SUCCESS",
                "errors", List.of()
        ));
        JsonNode envelopeNode = objectMapper.readTree(envelopeJson);
        List<Float> fromEnvelope = EmbeddingClient.extractVector(envelopeNode);
        assertNotNull(fromEnvelope);
        assertEquals(EmbeddingClient.EMBEDDING_DIMS, fromEnvelope.size());

        String openAiJson = objectMapper.writeValueAsString(Map.of(
                "data", List.of(Map.of("index", 0, "embedding", expected))
        ));
        JsonNode openAiNode = objectMapper.readTree(openAiJson);
        List<Float> fromOpenAi = EmbeddingClient.extractVector(openAiNode);
        assertNotNull(fromOpenAi);
        assertEquals(EmbeddingClient.EMBEDDING_DIMS, fromOpenAi.size());
    }

    @Test
    @DisplayName("resolveEndpoint appends /embeddings for /api/v1 and /v1 prefixes")
    void resolveEndpointAppendsEmbeddingsPath() {
        assertEquals(
                "http://ai-service:8000/api/v1/embeddings",
                EmbeddingClient.resolveEndpoint("http://ai-service:8000/api/v1")
        );
        assertEquals(
                "http://ai-service:8000/api/v1/embeddings",
                EmbeddingClient.resolveEndpoint("http://ai-service:8000/api/v1/embeddings")
        );
        assertTrue(EmbeddingClient.resolveEndpoint("http://model:8002/v1/").endsWith("/v1/embeddings"));
    }
}
