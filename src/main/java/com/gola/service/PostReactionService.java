package com.gola.service;

import com.gola.dto.community.ReactionRequest;
import com.gola.entity.Post;
import com.gola.entity.PostReaction;
import com.gola.entity.enums.ReactionKind;
import com.gola.exception.GolaException;
import com.gola.repository.PostReactionRepository;
import com.gola.repository.PostRepository;
import com.gola.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostReactionService {

    private final PostReactionRepository reactionRepo;
    private final PostRepository postRepo;
    private final ProfileRepository profileRepo;
    private final NotificationService notificationService;

    @Transactional
    public void reactToPost(UUID postId, UUID userId, ReactionRequest req) {
        Post post = visiblePost(postId);
        ReactionKind kind = req != null && req.getKind() != null ? req.getKind() : ReactionKind.LIKE;
        reactionRepo.deleteAll(reactionRepo.findAllByPostIdAndUserId(postId, userId));
        reactionRepo.flush();
        reactionRepo.save(PostReaction.builder()
                .postId(postId)
                .userId(userId)
                .kind(kind)
                .build());
        notifyPostAuthor(post, userId, "POST_REACTION", "Có tương tác mới", "%s đã bày tỏ cảm xúc với bài viết của bạn.");
    }

    @Transactional
    public void removeReaction(UUID postId, UUID userId) {
        var reactions = reactionRepo.findAllByPostIdAndUserId(postId, userId);
        if (reactions.isEmpty()) {
            throw GolaException.notFound("Reaction");
        }
        reactions.forEach(reactionRepo::delete);
    }

    @Transactional
    public boolean toggleReaction(UUID postId, UUID userId, ReactionRequest req) {
        Post post = visiblePost(postId);
        ReactionKind kind = req != null && req.getKind() != null ? req.getKind() : ReactionKind.LIKE;
        var existing = reactionRepo.findAllByPostIdAndUserId(postId, userId);
        boolean sameReaction = existing.stream().anyMatch(reaction -> reaction.getKind() == kind);
        if (sameReaction) {
            reactionRepo.deleteAll(existing);
            return false;
        }
        reactionRepo.deleteAll(existing);
        reactionRepo.flush();
        reactionRepo.save(PostReaction.builder()
                .postId(postId)
                .userId(userId)
                .kind(kind)
                .build());
        notifyPostAuthor(post, userId, "POST_REACTION", "Có tương tác mới", "%s đã bày tỏ cảm xúc với bài viết của bạn.");
        return true;
    }

    public int getReactionCount(UUID postId) {
        return reactionRepo.countByPostId(postId);
    }

    private Post visiblePost(UUID postId) {
        Post post = postRepo.findById(postId)
                .orElseThrow(() -> GolaException.notFound("Post"));
        if (post.isHidden()) {
            throw GolaException.forbidden();
        }
        return post;
    }

    private void notifyPostAuthor(Post post, UUID actorId, String notificationType, String title, String bodyTemplate) {
        if (post == null || post.getAuthorId() == null || post.getAuthorId().equals(actorId)) return;
        try {
            String actorName = profileRepo.findById(actorId)
                    .map(profile -> profile.getDisplayName() != null && !profile.getDisplayName().isBlank() ? profile.getDisplayName() : "Một thành viên GOLA")
                    .orElse("Một thành viên GOLA");
            notificationService.notifySocial(
                    post.getAuthorId(),
                    actorId,
                    notificationType,
                    title,
                    String.format(bodyTemplate, actorName),
                    "POST",
                    post.getId(),
                    "/community/posts/" + post.getId()
            );
        } catch (Exception e) {
            log.warn("Failed to create post reaction notification post={}: {}", post.getId(), e.getMessage());
        }
    }
}
