package com.gola.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Entity
@Table(name = "badges")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Badge extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "icon_url")
    private String iconUrl;

    @Column(length = 1000)
    private String criteria;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;
}
