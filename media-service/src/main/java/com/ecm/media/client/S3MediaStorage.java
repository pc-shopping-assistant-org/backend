package com.ecm.media.client;

import com.ecm.common.exception.BusinessException;
import com.ecm.media.config.S3StorageProperties;
import com.ecm.media.exception.MediaErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "media.storage", name = "provider", havingValue = "S3", matchIfMissing = true)
public class S3MediaStorage implements MediaStorage {

    private static final String IMAGE_PREFIX = "images/";
    private final S3Client client;
    private final S3StorageProperties properties;

    @Override
    public StoredMedia upload(byte[] content, String contentType) {
        properties.requireUploadConfiguration();
        String key = IMAGE_PREFIX + UUID.randomUUID();
        try {
            client.putObject(PutObjectRequest.builder().bucket(properties.bucket()).key(key)
                    .contentType(contentType).build(), RequestBody.fromBytes(content));
            String publicUrl = properties.publicBaseUrl().replaceAll("/+$", "") + "/" + properties.bucket() + "/" + key;
            return new StoredMedia(StorageProvider.S3, key, publicUrl);
        } catch (RuntimeException failure) {
            throw new BusinessException(MediaErrorCode.STORAGE_UPLOAD_FAILED, "Could not upload image to S3", failure);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(storageKey).build());
        } catch (RuntimeException failure) {
            throw new BusinessException(MediaErrorCode.STORAGE_UPLOAD_FAILED, "Could not remove S3 image after failed upload", failure);
        }
    }
}
