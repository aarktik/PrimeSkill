package com.example.toolhub.service;

import com.example.toolhub.dto.request.ToolVersionRequest;
import com.example.toolhub.dto.response.ToolVersionResponse;
import com.example.toolhub.security.CurrentActor;
import java.util.List;

public interface ToolVersionService {
    List<ToolVersionResponse> list(Long toolId, CurrentActor actor);

    ToolVersionResponse get(Long toolId, Long versionId, CurrentActor actor);

    ToolVersionResponse create(Long toolId, ToolVersionRequest request, CurrentActor actor);

    ToolVersionResponse update(Long toolId, Long versionId, ToolVersionRequest request, CurrentActor actor);

    void delete(Long toolId, Long versionId, CurrentActor actor);
}
