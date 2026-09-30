package com.ecm.media.repository;

import com.ecm.media.entity.FileStatus;
import com.ecm.media.entity.MediaFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MediaFileRepository extends JpaRepository<MediaFile, UUID> {

    List<MediaFile> findByIdInAndStatus(Collection<UUID> ids, FileStatus status);
}
