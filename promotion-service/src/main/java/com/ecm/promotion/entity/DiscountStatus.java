package com.ecm.promotion.entity;

/** INACTIVE means locked by an employee; expiry is not a status, it is derived from {@code endAt}. */
public enum DiscountStatus {
    ACTIVE,
    INACTIVE,
    DELETED
}
