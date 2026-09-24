package com.example.toolhub.service.search;

public enum ToolSortOption {
    NEWEST("newest"),
    POPULAR("popular"),
    RATING("rating"),
    RELEVANCE("relevance");

    private final String value;

    ToolSortOption(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ToolSortOption from(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase();
        for (ToolSortOption option : values()) {
            if (option.value.equals(normalized)) {
                return option;
            }
        }
        throw new IllegalArgumentException("Unsupported sort: " + raw);
    }
}
