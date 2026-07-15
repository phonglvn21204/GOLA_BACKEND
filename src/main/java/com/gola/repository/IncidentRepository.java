package com.gola.repository;
import com.gola.entity.Incident;
import com.gola.entity.enums.IncidentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;
@Repository public interface IncidentRepository extends JpaRepository<Incident, UUID> {
    Page<Incident> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable p);
    Page<Incident> findByTripIdOrderByCreatedAtDesc(UUID tripId, Pageable p);
    Page<Incident> findByStatusOrderByCreatedAtDesc(String status, Pageable p);
    Page<Incident> findByStatusAndTypeOrderByCreatedAtDesc(String status, IncidentType type, Pageable p);
}
