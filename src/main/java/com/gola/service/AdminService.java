package com.gola.service;

import com.gola.dto.common.PageResponse;
import com.gola.dto.community.PostResponse;
import com.gola.dto.safety.IncidentResponse;
import com.gola.dto.user.ProfileResponse;
import com.gola.entity.Post;
import com.gola.entity.PostHashtag;
import com.gola.entity.Profile;
import com.gola.entity.SosEvent;
import com.gola.entity.UserRole;
import com.gola.entity.enums.AppRole;
import com.gola.entity.enums.QuestProgressStatus;
import com.gola.entity.enums.SosStatus;
import com.gola.entity.enums.TripStatus;
import com.gola.exception.GolaException;
import com.gola.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final ProfileRepository      profileRepo;
    private final TripRepository         tripRepo;
    private final ReportRepository       reportRepo;
    private final QuestProgressRepository questProgressRepo;
    private final IncidentRepository     incidentRepo;
    private final PostRepository         postRepo;
    private final PostHashtagRepository  postHashtagRepo;
    private final SosEventRepository     sosEventRepo;
    private final UserRoleRepository     userRoleRepo;
    private final IncidentService        incidentService;

    // ── Metrics ───────────────────────────────────────────────────────────────

    public Map<String, Object> getMetrics() {
        long totalUsers        = profileRepo.count();
        long activeTrips       = tripRepo.countByStatusAndDeletedAtIsNull(TripStatus.ACTIVE);
        long openReports       = reportRepo.findByStatus("OPEN").size();
        Instant since7d        = Instant.now().minus(7, ChronoUnit.DAYS);
        long questCompletions  = questProgressRepo.countByStatusAndVerifiedAtAfter(
                                     QuestProgressStatus.COMPLETED, since7d);

        return Map.of(
            "totalUsers",          totalUsers,
            "activeTrips",         activeTrips,
            "openReports",         openReports,
            "questCompletions7d",  questCompletions
        );
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    public PageResponse<ProfileResponse> listUsers(Pageable pageable) {
        return new PageResponse<>(profileRepo.findAll(pageable).map(this::mapProfile));
    }

    @Transactional
    public void changeUserRole(UUID userId, AppRole role) {
        Profile profile = profileRepo.findActiveById(userId)
                .orElseThrow(() -> GolaException.notFound("User"));

        List<UserRole> existingRoles = userRoleRepo.findByProfile_Id(userId);
        if (existingRoles.size() == 1 && existingRoles.get(0).getRole() == role) {
            return;
        }

        userRoleRepo.deleteByProfile_Id(userId);
        userRoleRepo.flush();

        UserRole newRole = UserRole.builder()
                .profile(profile)
                .role(role)
                .build();
        userRoleRepo.save(newRole);
        log.info("Admin changed role of user {} to {}", userId, role);
    }

    // ── Incidents ─────────────────────────────────────────────────────────────

    public PageResponse<IncidentResponse> listAllIncidents(Pageable pageable) {
        return new PageResponse<>(incidentRepo.findAll(pageable).map(incidentService::mapToResponse));
    }

    @Transactional
    public IncidentResponse updateIncidentStatus(UUID id, String status) {
        return incidentService.updateIncidentStatus(id, status);
    }

    // ── Posts ─────────────────────────────────────────────────────────────────

    public PageResponse<PostResponse> listAllPosts(Pageable pageable) {
        return new PageResponse<>(postRepo.findAll(pageable).map(this::mapPost));
    }

    @Transactional
    public void hidePost(UUID postId) {
        Post post = postRepo.findById(postId)
                .orElseThrow(() -> GolaException.notFound("Post"));
        post.setHidden(true);
        postRepo.save(post);
        log.info("Admin hid post {}", postId);
    }

    // ── SOS ───────────────────────────────────────────────────────────────────

    public List<SosEvent> getActiveSosEvents() {
        return sosEventRepo.findByStatus(SosStatus.ACTIVE);
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private ProfileResponse mapProfile(Profile p) {
        return ProfileResponse.builder()
                .id(p.getId())
                .email(p.getEmail())
                .displayName(p.getDisplayName())
                .avatarUrl(p.getAvatarUrl())
                .bio(p.getBio())
                .isPublic(p.isPublic())
                .emailVerified(p.isEmailVerified())
                .onboardedAt(p.getOnboardedAt())
                .createdAt(p.getCreatedAt())
                .build();
    }

    private PostResponse mapPost(Post p) {
        List<String> tags = postHashtagRepo.findByPostId(p.getId()).stream()
                .map(PostHashtag::getTag)
                .collect(Collectors.toList());
        return PostResponse.builder()
                .id(p.getId())
                .authorId(p.getAuthorId())
                .body(p.getBody())
                .mediaUrls(p.getMediaUrls())
                .tripId(p.getTripId())
                .isHidden(p.isHidden())
                .hashtags(tags)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
