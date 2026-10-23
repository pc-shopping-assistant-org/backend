package com.ecm.media.client;

import com.ecm.media.config.S3StorageConfig;
import com.ecm.media.config.S3StorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "SEED_TEST_S3_ENDPOINT", matches = "http://.*")
class S3MediaStorageIntegrationTest {
    @Test
    void realSeaweedfsRoundtripKeepsImageBytesMetadataAndSupportsCompensation() {
        String endpoint = System.getenv("SEED_TEST_S3_ENDPOINT");
        var properties = new S3StorageProperties(URI.create(endpoint), "pcshop-media", "us-east-1",
                endpoint, "seed-test-only", "seed-test-only");
        byte[] image = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        try (var client = new S3StorageConfig().s3Client(properties)) {
            var storage = new S3MediaStorage(client, properties);
            var uploaded = storage.upload(image, "image/png");
            try {
                var downloaded = client.getObjectAsBytes(request -> request.bucket(properties.bucket()).key(uploaded.storageKey()));
                assertArrayEquals(image, downloaded.asByteArray());
                assertEquals("image/png", downloaded.response().contentType());
                assertEquals(StorageProvider.S3, uploaded.provider());
                assertEquals(endpoint + "/pcshop-media/" + uploaded.storageKey(), uploaded.publicUrl());
            } finally {
                storage.delete(uploaded.storageKey());
            }
            var missing = assertThrows(S3Exception.class,
                    () -> client.headObject(request -> request.bucket(properties.bucket()).key(uploaded.storageKey())));
            assertEquals(404, missing.statusCode());
        }
    }
}
