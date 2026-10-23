package com.ecm.media.client;

public record StoredMedia(StorageProvider provider, String storageKey, String publicUrl) {}
