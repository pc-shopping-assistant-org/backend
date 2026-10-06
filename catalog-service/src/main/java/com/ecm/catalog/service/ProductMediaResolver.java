package com.ecm.catalog.service;

import com.ecm.catalog.client.MediaServiceClient;
import com.ecm.catalog.dto.response.MediaFileResponse;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ExternalServiceException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/** Looks up product and variant images in the Media Service, which owns the files. */
@Component
@RequiredArgsConstructor
public class ProductMediaResolver {

    private static final String MEDIA_SERVICE = "media-service";

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
