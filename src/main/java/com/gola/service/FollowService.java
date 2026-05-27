package com.gola.service;

import com.gola.entity.Follow;
import com.gola.exception.GolaException;
import com.gola.repository.FollowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowRepository followRepo;

    @Transactional
    public void followUser(UUID followerId, UUID followeeId) {
        if (followerId.equals(followeeId)) {
            throw GolaException.badRequest("Cannot follow yourself");
        }
        if (followRepo.existsByFollowerIdAndFolloweeId(followerId, followeeId)) {
            return;
        }

        Follow follow = Follow.builder()
                .followerId(followerId)
                .followeeId(followeeId)
                .build();
        followRepo.save(follow);
    }

    @Transactional
    public void unfollowUser(UUID followerId, UUID followeeId) {
        if (!followRepo.existsByFollowerIdAndFolloweeId(followerId, followeeId)) {
            throw GolaException.notFound("Follow relationship");
        }
        followRepo.deleteByFollowerIdAndFolloweeId(followerId, followeeId);
    }
}
