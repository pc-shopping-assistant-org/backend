package com.ecm.catalog.service;

import com.ecm.catalog.client.MediaServiceClient;
import com.ecm.catalog.dto.response.MediaFileResponse;
import com.ecm.common.response.ApiResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductMediaResolverTest {
    @Test
    void resolvesLargeGalleriesWithoutOversizedGetRequests() {
        MediaServiceClient client = mock(MediaServiceClient.class);
        List<UUID> ids = IntStream.range(0, 1700).mapToObj(i -> new UUID(0, i)).toList();
        when(client.getFiles(anyList())).thenAnswer(invocation -> {
            List<UUID> batch = invocation.getArgument(0);
            assertTrue(batch.size() <= 100, "GET query must fit the media request header limit");
            return ApiResponse.success(batch.stream().map(id ->
                    new MediaFileResponse(id, "photo.jpg", "image/jpeg", 100L,
                            "https://media.example/" + id, null)).toList());
        });

        ProductMediaResolver resolver = new ProductMediaResolver(client);
        assertEquals(ids.size(), resolver.resolveUrls(ids).size());
        resolver.requireExisting(ids);
    }
}
