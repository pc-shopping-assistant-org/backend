package com.ecm.media.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.media.client.MediaStorage;
import com.ecm.media.client.StorageProvider;
import com.ecm.media.client.StoredMedia;
import com.ecm.media.entity.FileStatus;
import com.ecm.media.entity.MediaFile;
import com.ecm.media.exception.MediaErrorCode;
import com.ecm.media.repository.MediaFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MediaFileServiceTests {

    private static final UUID FILE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final StoredMedia UPLOADED = new StoredMedia(StorageProvider.S3, "images/new-image",
            "https://files.example.test/pcshop-media/images/new-image");
    private final MediaFileRepository repository = mock(MediaFileRepository.class);
    private final MediaStorage storage = mock(MediaStorage.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final TransactionStatus transactionStatus = mock(TransactionStatus.class);
    private final MediaFileService service = new MediaFileService(repository, storage, transactionManager);

    @BeforeEach
    void configureTransaction() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
    }

    @Test
    void rejectsEmptyAndNonImageUploadsBeforeCallingStorage() {
        for (var file : List.of(new MockMultipartFile("file", "", "image/png", new byte[0]),
                new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes()))) {
            BusinessException failure = assertThrows(BusinessException.class, () -> service.uploadImage(file));
            assertEquals(MediaErrorCode.INVALID_FILE, failure.getErrorCode());
        }
        verifyNoInteractions(storage, repository, transactionManager);
    }

    @Test
    void storesAdapterMetadataAndCommitsBeforeReturningTheExistingResponseShape() {
        when(storage.upload(any(byte[].class), any(String.class))).thenReturn(UPLOADED);
        when(repository.saveAndFlush(any(MediaFile.class))).thenAnswer(call -> {
            MediaFile file = call.getArgument(0);
            file.setId(FILE_ID);
            file.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
            return file;
        });

        var response = service.uploadImage(image());

        assertEquals(FILE_ID, response.id());
        assertEquals(UPLOADED.publicUrl(), response.url());
        assertEquals("photo.png", response.originalName());
        assertEquals(3L, response.sizeBytes());
        var saved = ArgumentCaptor.forClass(MediaFile.class);
        var order = inOrder(storage, transactionManager, repository);
        order.verify(storage).upload(any(byte[].class), any(String.class));
        order.verify(transactionManager).getTransaction(any(TransactionDefinition.class));
        order.verify(repository).saveAndFlush(saved.capture());
        order.verify(transactionManager).commit(transactionStatus);
        assertEquals("S3", saved.getValue().getStorageProvider());
        assertEquals(UPLOADED.storageKey(), saved.getValue().getStorageKey());
        assertEquals(FileStatus.ACTIVE, saved.getValue().getStatus());
        verify(storage, never()).delete(any());
    }

    @Test
    void storageFailureDoesNotWriteMetadataOrStartATransaction() {
        var failure = new BusinessException(MediaErrorCode.STORAGE_UPLOAD_FAILED);
        when(storage.upload(any(byte[].class), any(String.class))).thenThrow(failure);

        assertSame(failure, assertThrows(BusinessException.class, () -> service.uploadImage(image())));
        verifyNoInteractions(repository, transactionManager);
        verify(storage, never()).delete(any());
    }

    @Test
    void failedMetadataWriteRollsBackAndDeletesOnlyTheNewObject() {
        when(storage.upload(any(byte[].class), any(String.class))).thenReturn(UPLOADED);
        var databaseFailure = new DataIntegrityViolationException("test constraint");
        when(repository.saveAndFlush(any())).thenThrow(databaseFailure);

        var failure = assertThrows(BusinessException.class, () -> service.uploadImage(image()));

        assertEquals(MediaErrorCode.STORAGE_UPLOAD_FAILED, failure.getErrorCode());
        assertSame(databaseFailure, failure.getCause());
        verify(transactionManager).rollback(transactionStatus);
        verify(storage).delete(UPLOADED.storageKey());
    }

    @Test
    void commitFailureAlsoCompensatesTheUploadedObject() {
        when(storage.upload(any(byte[].class), any(String.class))).thenReturn(UPLOADED);
        when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        doThrow(new TransactionSystemException("commit rejected")).when(transactionManager).commit(transactionStatus);

        assertThrows(BusinessException.class, () -> service.uploadImage(image()));

        verify(storage).delete(UPLOADED.storageKey());
    }

    @Test
    void cleanupFailureDoesNotHideTheMetadataFailure() {
        when(storage.upload(any(byte[].class), any(String.class))).thenReturn(UPLOADED);
        var databaseFailure = new DataIntegrityViolationException("test constraint");
        var cleanupFailure = new IllegalStateException("cleanup failed");
        when(repository.saveAndFlush(any())).thenThrow(databaseFailure);
        doThrow(cleanupFailure).when(storage).delete(UPLOADED.storageKey());

        var failure = assertThrows(BusinessException.class, () -> service.uploadImage(image()));

        assertSame(databaseFailure, failure.getCause());
        assertArrayEquals(new Throwable[] {cleanupFailure}, databaseFailure.getSuppressed());
    }

    @Test
    void historicalCloudinaryAndExternalRowsRemainReadableWithoutStorageCalls() {
        for (String provider : List.of("CLOUDINARY", "EXTERNAL")) {
            var existing = MediaFile.builder().id(FILE_ID).storageProvider(provider)
                    .publicUrl("https://old.example.test/image.png").status(FileStatus.ACTIVE).build();
            when(repository.findByIdInAndStatus(List.of(FILE_ID), FileStatus.ACTIVE)).thenReturn(List.of(existing));

            assertEquals(existing.getPublicUrl(), service.getActiveFile(FILE_ID).url());
            assertEquals(existing.getPublicUrl(), service.getActiveFiles(List.of(FILE_ID)).getFirst().url());
        }
        verifyNoInteractions(storage, transactionManager);
    }

    private static MockMultipartFile image() {
        return new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1, 2, 3});
    }
}
