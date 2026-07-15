package com.gola.repository;

import com.gola.entity.TravelResearchCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TravelResearchCacheRepository extends JpaRepository<TravelResearchCache, UUID> {
    Optional<TravelResearchCache> findByCacheKey(String cacheKey);
}
