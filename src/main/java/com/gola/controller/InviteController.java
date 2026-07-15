package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.entity.TripMember;
import com.gola.entity.TripInvitation;
import com.gola.entity.enums.MemberRole;
import com.gola.entity.enums.InvitationStatus;
import com.gola.exception.GolaException;
import com.gola.repository.TripInvitationRepository;
import com.gola.repository.TripMemberRepository;
import com.gola.repository.TripRepository;
import com.gola.repository.ProfileRepository;
import com.gola.security.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/invites")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Invites")
public class InviteController {
    private final TripInvitationRepository invitationRepo;
    private final TripMemberRepository memberRepo;
    private final TripRepository tripRepo;
    private final ProfileRepository profileRepo;

    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(@RequestBody InviteRequest req) {
        if (req.getTripId() == null) {
            throw GolaException.badRequest("tripId is required");
        }

        UUID currentUserId = SecurityUtils.getCurrentUserId();
        tripRepo.findActiveById(req.getTripId()).orElseThrow(() -> GolaException.notFound("Trip"));
        if (!memberRepo.existsByTripIdAndUserIdAndRoleIn(
                req.getTripId(), currentUserId, java.util.List.of(MemberRole.OWNER, MemberRole.EDITOR))) {
            throw GolaException.forbidden();
        }

        String email = req.getEmail() != null ? req.getEmail().trim().toLowerCase() : "";
        UUID inviteeUserId = email.isBlank()
                ? null
                : profileRepo.findByEmail(email).map(profile -> profile.getId()).orElse(null);
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try {
            invitationRepo.save(TripInvitation.builder()
                    .tripId(req.getTripId())
                    .email(email)
                    .userId(inviteeUserId)
                    .createdBy(currentUserId)
                    .token(token)
                    .role(req.getRole() != null ? req.getRole() : MemberRole.VIEWER)
                    .expiresAt(Instant.now().plusSeconds(7 * 86400L))
                    .build());
        } catch (RuntimeException e) {
            log.error("Failed to create trip invite: tripId={} userId={} email={} token={}",
                    req.getTripId(), currentUserId, req.getEmail(), token, e);
            throw e;
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Invite created", Map.of(
                "code", token,
                "token", token,
                "tripId", req.getTripId(),
                "email", email,
                "status", InvitationStatus.PENDING,
                "role", req.getRole() != null ? req.getRole() : MemberRole.VIEWER,
                "url", "/invites/" + token
        )));
    }

    @GetMapping("/{code}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable String code) {
        return invitationRepo.findByToken(code)
                .<ResponseEntity<ApiResponse<Map<String, Object>>>>map(invite -> ResponseEntity.ok(ApiResponse.ok(Map.of(
                        "code", invite.getToken(),
                        "tripId", invite.getTripId(),
                        "email", invite.getEmail(),
                        "status", invite.getStatus(),
                        "role", invite.getRole()
                ))))
                .orElseThrow(() -> GolaException.notFound("Invite"));
    }

    @PostMapping("/{code}/accept")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> accept(@PathVariable String code) {
        UUID userId = SecurityUtils.getCurrentUserId();
        TripInvitation invite = invitationRepo.findByToken(code)
                .orElseThrow(() -> GolaException.notFound("Invite"));

        if (invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(Instant.now())) {
            invite.setStatus(InvitationStatus.EXPIRED);
            invitationRepo.save(invite);
            throw GolaException.badRequest("Invite expired");
        }

        if (invite.getStatus() != InvitationStatus.PENDING && invite.getStatus() != InvitationStatus.ACCEPTED) {
            throw GolaException.badRequest("Invite is not pending");
        }

        var profile = profileRepo.findById(userId).orElseThrow(() -> GolaException.notFound("Profile"));
        if (invite.getEmail() != null && !invite.getEmail().isBlank()
                && !invite.getEmail().equalsIgnoreCase(profile.getEmail())) {
            throw GolaException.forbidden();
        }

        if (!memberRepo.existsByTripIdAndUserId(invite.getTripId(), userId)) {
            memberRepo.save(TripMember.builder()
                    .tripId(invite.getTripId())
                    .userId(userId)
                    .role(invite.getRole() != null ? invite.getRole() : MemberRole.VIEWER)
                    .build());
        }

        invite.setStatus(InvitationStatus.ACCEPTED);
        invite.setUserId(userId);
        invitationRepo.save(invite);

        return ResponseEntity.ok(ApiResponse.ok("Invite accepted", Map.of(
                "code", invite.getToken(),
                "tripId", invite.getTripId(),
                "status", invite.getStatus(),
                "role", invite.getRole()
        )));
    }

    @Data
    public static class InviteRequest {
        private UUID tripId;
        private String email;
        private MemberRole role;
    }
}
