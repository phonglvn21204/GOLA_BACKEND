package com.gola.repository;
import com.gola.entity.TripShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TripShareRepository extends JpaRepository<TripShare, UUID> {
    Optional<TripShare> findByToken(String token);
}