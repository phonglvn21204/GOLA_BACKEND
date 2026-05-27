package com.gola.service;

import com.gola.dto.community.ReactionRequest;
import com.gola.entity.PostReaction;
import com.gola.exception.GolaException;
import com.gola.repository.PostReactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PostReactionService {

    private final PostReactionRepository reactionRepo;

    @Transactional
    public void reactToPost(UUID postId, UUID userId, ReactionRequest req) {
        PostReaction reaction = reactionRepo.findByPostIdAndUserId(postId, userId)
                .orElseGet(() -> PostReaction.builder()
                        .postId(postId)
                        .userId(userId)
                        .build());
        
        reaction.setKind(req.getKind());
        reactionRepo.save(reaction);
    }

    @Transactional
    public void removeReaction(UUID postId, UUID userId) {
        PostReaction reaction = reactionRepo.findByPostIdAndUserId(postId, userId)
                .orElseThrow(() -> GolaException.notFound("Reaction"));
        reactionRepo.delete(reaction);
    }

    public int getReactionCount(UUID postId) {
        return reactionRepo.countByPostId(postId);
    }
}
