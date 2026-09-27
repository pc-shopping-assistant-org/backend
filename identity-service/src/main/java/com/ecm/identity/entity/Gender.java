package com.ecm.identity.entity;

/**
 * Shared by {@link Employee} (DB-restricted to MALE/FEMALE via CHECK constraint) and
 * {@link Customer} (allows OTHER too).
 */
public enum Gender {
    MALE,
    FEMALE,
    OTHER
}
