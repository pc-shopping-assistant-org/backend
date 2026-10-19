package com.ecm.identity.service.google;

import com.ecm.identity.client.MediaServiceClient;
import com.ecm.identity.config.AsyncConfig;
import com.ecm.identity.config.JwtTokenProvider;
import com.ecm.identity.dto.response.MediaFileResponse;
import com.ecm.identity.event.GoogleAvatarRequested;
import com.ecm.identity.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

/**
 * Copies the Google profile picture into the Media Service after a Google signup is saved.
 * It runs on its own thread so a slow download or upload never delays the login, and any failure
 * is only logged: the customer simply keeps the default avatar.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleAvatarImporter {

    private static final String ROLE_CUSTOMER = "ROLE_CUSTOMER";
    private static final String ACTIVE = "ACTIVE";
    private static final String GOOGLE_PICTURE_HOST_SUFFIX = ".googleusercontent.com";
    private static final int MAX_PICTURE_BYTES = 2 * 1024 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final MediaServiceClient mediaServiceClient;
    private final CustomerRepository customerRepository;
    private final JwtTokenProvider tokenProvider;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Async(AsyncConfig.AVATAR_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(GoogleAvatarRequested event) {
        try {
            importAvatar(event);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            log.warn("Could not copy the Google picture of account {}: {}", event.accountId(), ex.getMessage());
        }
    }

    private void importAvatar(GoogleAvatarRequested event) throws IOException, InterruptedException {
        // 1. Only fetch from Google's own picture hosts, so a crafted claim cannot make this service call anywhere else
        URI uri = pictureUri(event.pictureUrl());
        if (uri == null) {
            log.info("Skipping the Google picture of account {}: not a Google picture address", event.accountId());
            return;
        }

        // 2. Download with a size cap and require an image
        HttpResponse<byte[]> response = httpClient.send(
                HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        byte[] bytes = response.body();
        if (response.statusCode() != 200 || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")
                || bytes.length == 0 || bytes.length > MAX_PICTURE_BYTES) {
            log.info("Skipping the Google picture of account {}: status {}, type {}, {} bytes",
                    event.accountId(), response.statusCode(), contentType, bytes.length);
            return;
        }

        // 3. Upload as the new customer, then attach it unless they already chose an avatar meanwhile
        String token = tokenProvider.generateAccessToken(event.accountId(), event.email(), ROLE_CUSTOMER, ACTIVE);
        MediaFileResponse file = mediaServiceClient.upload("Bearer " + token,
                new ByteArrayMultipartFile("file", "google-avatar", contentType, bytes)).getData();
        attach(event.accountId(), file.id());
    }

    private void attach(UUID accountId, UUID fileId) {
        customerRepository.setAvatarIfAbsent(accountId, fileId);
    }

    static URI pictureUri(String url) {
        if (url == null) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            boolean googleHost = host != null && host.toLowerCase(Locale.ROOT).endsWith(GOOGLE_PICTURE_HOST_SUFFIX);
            return "https".equalsIgnoreCase(uri.getScheme()) && googleHost ? uri : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
