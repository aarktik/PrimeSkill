package com.example.toolhub.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "tool_versions", uniqueConstraints =
        @UniqueConstraint(name = "uq_tool_versions_tool_version", columnNames = {"tool_id", "version"}))
public class ToolVersion extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tool_id", nullable = false)
    private Tool tool;

    @Column(nullable = false, length = 100)
    private String version;

    @Column(name = "release_notes", columnDefinition = "TEXT")
    private String releaseNotes;

    @Column(name = "released_at")
    private Instant releasedAt;

    protected ToolVersion() {
    }

    public ToolVersion(Tool tool, String version, String releaseNotes) {
        this.tool = tool;
        this.version = version;
        this.releaseNotes = releaseNotes;
    }

    public Long getId() { return id; }
    public Tool getTool() { return tool; }
    public String getVersion() { return version; }
    public String getReleaseNotes() { return releaseNotes; }
    public Instant getReleasedAt() { return releasedAt; }

    public void update(String version, String releaseNotes) {
        this.version = version;
        this.releaseNotes = releaseNotes;
    }
}
