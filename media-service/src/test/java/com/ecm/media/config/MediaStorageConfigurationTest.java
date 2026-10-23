package com.ecm.media.config;

import com.ecm.media.client.CloudinaryMediaStorage;
import com.ecm.media.client.MediaStorage;
import com.ecm.media.client.S3MediaStorage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class MediaStorageConfigurationTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(S3StorageConfig.class, CloudinaryConfig.class,
                    S3MediaStorage.class, CloudinaryMediaStorage.class)
            .withPropertyValues(
                    "media.storage.s3.endpoint=http://localhost:8333",
                    "media.storage.s3.bucket=pcshop-media",
                    "media.storage.s3.region=us-east-1",
                    "media.storage.s3.public-base-url=",
                    "media.storage.s3.access-key=",
                    "media.storage.s3.secret-key=");

    @Test
    void defaultS3AdapterCanStartForLegacyReadsBeforeUploadCredentialsAreSet() {
        context.run(application -> {
            assertThat(application).hasNotFailed().hasSingleBean(MediaStorage.class);
            assertThat(application.getBean(MediaStorage.class)).isInstanceOf(S3MediaStorage.class);
        });
    }

    @Test
    void explicitCloudinaryConfigurationSelectsTheExistingProvider() {
        context.withPropertyValues("media.storage.provider=CLOUDINARY").run(application -> {
            assertThat(application).hasNotFailed().hasSingleBean(MediaStorage.class);
            assertThat(application.getBean(MediaStorage.class)).isInstanceOf(CloudinaryMediaStorage.class);
        });
    }
}
