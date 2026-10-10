package com.example.toolhub.controller.web;

import java.net.URI;
import java.util.Locale;

/** Only browser pages in this application may be used as an authentication return target. */
public final class ReturnTarget {
    public static final String DEFAULT = "/dashboard/tools";
    private ReturnTarget() {}

    public static String safe(String value) {
        if (value == null || !value.startsWith("/") || value.startsWith("//")
                || value.indexOf('\\') >= 0 || value.chars().anyMatch(c -> c < 32 || c == 127)) return DEFAULT;
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("%0a") || lower.contains("%0d") || lower.contains("%5c")) return DEFAULT;
        try {
            URI uri = URI.create(value);
            if (uri.isAbsolute() || uri.getRawAuthority() != null) return DEFAULT;
            String path = uri.getPath();
            boolean allowed = path.equals("/") || path.equals("/profile") || path.equals("/my/reviews")
                    || path.matches("/tools(?:/[a-z0-9]+(?:-[a-z0-9]+)*(?:/versions)?)?")
                    || path.matches("/dashboard/tools(?:/new|/[0-9]+(?:/edit|/tags|/versions(?:/new|/[0-9]+/edit)?)?)?")
                    || path.matches("/admin/(?:tools|categories|tags)(?:/new|/[0-9]+/edit)?");
            return allowed ? value : DEFAULT;
        } catch (IllegalArgumentException exception) { return DEFAULT; }
    }
}
