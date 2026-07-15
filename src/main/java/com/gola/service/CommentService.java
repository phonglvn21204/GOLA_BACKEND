package com.gola.service;

import com.gola.dto.community.CommentResponse;
import com.gola.dto.community.PostCommentRequest;
import com.gola.dto.community.ReactionRequest;
import com.gola.entity.Comment;
import com.gola.entity.CommentReaction;
import com.gola.entity.Post;
import com.gola.entity.enums.ReactionKind;
import com.gola.exception.GolaException;
import com.gola.repository.CommentReactionRepository;
import com.gola.repository.CommentRepository;
import com.gola.repository.PostRepository;
import com.gola.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentService {

    private final CommentRepository commentRepo;
    private final PostRepository postRepo;
    private final ProfileRepository profileRepo;
    private final CommentReactionRepository commentReactionRepo;
    private final NotificationService notificationService;

    @Transactional
    public CommentResponse addComment(UUID postId, UUID authorId, PostCommentRequest req) {
        String body = cleanBody(req);
        if (req.getParentId() != null) {
            return addReply(req.getParentId(), authorId, req);
        }
        Post post = visiblePost(postId);

        Comment comment = Comment.builder()
                .postId(post.getId())
                .authorId(authorId)
                .body(body)
                .build();

        Comment saved = commentRepo.save(comment);
        notifyUser(post.getAuthorId(), authorId, "POST_COMMENT", "Bình luận mới", "%s đã bình luận về bài viết của bạn.", "POST", post.getId(), "/community/posts/" + post.getId());
        return toResponse(saved, authorId, false, post.getAuthorId(), Map.of(), Map.of(), Set.of());
    }

    @Transactional
    public CommentResponse addReply(UUID parentCommentId, UUID authorId, PostCommentRequest req) {
        String body = cleanBody(req);
        Comment parent = commentRepo.findById(parentCommentId)
                .orElseThrow(() -> GolaException.notFound("Comment"));
        if (parent.isHidden()) {
            throw GolaException.badRequest("Cannot reply to a hidden comment");
        }

        UUID rootParentId = parent.getParentId() != null ? parent.getParentId() : parent.getId();
        Comment rootParent = commentRepo.findById(rootParentId)
                .orElseThrow(() -> GolaException.notFound("Comment"));
        if (rootParent.isHidden()) {
            throw GolaException.badRequest("Cannot reply to a hidden comment");
        }

        Post post = visiblePost(parent.getPostId());
        Comment reply = Comment.builder()
                .postId(post.getId())
                .authorId(authorId)
                .body(body)
                .parentId(rootParentId)
                .build();

        Comment saved = commentRepo.save(reply);
        notifyUser(parent.getAuthorId(), authorId, "COMMENT_REPLY", "Trả lời bình luận", "%s đã trả lời bình luận của bạn.", "POST", post.getId(), "/community/posts/" + post.getId());
        return toResponse(saved, authorId, false, post.getAuthorId(), Map.of(), Map.of(), Set.of());
    }

    public List<CommentResponse> getCommentsForPost(UUID postId) {
        return getCommentsForPost(postId, null, false);
    }

    public List<CommentResponse> getCommentsForPost(UUID postId, UUID viewerId, boolean isAdmin) {
        Post post = visiblePost(postId);
        List<Comment> comments = commentRepo.findByPostIdAndHiddenFalseOrderByCreatedAtAsc(postId);
        List<UUID> commentIds = comments.stream().map(Comment::getId).toList();
        Map<UUID, Long> likeCounts = commentLikeCounts(commentIds);
        Set<UUID> likedCommentIds = viewerId == null || commentIds.isEmpty()
                ? Set.of()
                : commentReactionRepo.findLikedCommentIds(viewerId, commentIds);
        Map<UUID, List<Comment>> repliesByParent = comments.stream()
                .filter(comment -> comment.getParentId() != null)
                .collect(Collectors.groupingBy(Comment::getParentId));

        return comments.stream()
                .filter(comment -> comment.getParentId() == null)
                .map(comment -> toResponse(comment, viewerId, isAdmin, post.getAuthorId(), repliesByParent, likeCounts, likedCommentIds))
                .collect(Collectors.toList());
    }

    @Transactional
    public CommentResponse updateComment(UUID commentId, UUID userId, PostCommentRequest req) {
        Comment comment = commentRepo.findById(commentId)
                .orElseThrow(() -> GolaException.notFound("Comment"));
        if (comment.isHidden()) {
            throw GolaException.notFound("Comment");
        }
        if (!comment.getAuthorId().equals(userId)) {
            throw GolaException.forbidden();
        }
        comment.setBody(cleanBody(req));
        Post post = visiblePost(comment.getPostId());
        return toResponse(commentRepo.save(comment), userId, false, post.getAuthorId(), Map.of(), singleLikeCount(comment.getId()), currentUserLikedSet(userId, comment.getId()));
    }

    @Transactional
    public void deleteComment(UUID commentId, UUID userId, boolean isAdmin) {
        Comment comment = commentRepo.findById(commentId)
                .orElseThrow(() -> GolaException.notFound("Comment"));
        Post post = postRepo.findById(comment.getPostId())
                .orElseThrow(() -> GolaException.notFound("Post"));
        boolean isCommentAuthor = comment.getAuthorId().equals(userId);
        boolean isPostAuthor = post.getAuthorId().equals(userId);
        if (!isAdmin && !isCommentAuthor && !isPostAuthor) {
            throw GolaException.forbidden();
        }
        comment.setHidden(true);
        commentRepo.save(comment);
    }

    @Transactional
    public CommentResponse setHidden(UUID commentId, boolean hidden) {
        Comment comment = commentRepo.findById(commentId)
                .orElseThrow(() -> GolaException.notFound("Comment"));
        comment.setHidden(hidden);
        Post post = visiblePost(comment.getPostId());
        return toResponse(commentRepo.save(comment), null, false, post.getAuthorId(), Map.of(), singleLikeCount(comment.getId()), Set.of());
    }

    @Transactional
    public CommentResponse toggleReaction(UUID commentId, UUID userId, ReactionRequest req) {
        Comment comment = visibleComment(commentId);
        Post post = visiblePost(comment.getPostId());
        ReactionKind kind = req != null && req.getKind() != null ? req.getKind() : ReactionKind.LIKE;
        var existing = commentReactionRepo.findAllByCommentIdAndUserId(commentId, userId);
        boolean sameReaction = existing.stream().anyMatch(reaction -> reaction.getReactionType() == kind);
        if (sameReaction) {
            commentReactionRepo.deleteAll(existing);
        } else {
            commentReactionRepo.deleteAll(existing);
            commentReactionRepo.flush();
            commentReactionRepo.save(CommentReaction.builder()
                    .commentId(commentId)
                    .userId(userId)
                    .reactionType(kind)
                    .build());
            notifyUser(comment.getAuthorId(), userId, "COMMENT_REACTION", "Bình luận được thích", "%s đã thích bình luận của bạn.", "COMMENT", commentId, "/community/posts/" + post.getId());
        }
        return toResponse(comment, userId, false, post.getAuthorId(), Map.of(), singleLikeCount(commentId), sameReaction ? Set.of() : Set.of(commentId));
    }

    @Transactional
    public CommentResponse removeReaction(UUID commentId, UUID userId) {
        Comment comment = visibleComment(commentId);
        Post post = visiblePost(comment.getPostId());
        commentReactionRepo.deleteByCommentIdAndUserId(commentId, userId);
        return toResponse(comment, userId, false, post.getAuthorId(), Map.of(), singleLikeCount(commentId), Set.of());
    }

    private Comment visibleComment(UUID commentId) {
        Comment comment = commentRepo.findById(commentId)
                .orElseThrow(() -> GolaException.notFound("Comment"));
        if (comment.isHidden()) {
            throw GolaException.notFound("Comment");
        }
        return comment;
    }

    private Post visiblePost(UUID postId) {
        Post post = postRepo.findById(postId)
                .orElseThrow(() -> GolaException.notFound("Post"));
        if (post.isHidden()) {
            throw GolaException.forbidden();
        }
        return post;
    }

    private String cleanBody(PostCommentRequest req) {
        if (req == null || req.getBody() == null || req.getBody().trim().isEmpty()) {
            throw GolaException.badRequest("Comment body cannot be blank");
        }
        return req.getBody().trim();
    }

    private Map<UUID, Long> commentLikeCounts(List<UUID> commentIds) {
        if (commentIds == null || commentIds.isEmpty()) {
            return Map.of();
        }
        return commentReactionRepo.countByCommentIds(commentIds).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
    }

    private Map<UUID, Long> singleLikeCount(UUID commentId) {
        return Map.of(commentId, commentReactionRepo.countByCommentId(commentId));
    }

    private Set<UUID> currentUserLikedSet(UUID userId, UUID commentId) {
        return commentReactionRepo.findByCommentIdAndUserId(commentId, userId)
                .map(reaction -> Set.of(commentId))
                .orElseGet(Collections::emptySet);
    }

    private CommentResponse toResponse(
            Comment c,
            UUID viewerId,
            boolean isAdmin,
            UUID postAuthorId,
            Map<UUID, List<Comment>> repliesByParent,
            Map<UUID, Long> likeCounts,
            Set<UUID> likedCommentIds) {
        var author = profileRepo.findById(c.getAuthorId()).orElse(null);
        List<CommentResponse> replies = repliesByParent.getOrDefault(c.getId(), List.of()).stream()
                .map(reply -> toResponse(reply, viewerId, isAdmin, postAuthorId, Map.of(), likeCounts, likedCommentIds))
                .collect(Collectors.toList());
        boolean liked = likedCommentIds.contains(c.getId());
        boolean canDelete = viewerId != null && (isAdmin || viewerId.equals(c.getAuthorId()) || viewerId.equals(postAuthorId));
        return CommentResponse.builder()
                .id(c.getId())
                .postId(c.getPostId())
                .authorId(c.getAuthorId())
                .authorDisplayName(author != null ? author.getDisplayName() : null)
                .authorAvatarUrl(author != null ? author.getAvatarUrl() : null)
                .body(c.getBody())
                .parentId(c.getParentId())
                .parentCommentId(c.getParentId())
                .replies(replies)
                .likeCount(likeCounts.getOrDefault(c.getId(), 0L))
                .likedByCurrentUser(liked)
                .currentUserReaction(liked ? ReactionKind.LIKE.name() : null)
                .canDelete(canDelete)
                .hidden(c.isHidden())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    private void notifyUser(UUID receiverId, UUID actorId, String notificationType, String title, String bodyTemplate, String targetType, UUID targetId, String targetUrl) {
        if (receiverId == null || receiverId.equals(actorId)) return;
        try {
            String actorName = profileRepo.findById(actorId)
                    .map(profile -> profile.getDisplayName() != null && !profile.getDisplayName().isBlank() ? profile.getDisplayName() : "Một thành viên GOLA")
                    .orElse("Một thành viên GOLA");
            notificationService.notifySocial(
                    receiverId,
                    actorId,
                    notificationType,
                    title,
                    String.format(bodyTemplate, actorName),
                    targetType,
                    targetId,
                    targetUrl
            );
        } catch (Exception e) {
            log.warn("Failed to create comment notification type={} target={}: {}", notificationType, targetId, e.getMessage());
        }
    }
}
