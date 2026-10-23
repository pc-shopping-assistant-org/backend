package com.ecm.media.client;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.ecm.common.exception.BusinessException;
import com.ecm.media.exception.MediaErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CloudinaryMediaStorageTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<Cloudinary> provider = mock(ObjectProvider.class);
    private final CloudinaryMediaStorage storage = new CloudinaryMediaStorage(provider);

    @Test
    void explicitCloudinaryAdapterKeepsTheHistoricalUploadMetadata() throws Exception {
        var client = mock(Cloudinary.class);
        var uploader = mock(Uploader.class);
        when(provider.getIfAvailable()).thenReturn(client);
        when(client.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), any(Map.class)))
                .thenReturn(Map.of("public_id", "pc-shopping/photo", "secure_url", "https://cloudinary.example.test/photo.png"));

        var uploaded = storage.upload(new byte[] {1}, "image/png");

        assertEquals(StorageProvider.CLOUDINARY, uploaded.provider());
        assertEquals("pc-shopping/photo", uploaded.storageKey());
        assertEquals("https://cloudinary.example.test/photo.png", uploaded.publicUrl());
        storage.delete(uploaded.storageKey());
        verify(uploader).destroy(eq("pc-shopping/photo"), any(Map.class));
    }

    @Test
    void missingCloudinaryConfigurationRetainsTheExistingErrorKey() {
        var failure = assertThrows(BusinessException.class, () -> storage.upload(new byte[] {1}, "image/png"));

        assertEquals(MediaErrorCode.CLOUDINARY_UPLOAD_FAILED, failure.getErrorCode());
    }
}
