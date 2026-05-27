package com.gola.service;

import com.gola.dto.common.PageResponse;
import com.gola.dto.community.CreatePostRequest;
import com.gola.dto.community.PostResponse;
import com.gola.entity.Hashtag;
import com.gola.entity.Post;
import com.gola.entity.PostHashtag;
import com.gola.exception.GolaException;
import com.gola.repository.HashtagRepository;
import com.gola.repository.PostHashtagRepository;
import com.gola.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {
    private final PostRepository        postRepo;
    private final HashtagRepository     hashtagRepo;
    private final PostHashtagRepository postHashtagRepo;

    @Transactional
    public PostResponse createPost(UUID userId, CreatePostRequest req) {
        var post = Post.builder()
            .authorId(userId)
            .body(req.getBody())
            .tripId(req.getTripId())
            .mediaUrls(req.getMediaUrls() != null ? req.getMediaUrls() : new String[0])
            .isHidden(false)
            .build();
        postRepo.save(post);

        if (req.getHashtags() != null && !req.getHashtags().isEmpty()) {
            for (String tag : req.getHashtags()) {
                String cleanedTag = tag.trim().toLowerCase();
                if (cleanedTag.startsWith("#")) {
                    cleanedTag = cleanedTag.substring(1);
                }
                if (cleanedTag.isEmpty()) continue;

                // Ensure parent hashtag entry exists for the DB foreign key constraint
                if (!hashtagRepo.existsById(cleanedTag)) {
                    hashtagRepo.save(Hashtag.builder()
                        .tag(cleanedTag)
                        .postCount(0)
                        .lastUsedAt(Instant.now())
                        .build());
                }

                postHashtagRepo.save(PostHashtag.builder()
                    .postId(post.getId())
                    .tag(cleanedTag)
                    .build());
            }
        }

        log.info("Post created: {} by user: {}", post.getId(), userId);
        return mapToResponse(post);
    }

    public PageResponse<PostResponse> getFeed(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return new PageResponse<>(postRepo.findByIsHiddenFalseOrderByCreatedAtDesc(pageable).map(this::mapToResponse));
    }

    public PageResponse<PostResponse> getPostsByHashtag(String tag, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        String cleanedTag = tag.trim().toLowerCase();
        if (cleanedTag.startsWith("#")) {
            cleanedTag = cleanedTag.substring(1);
        }
        return new PageResponse<>(postRepo.findByHashtag(cleanedTag, pageable).map(this::mapToResponse));
    }

    @Transactional
    public void deletePost(UUID postId, UUID userId) {
        var post = postRepo.findById(postId)
            .orElseThrow(() -> GolaException.notFound("Post"));
        if (!post.getAuthorId().equals(userId)) {
            throw GolaException.forbidden();
        }
        postRepo.delete(post);
        log.info("Post deleted: {} by user: {}", postId, userId);
    }

    public PostResponse getPostById(UUID id) {
        var post = postRepo.findById(id)
            .orElseThrow(() -> GolaException.notFound("Post"));
        if (post.isHidden()) {
            throw GolaException.forbidden();
        }
        return mapToResponse(post);
    }

    private PostResponse mapToResponse(Post p) {
        List<String> tags = postHashtagRepo.findByPostId(p.getId()).stream()
            .map(PostHashtag::getTag)
            .collect(Collectors.toList());
        return PostResponse.builder()
            .id(p.getId())
            .authorId(p.getAuthorId())
            .body(p.getBody())
            .mediaUrls(p.getMediaUrls())
            .tripId(p.getTripId())
            .isHidden(p.isHidden())
            .hashtags(tags)
            .createdAt(p.getCreatedAt())
            .updatedAt(p.getUpdatedAt())
            .build();
    }
}
