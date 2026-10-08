package com.ecm.promotion.entity;

/** What an employee sees in the discount list: the stored status combined with the validity period. */
public enum DiscountState {
    SCHEDULED,
    RUNNING,
    EXPIRED,
    LOCKED
}
