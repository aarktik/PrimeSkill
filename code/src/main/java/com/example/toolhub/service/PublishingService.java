package com.example.toolhub.service;

import com.example.toolhub.domain.enums.PublishingAction;
import com.example.toolhub.dto.response.ToolResponse;
import com.example.toolhub.security.CurrentActor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PublishingService {
    ToolResponse transition(Long toolId, PublishingAction action, CurrentActor actor);

    Page<ToolResponse> listPending(Pageable pageable, CurrentActor actor);
}
