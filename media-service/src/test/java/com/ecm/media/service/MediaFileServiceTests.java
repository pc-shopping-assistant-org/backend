package com.ecm.media.service;

import com.cloudinary.Cloudinary;
import com.ecm.common.exception.BusinessException;
import com.ecm.media.exception.MediaErrorCode;
import com.ecm.media.repository.MediaFileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class MediaFileServiceTests {

    private final MediaFileRepository repository = mock(MediaFileRepository.class);
    private final ObjectProvider<Cloudinary> cloudinaryProvider = mock(ObjectProvider.class);
    private final MediaFileService service = new MediaFileService(repository, cloudinaryProvider);

    @Test
    void rejectsEmptyUploadBeforeCheckingCloudinaryConfiguration() {
        MockMultipartFile file = new MockMultipartFile("file", "", "image/png", new byte[0]);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.uploadImage(file));

        assertEquals(MediaErrorCode.INVALID_FILE, exception.getErrorCode());
    }

    @Test
    void rejectsNonImageUploadBeforeCheckingCloudinaryConfiguration() {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());

        BusinessException exception = assertThrows(BusinessException.class, () -> service.uploadImage(file));

        assertEquals(MediaErrorCode.INVALID_FILE, exception.getErrorCode());
    }

    @Test
    void reportsMissingCloudinaryBeanClearly() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1});

        BusinessException exception = assertThrows(BusinessException.class, () -> service.uploadImage(file));

        assertEquals(MediaErrorCode.CLOUDINARY_UPLOAD_FAILED, exception.getErrorCode());
    }
}
