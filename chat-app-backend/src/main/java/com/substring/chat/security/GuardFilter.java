package com.substring.chat.security;

import com.substring.chat.config.GuardProperties;
import com.substring.chat.security.DemoGuard.Action;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Reject abusive REST traffic before JSON parsing, database work or room creation. */
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class GuardFilter extends OncePerRequestFilter {
    private static final long MAX_BODY_BYTES = 16 * 1024L;
    private final GuardProperties properties;
    private final DemoGuard limits;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        var path = request.getRequestURI();
        return !path.equals("/api/v1/rooms") && !path.startsWith("/api/v1/rooms/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (!properties.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }
        if (!properties.isChatEnabled()) {
            reject(response, 503, "Public chat is temporarily unavailable", "60");
            return;
        }
        var method = request.getMethod();
        var path = request.getRequestURI();
        var creating = "POST".equals(method) && path.equals("/api/v1/rooms");
        if (creating && !properties.isRoomCreationEnabled()) {
            reject(response, 503, "Room creation is temporarily disabled", "60");
            return;
        }
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            reject(response, 413, "Request body is too large", null);
            return;
        }
        var action = creating ? Action.CREATE_ROOM
                : "POST".equals(method) && path.matches("/api/v1/rooms/[^/]+/messages")
                ? Action.SEND_MESSAGE : Action.READ;

        // Remote address cannot be forged by putting arbitrary X-Forwarded-For in a request.
        // Behind a managed reverse proxy, this may identify the proxy: global caps still apply.
        var ip = request.getRemoteAddr();
        if (ip == null || ip.isBlank()) ip = "unknown";
        if (!limits.allowRequest(ip, action)) {
            reject(response, 429, "Public demo rate limit exceeded", creating ? "3600" : "60");
            return;
        }
        chain.doFilter(request, response);
    }

    private static void reject(HttpServletResponse response, int status, String message, String retryAfter)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        if (retryAfter != null) response.setHeader("Retry-After", retryAfter);
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
