package com.example.toolhub.service.publishing;

import com.example.toolhub.domain.enums.PublishingAction;
import com.example.toolhub.domain.enums.ToolStatus;

public interface PublishingState {
    ToolStatus status();

    ToolStatus transition(PublishingAction action);
}
