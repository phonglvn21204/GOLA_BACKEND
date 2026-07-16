package com.gola.controller;

import com.gola.config.GolaProperties;
import com.gola.service.PublicShareService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping(value = "/share", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
@RequiredArgsConstructor
@Slf4j
public class PublicShareController {
    private final PublicShareService publicShareService;
    private final GolaProperties properties;

    @GetMapping("/trip/{tripId}")
    public ResponseEntity<String> shareTrip(
            @PathVariable UUID tripId,
            @RequestParam(required = false) String token,
            HttpServletRequest request) {
        return html(publicShareService.tripPreview(tripId, token, canonicalUrl(request)), request);
    }

    @GetMapping("/memory/{memoryId}")
    public ResponseEntity<String> shareMemory(@PathVariable UUID memoryId, HttpServletRequest request) {
        return html(publicShareService.memoryPreview(memoryId, canonicalUrl(request)), request);
    }

    @GetMapping("/post/{postId}")
    public ResponseEntity<String> sharePost(@PathVariable UUID postId, HttpServletRequest request) {
        return html(publicShareService.postPreview(postId, canonicalUrl(request)), request);
    }

    private ResponseEntity<String> html(PublicShareService.SharePreview preview, HttpServletRequest request) {
        log.info("Public share OG page path={} title='{}' hasOgImage={} contentType={}",
            request.getRequestURI(), preview.title(), hasText(preview.imageUrl()), "text/html;charset=UTF-8");
        return ResponseEntity.ok()
            .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
            .body(render(preview));
    }

    private String canonicalUrl(HttpServletRequest request) {
        String query = request.getQueryString();
        String base = trimTrailingSlash(hasText(properties.getPublicUrl()) ? properties.getPublicUrl() : "http://localhost:8080");
        return base + request.getRequestURI() + (query == null || query.isBlank() ? "" : "?" + query);
    }

    private String render(PublicShareService.SharePreview preview) {
        String imageMeta = hasText(preview.imageUrl())
            ? """
              <meta property="og:image" content="%s">
              <meta name="twitter:image" content="%s">
              """.formatted(escape(preview.imageUrl()), escape(preview.imageUrl()))
            : "";
        return """
            <!doctype html>
            <html lang="vi">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>%s</title>
              <meta name="description" content="%s">
              <meta property="og:type" content="website">
              <meta property="og:title" content="%s">
              <meta property="og:description" content="%s">
              <meta property="og:url" content="%s">
              %s
              <meta name="twitter:card" content="summary_large_image">
              <meta name="twitter:title" content="%s">
              <meta name="twitter:description" content="%s">
              <style>
                body { margin: 0; min-height: 100vh; display: grid; place-items: center; font-family: Inter, system-ui, sans-serif; background: #071513; color: #f8fafc; }
                main { width: min(92vw, 720px); padding: 32px; border: 1px solid rgba(255,255,255,.12); border-radius: 28px; background: linear-gradient(145deg, rgba(20,184,166,.16), rgba(15,23,42,.92)); box-shadow: 0 28px 80px rgba(0,0,0,.32); }
                img { width: 100%%; max-height: 360px; object-fit: cover; border-radius: 22px; margin-bottom: 24px; background: rgba(255,255,255,.08); }
                .brand { color: #2dd4bf; font-weight: 800; letter-spacing: .14em; font-size: 12px; text-transform: uppercase; }
                h1 { margin: 10px 0 12px; font-size: clamp(30px, 6vw, 52px); line-height: 1.02; }
                p { color: rgba(248,250,252,.78); line-height: 1.7; }
                a { display: inline-flex; margin-top: 18px; padding: 13px 18px; border-radius: 999px; background: #14b8a6; color: #05201c; text-decoration: none; font-weight: 800; }
              </style>
            </head>
            <body>
              <main>
                %s
                <div class="brand">GOLA Travel Memory</div>
                <h1>%s</h1>
                <p>%s</p>
                <a href="%s">Mở trong GOLA</a>
              </main>
            </body>
            </html>
            """.formatted(
                escape(preview.title()),
                escape(preview.description()),
                escape(preview.title()),
                escape(preview.description()),
                escape(preview.canonicalUrl()),
                imageMeta,
                escape(preview.title()),
                escape(preview.description()),
                hasText(preview.imageUrl()) ? "<img src=\"" + escape(preview.imageUrl()) + "\" alt=\"\">" : "",
                escape(preview.title()),
                escape(preview.description()),
                escape(preview.appUrl())
            );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String escape(String value) {
        if (value == null) return "";
        return value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }
}
