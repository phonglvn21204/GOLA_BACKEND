package com.gola.service;

import com.gola.dto.user.ProfileResponse;
import com.gola.dto.user.UpdateProfileRequest;
import com.gola.entity.Profile;
import com.gola.entity.UserRole;
import com.gola.entity.enums.AppRole;
import com.gola.entity.enums.SubStatus;
import com.gola.exception.GolaException;
import com.gola.repository.ProfileRepository;
import com.gola.repository.SubscriptionRepository;
import com.gola.repository.TripRepository;
import com.gola.repository.TripStopRepository;
import com.gola.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepo;
    private final SubscriptionRepository subscriptionRepo;
    private final UserRoleRepository userRoleRepo;
    private final TripRepository tripRepo;
    private final TripStopRepository tripStopRepo;

    public ProfileResponse getMyProfile(UUID userId) {
        return toResponse(findProfile(userId));
    }

    public ProfileResponse getProfile(UUID targetUserId, UUID requesterId) {
        Profile profile = findProfile(targetUserId);
        if (!profile.isPublic() && !targetUserId.equals(requesterId)) {
            throw GolaException.forbidden();
        }
        return toResponse(profile);
    }

    @Transactional
    public ProfileResponse updateProfile(UUID userId, UpdateProfileRequest req) {
        Profile profile = findProfile(userId);

        if (req.getDisplayName() != null) profile.setDisplayName(req.getDisplayName());
        if (req.getAvatarUrl()   != null) profile.setAvatarUrl(req.getAvatarUrl());
        if (req.getBio()         != null) profile.setBio(req.getBio());
        if (req.getLocale()      != null) profile.setLocale(req.getLocale());
        if (req.getTheme()       != null) profile.setTheme(req.getTheme());
        if (req.getHomeCity()    != null) profile.setHomeCity(req.getHomeCity());
        if (req.getPhone()       != null) profile.setPhone(req.getPhone());
        if (req.getIsPublic()    != null) profile.setPublic(req.getIsPublic());

        return toResponse(profileRepo.save(profile));
    }

    @Transactional
    public ProfileResponse updateAvatar(UUID userId, String avatarUrl) {
        Profile profile = findProfile(userId);
        profile.setAvatarUrl(avatarUrl);
        return toResponse(profileRepo.save(profile));
    }

    @Transactional
    public ProfileResponse completeOnboarding(UUID userId, UpdateProfileRequest req) {
        Profile profile = findProfile(userId);
        if (req.getHomeCity() != null) profile.setHomeCity(req.getHomeCity());
        if (req.getLocale() != null) profile.setLocale(req.getLocale());
        if (req.getTheme() != null) profile.setTheme(req.getTheme());
        profile.setOnboardedAt(Instant.now());
        return toResponse(profileRepo.save(profile));
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private Profile findProfile(UUID userId) {
        return profileRepo.findById(userId)
                .orElseThrow(() -> GolaException.notFound("Profile"));
    }

    private ProfileResponse toResponse(Profile p) {
        List<String> roles = userRoleRepo.findByProfile_Id(p.getId()).stream()
                .map(UserRole::getRole)
                .map(AppRole::name)
                .distinct()
                .toList();
        if (roles.isEmpty()) {
            roles = List.of(AppRole.USER.name());
        }

        ProfileStats stats = calculateStats(p.getId());

        return ProfileResponse.builder()
                .id(p.getId())
                .email(p.getEmail())
                .displayName(p.getDisplayName())
                .avatarUrl(p.getAvatarUrl())
                .bio(p.getBio())
                .locale(p.getLocale())
                .theme(p.getTheme())
                .homeCity(p.getHomeCity())
                .isPublic(p.isPublic())
                .phone(p.getPhone())
                .emailVerified(p.isEmailVerified())
                .isPremium(subscriptionRepo.findFirstByUserIdAndStatusOrderByCurrentPeriodEndDesc(p.getId(), SubStatus.ACTIVE).isPresent())
                .roles(roles)
                .isAdmin(roles.contains(AppRole.ADMIN.name()))
                .isBlocked(p.isBlocked())
                .blockedAt(p.getBlockedAt())
                .blockReason(p.getBlockReason())
                .totalDistanceKm(stats.totalDistanceKm())
                .totalTrips(stats.totalTrips())
                .totalStops(stats.totalStops())
                .onboardedAt(p.getOnboardedAt())
                .createdAt(p.getCreatedAt())
                .build();
    }

    private ProfileStats calculateStats(UUID userId) {
        List<com.gola.entity.Trip> trips = tripRepo.findAllForUser(userId);
        long stopsCount = 0;
        double totalKm = 0.0;
        for (com.gola.entity.Trip trip : trips) {
            List<com.gola.entity.TripStop> stops = tripStopRepo.findByTrip_IdOrderByOrderIdxAsc(trip.getId());
            stopsCount += stops.size();
            com.gola.entity.TripStop previous = null;
            for (com.gola.entity.TripStop stop : stops) {
                if (!hasTrustedCoordinate(stop)) {
                    continue;
                }
                if (previous != null) {
                    double segment = haversineKm(previous.getLat(), previous.getLng(), stop.getLat(), stop.getLng());
                    if (segment > 0.02) {
                        totalKm += segment;
                    }
                }
                previous = stop;
            }
        }
        double rounded = Math.round(totalKm * 10.0) / 10.0;
        return new ProfileStats(rounded, (long) trips.size(), stopsCount);
    }

    private boolean hasTrustedCoordinate(com.gola.entity.TripStop stop) {
        return Boolean.TRUE.equals(stop.getHasRealCoordinates())
            && stop.getLat() != null
            && stop.getLng() != null
            && Double.isFinite(stop.getLat())
            && Double.isFinite(stop.getLng())
            && stop.getLat() != 0.0
            && stop.getLng() != 0.0;
    }

    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private record ProfileStats(Double totalDistanceKm, Long totalTrips, Long totalStops) {}
}
