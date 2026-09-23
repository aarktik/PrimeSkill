package com.example.toolhub.service;

import com.example.toolhub.dto.request.CreateToolRequest;
import com.example.toolhub.dto.request.UpdateToolRequest;
import com.example.toolhub.dto.response.ToolResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ToolService {
    ToolResponse create(CreateToolRequest request, Long actorUserId);
    ToolResponse getByIdOrSlug(String idOrSlug, Long actorUserId, boolean actorIsAdmin);
    Page<ToolResponse> listOwnedBy(Long actorUserId, Pageable pageable);
    ToolResponse update(Long id, UpdateToolRequest request, Long actorUserId, boolean actorIsAdmin);
    void delete(Long id, Long actorUserId, boolean actorIsAdmin);
}
