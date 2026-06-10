package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.dto.common.PageResponse;
import com.gola.dto.community.PostResponse;
import com.gola.service.PostService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/hashtags")
@RequiredArgsConstructor
@Tag(name = "Hashtags")
public class HashtagController {
    private final PostService postService;

    @GetMapping("/{tag}/posts")
    public ResponseEntity<ApiResponse<PageResponse<PostResponse>>> getPostsByHashtag(
            @PathVariable String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(postService.getPostsByHashtag(tag, page, size)));
    }
}
