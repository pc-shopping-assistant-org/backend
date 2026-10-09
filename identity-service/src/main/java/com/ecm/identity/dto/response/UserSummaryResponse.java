package com.ecm.identity.dto.response;

import com.ecm.identity.entity.Gender;

import java.time.LocalDate;
import java.util.UUID;

public record UserSummaryResponse(
        UUID accountId,
        String email,
        String phone,
        String role,
        String firstName,
        String lastName,
        Gender gender,
        LocalDate birthday,
        UUID avatarFileId,
        String avatarUrl) {

    /** The same summary with the URL of the avatar file, which only the Media Service knows. */
    public UserSummaryResponse withAvatarUrl(String url) {
        return new UserSummaryResponse(accountId, email, phone, role, firstName, lastName, gender, birthday, avatarFileId, url);
    }
}
