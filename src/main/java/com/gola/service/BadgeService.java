package com.gola.service;

import com.gola.dto.quest.BadgeResponse;
import com.gola.entity.Badge;
import com.gola.repository.BadgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BadgeService {

    private final BadgeRepository badgeRepo;

    public List<BadgeResponse> getAllActiveBadges() {
        return badgeRepo.findAll().stream()
                .filter(Badge::isActive)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private BadgeResponse toResponse(Badge badge) {
        return BadgeResponse.builder()
                .id(badge.getId())
                .name(badge.getName())
                .iconUrl(badge.getIconUrl())
                .criteria(badge.getCriteria())
                .isActive(badge.isActive())
                .build();
    }
}
