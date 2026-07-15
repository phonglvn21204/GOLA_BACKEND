package com.gola.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import com.gola.repository.ProfileRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final ProfileRepository profileRepo;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String token = extractBearer(req);
        if (token != null) {
            if (!jwtService.isValid(token)) {
                SecurityContextHolder.clearContext();
                log.debug("Rejected invalid or expired JWT for {} {}", req.getMethod(), req.getRequestURI());
                writeUnauthorized(res, "Session expired. Please login again.");
                return;
            }
            var userId = jwtService.extractUserId(token);
            var profile = profileRepo.findActiveById(userId);
            if (profile.isEmpty() || profile.get().isBlocked()) {
                SecurityContextHolder.clearContext();
                writeForbidden(res, "Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên.");
                return;
            }
            var role   = jwtService.extractRole(token);
            var auth   = new UsernamePasswordAuthenticationToken(
                userId, null,
                role != null ? List.of(new SimpleGrantedAuthority("ROLE_" + role)) : List.of()
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(req, res);
    }

    private String extractBearer(HttpServletRequest req) {
        String header = req.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private void writeUnauthorized(HttpServletResponse res, String message) throws IOException {
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.getWriter().write("{\"success\":false,\"message\":\"" + message + "\",\"error\":\"UNAUTHORIZED\"}");
    }

    private void writeForbidden(HttpServletResponse res, String message) throws IOException {
        res.setStatus(HttpServletResponse.SC_FORBIDDEN);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.getWriter().write("{\"success\":false,\"message\":\"" + message + "\",\"error\":\"FORBIDDEN\"}");
    }
}
