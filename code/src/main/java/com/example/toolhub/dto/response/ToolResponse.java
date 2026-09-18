package com.example.toolhub.dto.response;

import com.example.toolhub.domain.enums.ToolStatus;
import java.time.Instant;

public final class ToolResponse {
    private final Long id;
    private final String name;
    private final String slug;
    private final String shortDescription;
    private final String description;
    private final Long categoryId;
    private final String categoryName;
    private final String categorySlug;
    private final Long ownerId;
    private final String repositoryUrl;
    private final ToolStatus status;
    private final long viewCount;
    private final Instant createdAt;
    private final Instant updatedAt;

    private ToolResponse(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.slug = builder.slug;
        this.shortDescription = builder.shortDescription;
        this.description = builder.description;
        this.categoryId = builder.categoryId;
        this.categoryName = builder.categoryName;
        this.categorySlug = builder.categorySlug;
        this.ownerId = builder.ownerId;
        this.repositoryUrl = builder.repositoryUrl;
        this.status = builder.status;
        this.viewCount = builder.viewCount;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    public static Builder builder() { return new Builder(); }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getShortDescription() { return shortDescription; }
    public String getDescription() { return description; }
    public Long getCategoryId() { return categoryId; }
    public String getCategoryName() { return categoryName; }
    public String getCategorySlug() { return categorySlug; }
    public Long getOwnerId() { return ownerId; }
    public String getRepositoryUrl() { return repositoryUrl; }
    public ToolStatus getStatus() { return status; }
    public long getViewCount() { return viewCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public static final class Builder {
        private Long id;
        private String name;
        private String slug;
        private String shortDescription;
        private String description;
        private Long categoryId;
        private String categoryName;
        private String categorySlug;
        private Long ownerId;
        private String repositoryUrl;
        private ToolStatus status;
        private long viewCount;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(Long value) { id = value; return this; }
        public Builder name(String value) { name = value; return this; }
        public Builder slug(String value) { slug = value; return this; }
        public Builder shortDescription(String value) { shortDescription = value; return this; }
        public Builder description(String value) { description = value; return this; }
        public Builder categoryId(Long value) { categoryId = value; return this; }
        public Builder categoryName(String value) { categoryName = value; return this; }
        public Builder categorySlug(String value) { categorySlug = value; return this; }
        public Builder ownerId(Long value) { ownerId = value; return this; }
        public Builder repositoryUrl(String value) { repositoryUrl = value; return this; }
        public Builder status(ToolStatus value) { status = value; return this; }
        public Builder viewCount(long value) { viewCount = value; return this; }
        public Builder createdAt(Instant value) { createdAt = value; return this; }
        public Builder updatedAt(Instant value) { updatedAt = value; return this; }
        public ToolResponse build() { return new ToolResponse(this); }
    }
}
