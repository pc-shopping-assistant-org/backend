package com.ecm.identity.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ExternalServiceException;
import com.ecm.identity.client.MediaServiceClient;
import com.ecm.identity.dto.response.MediaFileResponse;
import com.ecm.identity.exception.IdentityErrorCode;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/** Looks up avatar files in the Media Service, which owns the files. */
@Component
@RequiredArgsConstructor
public class AvatarResolver {

    private static final String MEDIA_SERVICE = "media-service";

    private final MediaServiceClient mediaServiceClient;

    /** Fails with INVALID_AVATAR_FILE when the Media Service does not know the file. */
    public void requireExisting(UUID fileId) {
        if (fileId == null) {
            return;
        }
        boolean found = fetch(List.of(fileId)).stream().anyMatch(file -> fileId.equals(file.id()));
        if (!found) {
            throw new BusinessException(IdentityErrorCode.INVALID_AVATAR_FILE);
        }
    }

    /** Maps each file id to its URL in one batched call; files without a URL are left out. */
    public Map<UUID, String> resolveUrls(Collection<UUID> fileIds) {
        List<UUID> distinct = fileIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return fetch(distinct).stream()
                .filter(file -> file.url() != null)
                .collect(Collectors.toMap(MediaFileResponse::id, MediaFileResponse::url, (first, second) -> first));
    }

    private List<MediaFileResponse> fetch(List<UUID> fileIds) {
        try {
            return mediaServiceClient.getFiles(fileIds).getData();
        } catch (FeignException ex) {
            throw new ExternalServiceException(MEDIA_SERVICE, ex);
        }
    }
}
