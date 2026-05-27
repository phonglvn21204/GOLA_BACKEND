package com.gola.service;

import com.gola.dto.user.ProfileResponse;
import com.gola.dto.user.UpdateProfileRequest;
import com.gola.entity.Profile;
import com.gola.exception.GolaException;
import com.gola.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepo;

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

    // ── helper ───────────────────────────────────────────────────────────────

    private Profile findProfile(UUID userId) {
        return profileRepo.findById(userId)
                .orElseThrow(() -> GolaException.notFound("Profile"));
    }

    private ProfileResponse toResponse(Profile p) {
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
                .onboardedAt(p.getOnboardedAt())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
