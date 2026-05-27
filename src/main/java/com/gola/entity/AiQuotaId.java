package com.gola.entity;
import com.gola.entity.enums.AiJobKind;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;
@Data public class AiQuotaId implements Serializable {
    private UUID userId; private AiJobKind kind; private LocalDate periodStart;
}
