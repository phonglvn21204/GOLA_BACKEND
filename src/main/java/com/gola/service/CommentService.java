package com.gola.service;

import com.gola.dto.community.CommentResponse;
import com.gola.dto.community.PostCommentRequest;
import com.gola.entity.Comment;
import com.gola.entity.Post;
import com.gola.exception.GolaException;
import com.gola.repository.CommentRepository;
import com.gola.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepo;
    private final PostRepository postRepo;

    @Transactional
    public CommentResponse addComment(UUID postId, UUID authorId, PostCommentRequest req) {
        Post post = postRepo.findById(postId)
                .orElseThrow(() -> GolaException.notFound("Post"));

        Comment comment = Comment.builder()
                .postId(post.getId())
                .authorId(authorId)
                .body(req.getBody())
                .parentId(req.getParentId())
                .build();

        return toResponse(commentRepo.save(comment));
    }

    public List<CommentResponse> getCommentsForPost(UUID postId) {
        return commentRepo.findByPostIdOrderByCreatedAtAsc(postId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private CommentResponse toResponse(Comment c) {
        return CommentResponse.builder()
                .id(c.getId())
                .postId(c.getPostId())
                .authorId(c.getAuthorId())
                .body(c.getBody())
                .parentId(c.getParentId())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
