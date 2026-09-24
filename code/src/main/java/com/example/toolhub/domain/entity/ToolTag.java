package com.example.toolhub.domain.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "tool_tags")
public class ToolTag {

    @EmbeddedId
    private ToolTagId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("toolId")
    @JoinColumn(name = "tool_id", nullable = false)
    private Tool tool;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("tagId")
    @JoinColumn(name = "tag_id", nullable = false)
    private Tag tag;

    protected ToolTag() {
    }

    public ToolTag(Tool tool, Tag tag) {
        this.tool = tool;
        this.tag = tag;
        this.id = new ToolTagId(tool.getId(), tag.getId());
    }

    public ToolTagId getId() {
        return id;
    }

    public Tool getTool() {
        return tool;
    }

    public Tag getTag() {
        return tag;
    }
}
