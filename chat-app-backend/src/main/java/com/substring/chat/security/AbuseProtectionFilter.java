package com.substring.chat.security;

import com.substring.chat.config.ChatProperties;
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

@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AbuseProtectionFilter extends OncePerRequestFilter {
    private final ChatProperties properties;
    private final AbuseProtectionService protection;
    private final ClientIpResolver clientIpResolver;

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/rooms");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (!properties.isEnabled()) {
            reject(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Chat is temporarily disabled", null);
            return;
        }
        var clientId = clientIpResolver.resolve(request);
        if (!protection.allowRequest(clientId)) {
            reject(response, 429, "Request rate limit reached; retry shortly", "60");
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
