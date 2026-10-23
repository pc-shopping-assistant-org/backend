package com.ecm.media.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.media.client.MediaStorage;
import com.ecm.media.client.StoredMedia;
import com.ecm.media.dto.response.MediaFileResponse;
import com.ecm.media.entity.FileStatus;
import com.ecm.media.entity.MediaFile;
import com.ecm.media.exception.MediaErrorCode;
import com.ecm.media.repository.MediaFileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class MediaFileService {

    private static final Logger LOG = LoggerFactory.getLogger(MediaFileService.class);
    private static final String DEFAULT_ORIGINAL_NAME = "image";
    private final MediaFileRepository mediaFileRepository;
    private final MediaStorage storage;
    private final TransactionTemplate transactions;

    public MediaFileService(MediaFileRepository mediaFileRepository, MediaStorage storage,
                            PlatformTransactionManager transactionManager) {
        this.mediaFileRepository = mediaFileRepository;
        this.storage = storage;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public MediaFileResponse uploadImage(MultipartFile file) {
        // 1. Reject invalid uploads before making any storage call.
        if (file == null || file.isEmpty() || file.getContentType() == null
                || !file.getContentType().toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new BusinessException(MediaErrorCode.INVALID_FILE);
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(MediaErrorCode.STORAGE_UPLOAD_FAILED, "Could not read uploaded image", ex);
        }

        // 2. Upload before opening the metadata transaction.
        StoredMedia uploaded = storage.upload(content, file.getContentType());
        try {
            // 3. The transaction completes here, so persistence and commit failures trigger compensation.
            return transactions.execute(status -> toResponse(mediaFileRepository.saveAndFlush(MediaFile.builder()
                    .storageProvider(uploaded.provider().name())
                    .storageKey(uploaded.storageKey())
                    .originalName(file.getOriginalFilename() == null ? DEFAULT_ORIGINAL_NAME : file.getOriginalFilename())
                    .mimeType(file.getContentType())
                    .sizeBytes((long) content.length)
                    .publicUrl(uploaded.publicUrl())
                    .status(FileStatus.ACTIVE)
                    .build())));
        } catch (RuntimeException persistenceFailure) {
            removeUncommittedUpload(uploaded, persistenceFailure);
            throw new BusinessException(MediaErrorCode.STORAGE_UPLOAD_FAILED,
                    "Could not save image metadata", persistenceFailure);
        }
    }

    private void removeUncommittedUpload(StoredMedia uploaded, RuntimeException persistenceFailure) {
        try {
            storage.delete(uploaded.storageKey());
        } catch (RuntimeException cleanupFailure) {
            persistenceFailure.addSuppressed(cleanupFailure);
            LOG.error("Could not remove {} object {} after failed metadata transaction",
                    uploaded.provider(), uploaded.storageKey(), cleanupFailure);
        }
    }

    @Transactional(readOnly = true)
    public List<MediaFileResponse> getActiveFiles(Collection<UUID> ids) {
        return mediaFileRepository.findByIdInAndStatus(ids, FileStatus.ACTIVE).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MediaFileResponse getActiveFile(UUID id) {
        MediaFile file = mediaFileRepository.findByIdInAndStatus(List.of(id), FileStatus.ACTIVE).stream()
                .findFirst().orElseThrow(() -> new ResourceNotFoundException("File", id));
        return toResponse(file);
    }

    private MediaFileResponse toResponse(MediaFile file) {
        return new MediaFileResponse(file.getId(), file.getOriginalName(), file.getMimeType(),
                file.getSizeBytes(), file.getPublicUrl(), file.getCreatedAt());
    }
}
