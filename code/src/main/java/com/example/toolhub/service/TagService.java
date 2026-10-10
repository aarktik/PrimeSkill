package com.example.toolhub.service;

import com.example.toolhub.dto.request.TagRequest;
import com.example.toolhub.dto.response.TagResponse;
import java.util.List;

public interface TagService {
    List<TagResponse> findAll();

    TagResponse findById(Long id);

    TagResponse create(TagRequest request, boolean actorIsAdmin);

    TagResponse update(Long id, TagRequest request, boolean actorIsAdmin);

    void delete(Long id, boolean actorIsAdmin);

    void assignTag(Long toolId, Long tagId, Long actorUserId, boolean actorIsAdmin);

    void unassignTag(Long toolId, Long tagId, Long actorUserId, boolean actorIsAdmin);

    List<TagResponse> findTagsOfTool(Long toolId, Long actorUserId, boolean actorIsAdmin);
}
