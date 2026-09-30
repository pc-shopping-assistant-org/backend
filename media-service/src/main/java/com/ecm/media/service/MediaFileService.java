package com.ecm.media.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.media.dto.response.MediaFileResponse;
import com.ecm.media.entity.FileStatus;
import com.ecm.media.entity.MediaFile;
import com.ecm.media.exception.MediaErrorCode;
import com.ecm.media.repository.MediaFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaFileService {

    private static final String STORAGE_PROVIDER = "CLOUDINARY";
    private static final String RESOURCE_TYPE = "image";
    private final MediaFileRepository mediaFileRepository;
    private final ObjectProvider<Cloudinary> cloudinaryProvider;

    @Transactional
    public MediaFileResponse uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getContentType() == null
                || !file.getContentType().toLowerCase().startsWith("image/")) {
            throw new BusinessException(MediaErrorCode.INVALID_FILE);
        }

        Cloudinary cloudinary = cloudinaryProvider.getIfAvailable();
        if (cloudinary == null) {
            throw new BusinessException(MediaErrorCode.CLOUDINARY_UPLOAD_FAILED,
                    "Cloudinary credentials are not configured");
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "resource_type", RESOURCE_TYPE,
                    "folder", "pc-shopping"
            ));
            String publicId = (String) result.get("public_id");
            String secureUrl = (String) result.get("secure_url");
            if (publicId == null || secureUrl == null) {
                throw new BusinessException(MediaErrorCode.CLOUDINARY_UPLOAD_FAILED,
                        "Cloudinary did not return image metadata");
            }
            MediaFile saved = mediaFileRepository.save(MediaFile.builder()
                    .storageProvider(STORAGE_PROVIDER)
                    .storageKey(publicId)
                    .originalName(file.getOriginalFilename() == null ? "image" : file.getOriginalFilename())
                    .mimeType(file.getContentType())
                    .sizeBytes(file.getSize())
                    .publicUrl(secureUrl)
                    .status(FileStatus.ACTIVE)
                    .build());
            return toResponse(saved);
        } catch (IOException ex) {
            throw new BusinessException(MediaErrorCode.CLOUDINARY_UPLOAD_FAILED,
                    "Could not upload image to Cloudinary", ex);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(MediaErrorCode.CLOUDINARY_UPLOAD_FAILED,
                    "Could not upload image to Cloudinary", ex);
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
