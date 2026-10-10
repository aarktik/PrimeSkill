package com.example.toolhub.service.impl;

import com.example.toolhub.domain.entity.Tool;
import com.example.toolhub.domain.enums.PublishingAction;
import com.example.toolhub.domain.enums.ToolStatus;
import com.example.toolhub.dto.response.ToolResponse;
import com.example.toolhub.exception.AuthenticationRequiredException;
import com.example.toolhub.exception.ResourceNotFoundException;
import com.example.toolhub.mapper.ToolMapper;
import com.example.toolhub.repository.ToolRepository;
import com.example.toolhub.security.CurrentActor;
import com.example.toolhub.service.PublishingService;
import com.example.toolhub.service.publishing.PublishingStateMachine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import jakarta.persistence.EntityManager;
import com.example.toolhub.exception.StaleReviewRevisionException;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublishingServiceImpl implements PublishingService {
    private final ToolRepository toolRepository;
    private final ToolMapper toolMapper;
    private final PublishingStateMachine stateMachine;
    private final EntityManager entityManager;

    public PublishingServiceImpl(ToolRepository toolRepository, ToolMapper toolMapper,
                                 PublishingStateMachine stateMachine, EntityManager entityManager) {
        this.toolRepository = toolRepository;
        this.toolMapper = toolMapper;
        this.stateMachine = stateMachine;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public ToolResponse transition(Long toolId, PublishingAction action, CurrentActor actor) {
        requireActor(actor);
        if (action == null || action == PublishingAction.APPROVE || action == PublishingAction.REJECT) {
            throw new IllegalArgumentException("Review decisions require an expected submission revision");
        }
        Tool tool = lockedTool(toolId);
        boolean owner = actor.id().equals(tool.getOwnerId());
        boolean allowed = switch (action) {
            case SUBMIT, RESTORE -> owner;
            case APPROVE, REJECT -> actor.admin();
            case DEPRECATE -> owner || actor.admin();
        };
        if (!allowed) {
            throw new AccessDeniedException("You cannot perform this publishing action");
        }
        ToolStatus next = stateMachine.transition(tool.getStatus(), action);
        if (action == PublishingAction.SUBMIT) tool.advanceReviewRevision();
        tool.changeStatus(next);
        return toolMapper.toResponse(tool);
    }

    @Override
    @Transactional
    public ToolResponse decide(Long toolId, PublishingAction action, long expectedReviewRevision, CurrentActor actor) {
        requireActor(actor);
        if (!actor.admin()) throw new AccessDeniedException("Admin role is required");
        if (action != PublishingAction.APPROVE && action != PublishingAction.REJECT) {
            throw new IllegalArgumentException("Only approve and reject are review decisions");
        }
        if (expectedReviewRevision < 0) throw new IllegalArgumentException("Submission revision must be non-negative");
        Tool tool = lockedTool(toolId);
        if (tool.getReviewRevision() != expectedReviewRevision) throw new StaleReviewRevisionException();
        tool.changeStatus(stateMachine.transition(tool.getStatus(), action));
        return toolMapper.toResponse(tool);
    }

    private Tool lockedTool(Long toolId) {
        Tool tool = toolRepository.findForUpdateById(toolId)
                .orElseThrow(() -> new ResourceNotFoundException("Tool not found: " + toolId));
        entityManager.refresh(tool);
        return tool;
    }

    private void requireActor(CurrentActor actor) {
        if (actor == null || actor.id() == null) throw new AuthenticationRequiredException();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ToolResponse> listPending(Pageable pageable, CurrentActor actor) {
        if (actor == null || actor.id() == null) {
            throw new AuthenticationRequiredException();
        }
        if (!actor.admin()) {
            throw new AccessDeniedException("Admin role is required");
        }
        return toolRepository.findByStatus(ToolStatus.PENDING, pageable).map(toolMapper::toResponse);
    }
}
