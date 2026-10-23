package com.ecm.media.client;

/** Upload returns a newly created object; delete is used to compensate a failed metadata transaction. */
public interface MediaStorage {

    StoredMedia upload(byte[] content, String contentType);

    void delete(String storageKey);
}
