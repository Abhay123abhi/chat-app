package com.substring.chat.security;

import com.substring.chat.config.ChatProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetSocketAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClientIpResolver {
    private final ChatProperties properties;

    public String resolve(HttpServletRequest request) {
        var forwarded = trustedHeader(request.getHeader(properties.getTrustedClientIpHeader()));
        return forwarded != null ? forwarded : safe(request.getRemoteAddr());
    }

    public String resolve(ServerHttpRequest request) {
        var forwarded = trustedHeader(request.getHeaders().getFirst(properties.getTrustedClientIpHeader()));
        if (forwarded != null) return forwarded;

        InetSocketAddress remote = request.getRemoteAddress();
        if (remote == null) return "unknown";
        return safe(remote.getAddress() == null
                ? remote.getHostString()
                : remote.getAddress().getHostAddress());
    }

    private static String trustedHeader(String value) {
        if (value == null || value.isBlank()) return null;
        var candidate = value.split(",", 2)[0].trim();
        return candidate.isBlank() ? null : safe(candidate);
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) return "unknown";
        var trimmed = value.trim();
        return trimmed.substring(0, Math.min(trimmed.length(), 128));
    }
}
