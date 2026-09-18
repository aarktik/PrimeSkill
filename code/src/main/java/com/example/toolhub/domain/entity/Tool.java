package com.example.toolhub.domain.entity;

import com.example.toolhub.domain.enums.ToolStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "tools")
public class Tool extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 170, unique = true)
    private String slug;

    @Column(name = "short_description", nullable = false, length = 300)
    private String shortDescription;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "repository_url", length = 500)
    private String repositoryUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ToolStatus status = ToolStatus.DRAFT;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    protected Tool() {
    }

    public Tool(Long ownerId, Category category, String name, String slug,
                String shortDescription, String description, String repositoryUrl) {
        this.ownerId = ownerId;
        this.category = category;
        this.name = name;
        this.slug = slug;
        this.shortDescription = shortDescription;
        this.description = description;
        this.repositoryUrl = repositoryUrl;
        this.status = ToolStatus.DRAFT;
        this.viewCount = 0L;
    }

    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public Category getCategory() { return category; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getShortDescription() { return shortDescription; }
    public String getDescription() { return description; }
    public String getRepositoryUrl() { return repositoryUrl; }
    public ToolStatus getStatus() { return status; }
    public long getViewCount() { return viewCount; }

    public void updateCategory(Category category) { this.category = category; }
    public void updateDetails(String name, String slug, String shortDescription,
                              String description, String repositoryUrl) {
        this.name = name;
        this.slug = slug;
        this.shortDescription = shortDescription;
        this.description = description;
        this.repositoryUrl = repositoryUrl;
    }
}
