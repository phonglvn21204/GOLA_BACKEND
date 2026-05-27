package com.gola.entity;

import com.gola.entity.enums.DispatchChannel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "sos_dispatch_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SosDispatchLog {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "sos_id", nullable = false) private UUID sosId;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "dispatch_channel")
    private DispatchChannel channel;
    private String target;
    @Builder.Default private String status = "PENDING";
    @Column(name = "provider_id") private String providerId;
    private String error;
    @Column(name = "sent_at") private Instant sentAt = Instant.now();
}