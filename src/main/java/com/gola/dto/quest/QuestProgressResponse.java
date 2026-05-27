package com.gola.dto.quest;

import com.gola.entity.enums.QuestProgressStatus;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data @Builder
public class QuestProgressResponse {
    private UUID questId;
    private UUID userId;
    private int taskIdx;
    private QuestProgressStatus status;
}
