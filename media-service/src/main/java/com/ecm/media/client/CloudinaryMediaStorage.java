package com.ecm.media.client;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.ecm.common.exception.BusinessException;
import com.ecm.media.exception.MediaErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "media.storage", name = "provider", havingValue = "CLOUDINARY")
public class CloudinaryMediaStorage implements MediaStorage {

    private static final String RESOURCE_TYPE = "image";
    private static final String UPLOAD_FOLDER = "pc-shopping";
    private final ObjectProvider<Cloudinary> cloudinaryProvider;

    @Override
    public StoredMedia upload(byte[] content, String contentType) {
        Cloudinary cloudinary = configuredClient();
        try {
            Map<?, ?> result = cloudinary.uploader().upload(content,
                    ObjectUtils.asMap("resource_type", RESOURCE_TYPE, "folder", UPLOAD_FOLDER));
            if (!(result.get("public_id") instanceof String key) || key.isBlank()
                    || !(result.get("secure_url") instanceof String publicUrl) || publicUrl.isBlank()) {
                throw new BusinessException(MediaErrorCode.CLOUDINARY_UPLOAD_FAILED, "Cloudinary did not return image metadata");
            }
            return new StoredMedia(StorageProvider.CLOUDINARY, key, publicUrl);
        } catch (IOException failure) {
            throw new BusinessException(MediaErrorCode.CLOUDINARY_UPLOAD_FAILED, "Could not upload image to Cloudinary", failure);
        } catch (BusinessException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new BusinessException(MediaErrorCode.CLOUDINARY_UPLOAD_FAILED, "Could not upload image to Cloudinary", failure);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            configuredClient().uploader().destroy(storageKey, ObjectUtils.asMap("resource_type", RESOURCE_TYPE));
        } catch (IOException failure) {
            throw new BusinessException(MediaErrorCode.CLOUDINARY_UPLOAD_FAILED, "Could not remove Cloudinary image after failed upload", failure);
        }
    }

    private Cloudinary configuredClient() {
        Cloudinary cloudinary = cloudinaryProvider.getIfAvailable();
        if (cloudinary == null) {
            throw new BusinessException(MediaErrorCode.CLOUDINARY_UPLOAD_FAILED, "Cloudinary credentials are not configured");
        }
        return cloudinary;
    }
}
