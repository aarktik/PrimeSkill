package com.example.toolhub.mapper;

import com.example.toolhub.domain.entity.Tag;
import com.example.toolhub.dto.request.TagRequest;
import com.example.toolhub.dto.response.TagResponse;
import org.springframework.stereotype.Component;

@Component
public class TagMapper {

    public TagResponse toResponse(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName(), tag.getSlug());
    }

    public Tag toEntity(TagRequest request) {
        return new Tag(request.name(), request.slug());
    }

    public void updateEntity(Tag tag, TagRequest request) {
        tag.setName(request.name());
        tag.setSlug(request.slug());
    }
}
