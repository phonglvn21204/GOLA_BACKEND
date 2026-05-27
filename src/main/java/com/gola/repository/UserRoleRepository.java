package com.gola.repository;

import com.gola.entity.UserRole;
import com.gola.entity.enums.AppRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {
    List<UserRole> findByProfile_Id(UUID userId);
    boolean existsByProfile_IdAndRole(UUID userId, AppRole role);
    void deleteByProfile_Id(UUID userId);
}