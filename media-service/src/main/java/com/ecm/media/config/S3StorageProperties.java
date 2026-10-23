package com.ecm.media.config;

import com.ecm.common.exception.BusinessException;
import com.ecm.media.exception.MediaErrorCode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties("media.storage.s3")
public record S3StorageProperties(URI endpoint, String bucket, String region, String publicBaseUrl,
                                  String accessKey, String secretKey) {

    public void requireUploadConfiguration() {
        if (isBlank(bucket) || isBlank(accessKey) || isBlank(secretKey) || isBlank(publicBaseUrl)) {
            throw new BusinessException(MediaErrorCode.STORAGE_UPLOAD_FAILED, "S3 upload is not configured");
        }
        try {
            URI publicBase = URI.create(publicBaseUrl);
            if (publicBase.getHost() == null || !("https".equals(publicBase.getScheme()) || "http".equals(publicBase.getScheme()))
                    || publicBase.getQuery() != null || publicBase.getFragment() != null) {
                throw new IllegalArgumentException("Invalid public base URL");
            }
        } catch (IllegalArgumentException invalid) {
            throw new BusinessException(MediaErrorCode.STORAGE_UPLOAD_FAILED, "S3 public base URL is invalid");
        }
    }

    private static boolean isBlank(String value) { return value == null || value.isBlank(); }

    @Override
    public String toString() {
        return "S3StorageProperties[endpoint=" + endpoint + ", bucket=" + bucket + ", region=" + region + "]";
    }
}
