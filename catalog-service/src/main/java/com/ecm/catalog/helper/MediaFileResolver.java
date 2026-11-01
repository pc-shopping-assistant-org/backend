package com.ecm.catalog.helper;

import com.ecm.catalog.client.MediaServiceClient;
import com.ecm.catalog.dto.response.MediaFileResponse;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ExternalServiceException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/** Looks up files (product, variant and brand images) in the Media Service, which owns them. */
@Component
@RequiredArgsConstructor
public class MediaFileResolver {

    private static final String MEDIA_SERVICE = "media-service";
    // Repeated UUID query parameters must stay below the media HTTP header limit.
    private static final int MEDIA_LOOKUP_BATCH_SIZE = 100;

    private final MediaServiceClient mediaServiceClient;

    /** Fails with INVALID_MEDIA_FILE when any of the files is unknown to the Media Service. */
    public void requireExisting(Collection<UUID> fileIds) {
        List<UUID> distinct = fileIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return;
        }
        long found = fetch(distinct).stream().map(MediaFileResponse::id).distinct().count();
        if (found != distinct.size()) {
            throw new BusinessException(CatalogErrorCode.INVALID_MEDIA_FILE);
        }
    }

    /** Maps file ids to URLs through bounded requests; files without a URL are left out. */
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
            List<MediaFileResponse> files = new ArrayList<>();
            for (int offset = 0; offset < fileIds.size(); offset += MEDIA_LOOKUP_BATCH_SIZE) {
                files.addAll(mediaServiceClient.getFiles(fileIds.subList(offset,
                        Math.min(offset + MEDIA_LOOKUP_BATCH_SIZE, fileIds.size()))).getData());
            }
            return files;
        } catch (FeignException ex) {
            throw new ExternalServiceException(MEDIA_SERVICE, ex);
        }
    }
}
