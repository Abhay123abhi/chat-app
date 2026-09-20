package com.substring.chat.security;

import com.substring.chat.config.ChatProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AbuseProtectionFilter extends OncePerRequestFilter {
    private final ChatProperties properties;
    private final AbuseProtectionService protection;
    private final ClientIpResolver clientIpResolver;

    public AbuseProtectionFilter(ChatProperties properties, AbuseProtectionService protection,
            ClientIpResolver clientIpResolver) {
        this.properties = properties;
        this.protection = protection;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/rooms");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (!properties.isEnabled()) {
            reject(response, 503, "Chat is temporarily disabled", null);
            return;
        }
        String clientId = clientIpResolver.resolve(request);
        if (!protection.allowGlobalRequest(clientId)) {
            reject(response, 429, "Too many requests; retry shortly", "60");
            return;
        }
        chain.doFilter(request, response);
    }

    private static void reject(HttpServletResponse response, int status, String message, String retryAfter)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (retryAfter != null) response.setHeader("Retry-After", retryAfter);
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
