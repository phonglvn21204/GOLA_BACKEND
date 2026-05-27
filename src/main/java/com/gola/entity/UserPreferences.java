package com.gola.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "user_preferences")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserPreferences extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @ElementCollection
    @CollectionTable(name = "user_pref_travel_styles",
            joinColumns = @JoinColumn(name = "user_preferences_id"))
    @Column(name = "style")
    @Builder.Default
    private List<String> travelStyles = List.of();

    @ElementCollection
    @CollectionTable(name = "user_pref_interests",
            joinColumns = @JoinColumn(name = "user_preferences_id"))
    @Column(name = "interest")
    @Builder.Default
    private List<String> interests = List.of();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "dietary", columnDefinition = "text[]")
    @Builder.Default
    private String[] dietary = new String[0];

    @Column(name = "budget_band")
    private String budgetBand;
}
