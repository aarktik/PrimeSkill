package com.example.toolhub.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ToolTagId implements Serializable {

    @Column(name = "tool_id")
    private Long toolId;

    @Column(name = "tag_id")
    private Long tagId;

    protected ToolTagId() {
    }

    public ToolTagId(Long toolId, Long tagId) {
        this.toolId = toolId;
        this.tagId = tagId;
    }

    public Long getToolId() {
        return toolId;
    }

    public Long getTagId() {
        return tagId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ToolTagId that)) {
            return false;
        }
        return Objects.equals(toolId, that.toolId) && Objects.equals(tagId, that.tagId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(toolId, tagId);
    }
}
