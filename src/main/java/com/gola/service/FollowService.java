package com.gola.service;

import com.gola.entity.Follow;
import com.gola.exception.GolaException;
import com.gola.repository.FollowRepository;
import com.gola.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowRepository followRepo;
    private final ProfileRepository profileRepo;
    private final NotificationService notificationService;

    @Transactional
    public void followUser(UUID followerId, UUID followeeId) {
        if (followerId.equals(followeeId)) {
            throw GolaException.badRequest("Cannot follow yourself");
        }
        profileRepo.findActiveById(followeeId)
            .orElseThrow(() -> GolaException.notFound("Profile"));
        if (followRepo.existsByFollowerIdAndFolloweeId(followerId, followeeId)) {
            return;
        }

        Follow follow = Follow.builder()
                .followerId(followerId)
                .followeeId(followeeId)
                .build();
        followRepo.save(follow);

        try {
            String actorName = profileRepo.findById(followerId)
                    .map(profile -> profile.getDisplayName() != null && !profile.getDisplayName().isBlank() ? profile.getDisplayName() : "Một thành viên GOLA")
                    .orElse("Một thành viên GOLA");
            notificationService.notifySocial(
                    followeeId,
                    followerId,
                    "FOLLOWED_YOU",
                    "Người theo dõi mới",
                    actorName + " đã theo dõi bạn.",
                    "PROFILE",
                    followerId,
                    "/community"
            );
        } catch (Exception ignored) {
            // Follow must not fail if notification storage is temporarily unavailable.
        }
    }

    @Transactional
    public void unfollowUser(UUID followerId, UUID followeeId) {
        if (!followRepo.existsByFollowerIdAndFolloweeId(followerId, followeeId)) {
            return;
        }
        followRepo.deleteByFollowerIdAndFolloweeId(followerId, followeeId);
    }
}
