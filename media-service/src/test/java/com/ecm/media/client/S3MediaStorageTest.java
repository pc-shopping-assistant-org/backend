package com.ecm.media.client;

import com.ecm.common.exception.BusinessException;
import com.ecm.media.config.S3StorageProperties;
import com.ecm.media.exception.MediaErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class S3MediaStorageTest {
    private final S3Client client = mock(S3Client.class);
    private final S3StorageProperties properties = new S3StorageProperties(URI.create("http://localhost:8333"),
            "pcshop-media", "us-east-1", "https://files.example.test/", "test-key", "test-secret");
    private final S3MediaStorage storage = new S3MediaStorage(client, properties);

    @Test
    void uploadsToExistingBucketAndReturnsPublicUrlWithAnIndependentKey() throws Exception {
        byte[] content = {1, 2, 3};

        StoredMedia uploaded = storage.upload(content, "image/png");

        var request = ArgumentCaptor.forClass(PutObjectRequest.class);
        var body = ArgumentCaptor.forClass(RequestBody.class);
        verify(client).putObject(request.capture(), body.capture());
        assertEquals("pcshop-media", request.getValue().bucket());
        assertEquals("image/png", request.getValue().contentType());
        assertEquals(uploaded.storageKey(), request.getValue().key());
        assertTrue(uploaded.storageKey().startsWith("images/"));
        assertEquals(StorageProvider.S3, uploaded.provider());
        assertEquals("https://files.example.test/pcshop-media/" + uploaded.storageKey(), uploaded.publicUrl());
        try (var bytes = body.getValue().contentStreamProvider().newStream()) {
            assertArrayEquals(content, bytes.readAllBytes());
        }
    }

    @Test
    void failedS3UploadProducesAStableServiceError() {
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("storage offline"));

        var failure = assertThrows(BusinessException.class, () -> storage.upload(new byte[] {1}, "image/png"));

        assertEquals(MediaErrorCode.STORAGE_UPLOAD_FAILED, failure.getErrorCode());
    }

    @Test
    void absentClientCredentialsFailBeforeAnyStorageCall() {
        var unconfigured = new S3StorageProperties(properties.endpoint(), properties.bucket(), properties.region(),
                properties.publicBaseUrl(), "", "");

        assertThrows(BusinessException.class, () -> new S3MediaStorage(client, unconfigured).upload(new byte[] {1}, "image/png"));

        verifyNoInteractions(client);
    }

    @Test
    void compensationDeletesTheExactNewObjectKey() {
        storage.delete("images/new-key");

        var request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client).deleteObject(request.capture());
        assertEquals("pcshop-media", request.getValue().bucket());
        assertEquals("images/new-key", request.getValue().key());
    }
}
