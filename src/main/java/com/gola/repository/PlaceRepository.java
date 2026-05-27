package com.gola.repository;
import com.gola.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;
@Repository public interface PlaceRepository extends JpaRepository<Place, UUID> {
    @Query(value = "SELECT p.* FROM places p " +
                   "WHERE (:query IS NULL OR :query = '' " +
                   "OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) " +
                   "OR LOWER(p.category) LIKE LOWER(CONCAT('%', :query, '%'))) " +
                   "ORDER BY CASE WHEN :lat IS NOT NULL AND :lng IS NOT NULL " +
                   "THEN ST_Distance(p.geom, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) " +
                   "ELSE 0 END ASC", nativeQuery = true)
    List<Place> searchLocalPlaces(String query, Double lat, Double lng);
    java.util.Optional<Place> findByGooglePlaceId(String googlePlaceId);
}