package com.gola.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "saved_places")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedPlace extends BaseEntity {
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "place_id")
    private UUID placeId;

    @Column(name = "external_place_id")
    private String externalPlaceId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String address;

    private String category;

    private Double latitude;

    private Double longitude;

    private String provider;
}
