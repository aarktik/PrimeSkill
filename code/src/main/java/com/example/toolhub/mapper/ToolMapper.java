package com.example.toolhub.mapper;

import com.example.toolhub.domain.entity.Tool;
import com.example.toolhub.dto.response.ToolResponse;
import org.springframework.stereotype.Component;

@Component
public class ToolMapper {

    public ToolResponse toResponse(Tool tool) {
        var category = tool.getCategory();
        return ToolResponse.builder()
                .id(tool.getId())
                .name(tool.getName())
                .slug(tool.getSlug())
                .shortDescription(tool.getShortDescription())
                .description(tool.getDescription())
                .categoryId(category.getId())
                .categoryName(category.getName())
                .categorySlug(category.getSlug())
                .ownerId(tool.getOwnerId())
                .repositoryUrl(tool.getRepositoryUrl())
                .status(tool.getStatus())
                .viewCount(tool.getViewCount())
                .createdAt(tool.getCreatedAt())
                .updatedAt(tool.getUpdatedAt())
                .build();
    }
}
