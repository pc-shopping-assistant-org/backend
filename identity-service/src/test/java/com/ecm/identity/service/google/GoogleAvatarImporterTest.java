package com.ecm.identity.service.google;

import com.ecm.identity.client.MediaServiceClient;
import com.ecm.identity.config.JwtTokenProvider;
import com.ecm.identity.event.GoogleAvatarRequested;
import com.ecm.identity.repository.CustomerRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class GoogleAvatarImporterTest {

    @Test
    void fetchesOnlyHttpsPicturesFromGoogleHosts() {
        assertNotNull(GoogleAvatarImporter.pictureUri("https://lh3.googleusercontent.com/a/abc=s96-c"));
        assertNull(GoogleAvatarImporter.pictureUri("http://lh3.googleusercontent.com/a/abc"));
        assertNull(GoogleAvatarImporter.pictureUri("https://evil.example.com/a/abc"));
        assertNull(GoogleAvatarImporter.pictureUri("https://googleusercontent.com.evil.example.com/a"));
        assertNull(GoogleAvatarImporter.pictureUri("https://localhost:8080/internal"));
        assertNull(GoogleAvatarImporter.pictureUri("not a url"));
        assertNull(GoogleAvatarImporter.pictureUri(null));
    }

    @Test
    void aPictureFromAnotherHostIsSkippedWithoutCallingAnyService() {
        MediaServiceClient media = mock(MediaServiceClient.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        GoogleAvatarImporter importer = new GoogleAvatarImporter(media, customers, mock(JwtTokenProvider.class));

        assertDoesNotThrow(() -> importer.on(new GoogleAvatarRequested(UUID.randomUUID(), "a@b.c", "https://evil.example.com/x")));

        verifyNoInteractions(media, customers);
    }
}
