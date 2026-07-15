package com.gola.repository;

import com.gola.entity.AlbumMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AlbumMediaRepository extends JpaRepository<AlbumMedia, UUID> {
    List<AlbumMedia> findByAlbumIdOrderByDayIndexAscOrderIdxAsc(UUID albumId);
    void deleteByAlbumId(UUID albumId);
}
