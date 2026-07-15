package com.gola.dto.trip;
import com.gola.entity.enums.MemberRole;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data @Builder
public class TripMemberResponse {
    private UUID userId;
    private String displayName;
    private String avatarUrl;
    private String phone;
    private MemberRole role;
    private Instant joinedAt;
}
