package com.ecm.catalog.helper;

import com.ecm.catalog.client.MediaServiceClient;
import com.ecm.catalog.dto.response.MediaFileResponse;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ExternalServiceException;
import com.ecm.common.response.ApiResponse;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MediaFileResolverTest {

    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-0000000000f2");

    private MediaServiceClient client;
    private MediaFileResolver resolver;

    @BeforeEach
    void setUp() {
        client = mock(MediaServiceClient.class);
        resolver = new MediaFileResolver(client);
    }

    private static MediaFileResponse file(UUID id, String url) {
        return new MediaFileResponse(id, "f.jpg", "image/jpeg", 100L, url, Instant.now());
    }

    @Test
    void resolvesDistinctIdsInOneBatchAndSkipsNulls() {
        when(client.getFiles(List.of(FIRST, SECOND))).thenReturn(ApiResponse.success(List.of(file(FIRST, "u1"), file(SECOND, "u2"))));

        Map<UUID, String> urls = resolver.resolveUrls(java.util.Arrays.asList(FIRST, null, SECOND, FIRST));

        assertEquals(Map.of(FIRST, "u1", SECOND, "u2"), urls);
        verify(client).getFiles(List.of(FIRST, SECOND));
    }

    @Test
    void anEmptyInputDoesNotCallTheMediaService() {
        assertEquals(Map.of(), resolver.resolveUrls(java.util.Arrays.asList((UUID) null)));
        verifyNoInteractions(client);
    }

    @Test
    void aRepeatedFileInTheAnswerKeepsTheFirstUrl() {
        when(client.getFiles(any())).thenReturn(ApiResponse.success(List.of(file(FIRST, "first"), file(FIRST, "second"))));

        assertEquals("first", resolver.resolveUrls(List.of(FIRST)).get(FIRST));
    }

    @Test
    void aMediaServiceFailureBecomesAnExternalServiceError() {
        when(client.getFiles(any())).thenThrow(mock(FeignException.class));

        assertThrows(ExternalServiceException.class, () -> resolver.resolveUrls(List.of(FIRST)));
    }

    @Test
    void anUnknownFileFailsTheExistenceCheck() {
        when(client.getFiles(any())).thenReturn(ApiResponse.success(List.of(file(FIRST, "u1"))));

        BusinessException ex = assertThrows(BusinessException.class, () -> resolver.requireExisting(List.of(FIRST, SECOND)));

        assertEquals(CatalogErrorCode.INVALID_MEDIA_FILE, ex.getErrorCode());
    }
}
