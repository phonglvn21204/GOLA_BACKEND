package com.gola.service;

import com.gola.dto.common.PageResponse;
import com.gola.dto.community.CreatePostRequest;
import com.gola.dto.community.PostResponse;
import com.gola.entity.Hashtag;
import com.gola.entity.Post;
import com.gola.entity.PostHashtag;
import com.gola.entity.PostReaction;
import com.gola.entity.SavedPost;
import com.gola.entity.enums.ReactionKind;
import com.gola.exception.GolaException;
import com.gola.repository.CommentRepository;
import com.gola.repository.HashtagRepository;
import com.gola.repository.PostHashtagRepository;
import com.gola.repository.PostReactionRepository;
import com.gola.repository.PostRepository;
import com.gola.repository.ProfileRepository;
import com.gola.repository.SavedPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {
    private final PostRepository        postRepo;
    private final HashtagRepository     hashtagRepo;
    private final PostHashtagRepository postHashtagRepo;
    private final ProfileRepository     profileRepo;
    private final CommentRepository     commentRepo;
    private final PostReactionRepository reactionRepo;
    private final SavedPostRepository savedPostRepo;
    private final NotificationService notificationService;

    @Transactional
    public PostResponse createPost(UUID userId, CreatePostRequest req) {
        validatePostContent(req);
        var post = Post.builder()
            .authorId(userId)
            .body(clean(req.getBody()))
            .tripId(req.getTripId())
            .mediaUrls(req.getMediaUrls() != null ? req.getMediaUrls() : new String[0])
            .thumbnailUrls(req.getThumbnailUrls() != null ? req.getThumbnailUrls() : new String[0])
            .mediumUrls(req.getMediumUrls() != null ? req.getMediumUrls() : new String[0])
            .isHidden(false)
            .build();
        postRepo.save(post);

        saveHashtags(post.getId(), req.getHashtags());

        log.info("Post created: {} by user: {}", post.getId(), userId);
        return mapToResponse(post, userId);
    }

    public PageResponse<PostResponse> getFeed(int page, int size) {
        return getFeed(null, page, size);
    }

    public PageResponse<PostResponse> getFeed(UUID viewerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return new PageResponse<>(postRepo.findByIsHiddenFalseOrderByCreatedAtDesc(pageable)
            .map(post -> mapToResponse(post, viewerId)));
    }

    public PageResponse<PostResponse> getAllPosts(UUID viewerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return new PageResponse<>(postRepo.findAllByOrderByCreatedAtDesc(pageable)
            .map(post -> mapToResponse(post, viewerId)));
    }

    public PageResponse<PostResponse> getSavedPosts(UUID userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return new PageResponse<>(savedPostRepo.findSavedPosts(userId, pageable)
            .map(post -> mapToResponse(post, userId)));
    }

    public PageResponse<PostResponse> getPostsByHashtag(String tag, int page, int size) {
        return getPostsByHashtag(tag, null, page, size);
    }

    public PageResponse<PostResponse> getPostsByHashtag(String tag, UUID viewerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        String cleanedTag = tag.trim().toLowerCase();
        if (cleanedTag.startsWith("#")) {
            cleanedTag = cleanedTag.substring(1);
        }
        return new PageResponse<>(postRepo.findByHashtag(cleanedTag, pageable)
            .map(post -> mapToResponse(post, viewerId)));
    }

    @Transactional
    public void deletePost(UUID postId, UUID userId) {
        deletePost(postId, userId, false);
    }

    @Transactional
    public void deletePost(UUID postId, UUID userId, boolean isAdmin) {
        var post = postRepo.findById(postId)
            .orElseThrow(() -> GolaException.notFound("Post"));
        if (!isAdmin && !post.getAuthorId().equals(userId)) {
            throw GolaException.forbidden();
        }
        post.setHidden(true);
        postRepo.save(post);
        log.info("Post hidden by delete action: {} by user: {}", postId, userId);
    }

    public PostResponse getPostById(UUID id) {
        return getPostById(id, null, false);
    }

    public PostResponse getPostById(UUID id, UUID viewerId) {
        return getPostById(id, viewerId, false);
    }

    public PostResponse getPostById(UUID id, UUID viewerId, boolean allowHidden) {
        var post = postRepo.findById(id)
            .orElseThrow(() -> GolaException.notFound("Post"));
        if (post.isHidden() && !allowHidden) {
            throw GolaException.forbidden();
        }
        return mapToResponse(post, viewerId);
    }

    @Transactional
    public PostResponse updatePost(UUID postId, UUID userId, CreatePostRequest req) {
        validatePostContent(req);
        var post = postRepo.findById(postId)
            .orElseThrow(() -> GolaException.notFound("Post"));
        if (!post.getAuthorId().equals(userId)) {
            throw GolaException.forbidden();
        }
        post.setBody(clean(req.getBody()));
        post.setMediaUrls(req.getMediaUrls() != null ? req.getMediaUrls() : new String[0]);
        post.setThumbnailUrls(req.getThumbnailUrls() != null ? req.getThumbnailUrls() : new String[0]);
        post.setMediumUrls(req.getMediumUrls() != null ? req.getMediumUrls() : new String[0]);
        post.setTripId(req.getTripId());
        postHashtagRepo.deleteByPostId(postId);
        saveHashtags(postId, req.getHashtags());
        return mapToResponse(postRepo.save(post), userId);
    }

    @Transactional
    public PostResponse setHidden(UUID postId, boolean hidden, UUID adminId) {
        var post = postRepo.findById(postId)
            .orElseThrow(() -> GolaException.notFound("Post"));
        post.setHidden(hidden);
        log.info("Admin {} set post {} hidden={}", adminId, postId, hidden);
        return mapToResponse(postRepo.save(post), adminId);
    }

    @Transactional
    public PostResponse savePost(UUID postId, UUID userId) {
        var post = postRepo.findById(postId)
            .orElseThrow(() -> GolaException.notFound("Post"));
        if (post.isHidden()) {
            throw GolaException.forbidden();
        }
        boolean newlySaved = !savedPostRepo.existsByUserIdAndPostId(userId, postId);
        if (newlySaved) {
            savedPostRepo.save(SavedPost.builder()
                .userId(userId)
                .postId(postId)
                .build());
            notifyPostAuthor(post, userId, "POST_SAVED", "Bài viết được lưu", "%s đã lưu bài viết của bạn.");
        }
        return mapToResponse(post, userId);
    }

    @Transactional
    public PostResponse unsavePost(UUID postId, UUID userId) {
        var post = postRepo.findById(postId)
            .orElseThrow(() -> GolaException.notFound("Post"));
        savedPostRepo.deleteByUserIdAndPostId(userId, postId);
        return mapToResponse(post, userId);
    }

    public PostResponse mapToResponse(Post p) {
        return mapToResponse(p, null);
    }

    public PostResponse mapToResponse(Post p, UUID viewerId) {
        List<String> tags = postHashtagRepo.findByPostId(p.getId()).stream()
            .map(PostHashtag::getTag)
            .collect(Collectors.toList());
        var author = profileRepo.findById(p.getAuthorId()).orElse(null);
        String userReaction = viewerId == null ? null : reactionRepo.findAllByPostIdAndUserId(p.getId(), viewerId)
            .stream()
            .findFirst()
            .map(PostReaction::getKind)
            .map(Enum::name)
            .orElse(null);
        Map<String, Long> reactionCounts = reactionCounts(p.getId());
        long totalReactionCount = reactionCounts.values().stream().mapToLong(Long::longValue).sum();

        return PostResponse.builder()
            .id(p.getId())
            .authorId(p.getAuthorId())
            .authorDisplayName(author != null ? author.getDisplayName() : null)
            .authorAvatarUrl(author != null ? author.getAvatarUrl() : null)
            .body(p.getBody())
            .mediaUrls(p.getMediaUrls())
            .thumbnailUrls(p.getThumbnailUrls())
            .mediumUrls(p.getMediumUrls())
            .tripId(p.getTripId())
            .isHidden(p.isHidden())
            .hashtags(tags)
            .likeCount(totalReactionCount)
            .commentCount(commentRepo.countByPostIdAndHiddenFalse(p.getId()))
            .userReaction(userReaction)
            .currentUserReaction(userReaction)
            .reactionCounts(reactionCounts)
            .totalReactionCount(totalReactionCount)
            .savedByCurrentUser(viewerId != null && savedPostRepo.existsByUserIdAndPostId(viewerId, p.getId()))
            .savedCount(savedPostRepo.countByPostId(p.getId()))
            .createdAt(p.getCreatedAt())
            .updatedAt(p.getUpdatedAt())
            .build();
    }

    private Map<String, Long> reactionCounts(UUID postId) {
        Map<String, Long> counts = reactionRepo.findByPostId(postId).stream()
            .collect(Collectors.groupingBy(reaction -> reaction.getKind().name(), Collectors.counting()));
        Map<String, Long> ordered = new LinkedHashMap<>();
        Arrays.stream(ReactionKind.values())
            .map(ReactionKind::name)
            .filter(counts::containsKey)
            .forEach(kind -> ordered.put(kind, counts.get(kind)));
        return ordered;
    }

    private void saveHashtags(UUID postId, List<String> hashtags) {
        if (hashtags == null || hashtags.isEmpty()) {
            return;
        }
        for (String tag : hashtags) {
            String cleanedTag = cleanTag(tag);
            if (cleanedTag.isEmpty()) continue;

            if (!hashtagRepo.existsById(cleanedTag)) {
                hashtagRepo.save(Hashtag.builder()
                    .tag(cleanedTag)
                    .postCount(0)
                    .lastUsedAt(Instant.now())
                    .build());
            }

            postHashtagRepo.save(PostHashtag.builder()
                .postId(postId)
                .tag(cleanedTag)
                .build());
        }
    }

    private String cleanTag(String tag) {
        if (tag == null) return "";
        String cleanedTag = tag.trim().toLowerCase();
        if (cleanedTag.startsWith("#")) {
            cleanedTag = cleanedTag.substring(1);
        }
        return cleanedTag;
    }

    private void validatePostContent(CreatePostRequest req) {
        boolean hasBody = req != null && clean(req.getBody()) != null && !clean(req.getBody()).isBlank();
        boolean hasMedia = req != null && req.getMediaUrls() != null && req.getMediaUrls().length > 0;
        if (!hasBody && !hasMedia) {
            throw GolaException.badRequest("Post must include text or media");
        }
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
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
            log.warn("Failed to create post notification post={} type={}: {}", post.getId(), notificationType, e.getMessage());
        }
    }
}
