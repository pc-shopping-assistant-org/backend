package com.ecm.identity.event;

import java.util.UUID;

/** Published when a customer signs up with Google, so their Google picture can be copied once the signup is saved. */
public record GoogleAvatarRequested(UUID accountId, String email, String pictureUrl) {
}
