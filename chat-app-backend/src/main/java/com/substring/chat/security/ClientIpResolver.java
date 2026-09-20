package com.substring.chat.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetSocketAddress;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {
    public String resolve(HttpServletRequest request) {
        String value = fromHeaders(name -> request.getHeader(name));
        return value != null ? value : safe(request.getRemoteAddr());
    }

    public String resolve(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();
        String value = fromHeaders(name -> headers.getFirst(name));
        if (value != null) return value;
        InetSocketAddress remote = request.getRemoteAddress();
        return remote == null ? "unknown" : safe(remote.getAddress() == null
                ? remote.getHostString() : remote.getAddress().getHostAddress());
    }

    private String fromHeaders(HeaderLookup lookup) {
        String cloudflare = clean(lookup.get("CF-Connecting-IP"));
        if (cloudflare != null) return cloudflare;

        String forwarded = clean(lookup.get("X-Forwarded-For"));
        if (forwarded != null) {
            List<String> parts = List.of(forwarded.split(","));
            for (int i = parts.size() - 1; i >= 0; i--) {
                String candidate = clean(parts.get(i));
                if (candidate != null) return candidate;
            }
        }

        String realIp = clean(lookup.get("X-Real-IP"));
        return realIp;
    }

    private static String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned.substring(0, Math.min(cleaned.length(), 128));
    }

    private static String safe(String value) {
        String cleaned = clean(value);
        return cleaned == null ? "unknown" : cleaned;
    }

    @FunctionalInterface
    private interface HeaderLookup {
        String get(String name);
    }
}
