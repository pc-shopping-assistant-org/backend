package com.ecm.identity.dto.response;

import com.ecm.identity.entity.AccountStatus;
import com.ecm.identity.entity.Gender;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code avatarUrl} comes from the Media Service and is filled in after the row is read.
 */
public record CustomerResponse(
        UUID accountId,
        String email,
        String phone,
        AccountStatus status,
        String firstName,
        String lastName,
        Gender gender,
        LocalDate birthday,
        UUID avatarFileId,
        String avatarUrl,
        Instant createdAt) {

    /** For the JPQL constructor expression, which cannot know the avatar URL. */
    public CustomerResponse(UUID accountId, String email, String phone, AccountStatus status, String firstName,
                            String lastName, Gender gender, LocalDate birthday, UUID avatarFileId, Instant createdAt) {
        this(accountId, email, phone, status, firstName, lastName, gender, birthday, avatarFileId, null, createdAt);
    }

    public CustomerResponse withAvatarUrl(String url) {
        return new CustomerResponse(accountId, email, phone, status, firstName, lastName, gender, birthday,
                avatarFileId, url, createdAt);
    }
}
