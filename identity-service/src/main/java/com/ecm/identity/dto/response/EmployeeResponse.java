package com.ecm.identity.dto.response;

import com.ecm.identity.entity.AccountStatus;
import com.ecm.identity.entity.Gender;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code avatarUrl} comes from the Media Service and is filled in after the row is read.
 */
public record EmployeeResponse(
        UUID accountId,
        String email,
        String phone,
        AccountStatus status,
        String firstName,
        String lastName,
        Gender gender,
        LocalDate birthday,
        String address,
        UUID avatarFileId,
        String avatarUrl,
        Instant createdAt) {

    /** For the JPQL constructor expression, which cannot know the avatar URL. */
    public EmployeeResponse(UUID accountId, String email, String phone, AccountStatus status, String firstName,
                            String lastName, Gender gender, LocalDate birthday, String address, UUID avatarFileId,
                            Instant createdAt) {
        this(accountId, email, phone, status, firstName, lastName, gender, birthday, address, avatarFileId, null, createdAt);
    }

    public EmployeeResponse withAvatarUrl(String url) {
        return new EmployeeResponse(accountId, email, phone, status, firstName, lastName, gender, birthday, address,
                avatarFileId, url, createdAt);
    }
}
