package com.ecm.catalog.bootstrap;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SeedImagesTest {
    @Test
    void downloadsImageUploadsObjectAndOnlyThenInsertsS3Metadata() throws Exception {
        SeedDatabase media = emptyMedia();
        try (var server = new StorageServer()) {
            var file = file(server);
            new SeedImages(media, List.of(file)).insert(server.environment());

            assertEquals(1, server.sourceRequests.get());
            assertEquals(1, server.putRequests.get());
            assertArrayEquals(StorageServer.IMAGE, server.objects.get("/pcshop-media/" + SeedImages.objectKey(file)));
            Object[] record = insertedRecords(media).getFirst();
            assertEquals(file.id(), record[0]);
            assertEquals("S3", record[1]);
            assertEquals(SeedImages.objectKey(file), record[2]);
            assertEquals("image/png", record[4]);
            assertEquals((long) StorageServer.IMAGE.length, record[5]);
            assertEquals("https://files.example.test/pcshop-media/seed/catalog/" + file.id() + "/seed%20image.png", record[6]);
        }
    }

    @Test
    void detectsImageBytesWhenSourceUsesOctetStream() throws Exception {
        SeedDatabase media = emptyMedia();
        try (var server = new StorageServer()) {
            server.sourceContentType = "application/octet-stream";
            var file = file(server);

            new SeedImages(media, List.of(file)).insert(server.environment());

            assertEquals("image/png", server.putContentType);
            assertEquals("image/png", insertedRecords(media).getFirst()[4]);
        }
    }

    @Test
    void detectsExtensionlessJpegSourceFromImageBytes() throws Exception {
        SeedDatabase media = emptyMedia();
        try (var server = new StorageServer()) {
            server.sourceContentType = "application/octet-stream";
            server.sourceImage = new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00};
            var file = new SeedData.Media(UUID.randomUUID(), "11271_", "11271_", "image/jpeg", server.endpoint() + "/source.png");

            new SeedImages(media, List.of(file)).insert(server.environment());

            assertEquals("image/jpeg", server.putContentType);
            assertEquals("image/jpeg", insertedRecords(media).getFirst()[4]);
        }
    }

    @Test
    void reusesUploadedObjectAfterDatabaseRollbackWithoutDownloadingOrOverwriting() throws Exception {
        SeedDatabase media = emptyMedia();
        try (var server = new StorageServer()) {
            var file = file(server);
            server.objects.put("/pcshop-media/" + SeedImages.objectKey(file), StorageServer.IMAGE);
            new SeedImages(media, List.of(file)).insert(server.environment());

            assertEquals(0, server.sourceRequests.get());
            assertEquals(0, server.putRequests.get());
            assertEquals(1, insertedRecords(media).size());
        }
    }

    @Test
    void existingFileRowSkipsStorageAndDoesNotRequireAnyCredentials() throws Exception {
        SeedDatabase media = mock(SeedDatabase.class);
        var file = new SeedData.Media(UUID.randomUUID(), "external.png", "seed.png", "image/png", "https://source.invalid/image");
        when(media.query(anyString())).thenReturn(List.of(Map.of("id", file.id(), "storage_provider", "EXTERNAL", "storage_key", "external.png")));

        new SeedImages(media, List.of(file)).insert(Map.of());

        verify(media, never()).insert(anyString(), anyList());
    }

    @Test
    void missingSourceImageSkipsMetadata() throws Exception {
        SeedDatabase media = emptyMedia();
        try (var server = new StorageServer()) {
            server.sourceStatus = 404;
            var file = file(server);
            var images = new SeedImages(media, List.of(file));

            images.insert(server.environment());

            assertEquals(0, server.putRequests.get());
            assertFalse(images.ids.containsKey(file.id()));
            assertTrue(insertedRecords(media).isEmpty());
        }
    }

    private static SeedDatabase emptyMedia() throws Exception {
        SeedDatabase media = mock(SeedDatabase.class);
        when(media.query(anyString())).thenReturn(List.of());
        when(media.insert(anyString(), anyList())).thenReturn(1);
        return media;
    }

    private static SeedData.Media file(StorageServer server) {
        return new SeedData.Media(UUID.randomUUID(), "source-key", "seed image.png", "image/jpeg", server.endpoint() + "/source.png");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<Object[]> insertedRecords(SeedDatabase media) throws Exception {
        ArgumentCaptor<List<Object[]>> records = ArgumentCaptor.forClass(List.class);
        verify(media).insert(contains("INSERT INTO files"), records.capture());
        return records.getValue();
    }

    /** Minimal HTTP fixture exercises the real SDK client and source downloader without external requests. */
    static final class StorageServer implements AutoCloseable {
        static final byte[] IMAGE = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        final Map<String, byte[]> objects = new ConcurrentHashMap<>();
        final Map<String, String> contentTypes = new ConcurrentHashMap<>();
        final AtomicInteger sourceRequests = new AtomicInteger();
        final AtomicInteger putRequests = new AtomicInteger();
        int sourceStatus = 200;
        String sourceContentType = "image/png";
        byte[] sourceImage = IMAGE;
        String putContentType;
        private final HttpServer server;

        StorageServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", this::handle);
            server.start();
        }

        String endpoint() { return "http://127.0.0.1:" + server.getAddress().getPort(); }

        Map<String, String> environment() {
            var environment = new HashMap<String, String>();
            environment.put("S3_ENDPOINT", endpoint());
            environment.put("S3_PUBLIC_BASE_URL", "https://files.example.test");
            environment.put("S3_BUCKET", "pcshop-media");
            environment.put("AWS_ACCESS_KEY_ID", "seed-test-only");
            environment.put("AWS_SECRET_ACCESS_KEY", "seed-test-only");
            environment.put("SEED_IMAGE_WORKERS", "1");
            return environment;
        }

        private void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                String path = exchange.getRequestURI().getPath();
                if (path.equals("/source.png")) {
                    sourceRequests.incrementAndGet();
                    exchange.getResponseHeaders().set("Content-Type", sourceContentType);
                    exchange.sendResponseHeaders(sourceStatus, sourceImage.length);
                    exchange.getResponseBody().write(sourceImage);
                } else if (exchange.getRequestMethod().equals("HEAD") && (path.equals("/pcshop-media") || path.equals("/pcshop-media/"))) {
                    exchange.sendResponseHeaders(200, -1);
                } else if (exchange.getRequestMethod().equals("HEAD")) {
                    byte[] object = objects.get(path);
                    if (object != null) {
                        exchange.getResponseHeaders().set("Content-Type", contentTypes.getOrDefault(path, "image/png"));
                        exchange.getResponseHeaders().set("Content-Length", String.valueOf(object.length));
                    }
                    exchange.sendResponseHeaders(object == null ? 404 : 200, -1);
                } else if (exchange.getRequestMethod().equals("PUT")) {
                    putRequests.incrementAndGet();
                    putContentType = exchange.getRequestHeaders().getFirst("Content-Type");
                    byte[] body = exchange.getRequestBody().readAllBytes();
                    if (objects.putIfAbsent(path, body) != null) exchange.sendResponseHeaders(412, -1);
                    else {
                        contentTypes.put(path, putContentType);
                        exchange.getResponseHeaders().set("ETag", "\"seed-test-only\"");
                        exchange.sendResponseHeaders(200, -1);
                    }
                } else exchange.sendResponseHeaders(405, -1);
            }
        }

        @Override public void close() { server.stop(0); }
    }
}
