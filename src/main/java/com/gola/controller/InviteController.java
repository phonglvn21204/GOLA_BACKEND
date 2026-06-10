package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.entity.TripInvitation;
import com.gola.entity.enums.MemberRole;
import com.gola.repository.TripInvitationRepository;
import com.gola.security.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/invites")
@RequiredArgsConstructor
@Tag(name = "Invites")
public class InviteController {
    private final TripInvitationRepository invitationRepo;

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(@RequestBody InviteRequest req) {
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        if (req.getTripId() != null && req.getEmail() != null && !req.getEmail().isBlank()) {
            invitationRepo.save(TripInvitation.builder()
                    .tripId(req.getTripId())
                    .email(req.getEmail())
                    .userId(SecurityUtils.getCurrentUserId())
                    .token(token)
                    .role(req.getRole() != null ? req.getRole() : MemberRole.VIEWER)
                    .expiresAt(Instant.now().plusSeconds(7 * 86400L))
                    .build());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Invite created", Map.of(
                "code", token,
                "token", token,
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
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.ok(Map.of("code", code))));
    }

    @Data
    public static class InviteRequest {
        private UUID tripId;
        private String email;
        private MemberRole role;
    }
}
