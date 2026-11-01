package com.ecm.search.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingClientTest {
    @Test
    void consumesOpenAiCompatibleEmbeddingsOverHttp() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var body = new AtomicReference<String>();
        var authorization = new AtomicReference<String>();
        server.createContext("/v1/embeddings", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = ("{\"data\":[{\"embedding\":["
                    + String.join(",", Collections.nCopies(EmbeddingClient.EMBEDDING_DIMS, "0.1"))
                    + "]}]}" ).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var client = new EmbeddingClient("http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                    "fixture-key", "BAAI/bge-m3");
            assertThat(client.embed("wireless headset")).hasSize(EmbeddingClient.EMBEDDING_DIMS);
            assertThat(body.get()).contains("wireless headset", "BAAI/bge-m3");
            assertThat(authorization.get()).isEqualTo("Bearer fixture-key");
        } finally {
            server.stop(0);
        }
    }
}
