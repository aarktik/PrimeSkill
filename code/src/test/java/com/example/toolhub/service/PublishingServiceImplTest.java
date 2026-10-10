package com.example.toolhub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.toolhub.domain.entity.Category;
import com.example.toolhub.domain.entity.Tool;
import com.example.toolhub.domain.enums.PublishingAction;
import com.example.toolhub.domain.enums.ToolStatus;
import com.example.toolhub.exception.InvalidStateTransitionException;
import com.example.toolhub.mapper.ToolMapper;
import com.example.toolhub.repository.ToolRepository;
import com.example.toolhub.security.CurrentActor;
import com.example.toolhub.service.impl.PublishingServiceImpl;
import com.example.toolhub.service.publishing.PublishingStateMachine;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class PublishingServiceImplTest {
    @Mock private ToolRepository toolRepository;
    @Mock private jakarta.persistence.EntityManager entityManager;
    @Mock private ToolMapper toolMapper;
    private PublishingServiceImpl service;
    private Tool tool;

    @BeforeEach
    void setUp() {
        service = new PublishingServiceImpl(toolRepository, toolMapper, new PublishingStateMachine(), entityManager);
        tool = new Tool(7L, new Category("Automation", "automation", null),
                "Tool", "tool", "Short", "Description", null);
    }

    @Test
    void ownerCanSubmitAndAdminCanApprove() {
        when(toolRepository.findForUpdateById(1L)).thenReturn(Optional.of(tool));

        service.transition(1L, PublishingAction.SUBMIT, new CurrentActor(7L, false));
        assertEquals(ToolStatus.PENDING, tool.getStatus());

        service.decide(1L, PublishingAction.APPROVE, 1L, new CurrentActor(8L, true));
        assertEquals(ToolStatus.PUBLISHED, tool.getStatus());
        verify(toolMapper, org.mockito.Mockito.times(2)).toResponse(tool);
    }

    @Test
    void nonOwnerCannotSubmit() {
        when(toolRepository.findForUpdateById(1L)).thenReturn(Optional.of(tool));

        assertThrows(AccessDeniedException.class,
                () -> service.transition(1L, PublishingAction.SUBMIT, new CurrentActor(8L, false)));
        assertEquals(ToolStatus.DRAFT, tool.getStatus());
    }

    @Test
    void ownerWithoutAdminRoleCannotApprove() {

        assertThrows(AccessDeniedException.class,
                () -> service.decide(1L, PublishingAction.APPROVE, 0L, new CurrentActor(7L, false)));
        assertEquals(ToolStatus.DRAFT, tool.getStatus());
    }

    @Test
    void invalidTransitionLeavesStatusUnchanged() {
        when(toolRepository.findForUpdateById(1L)).thenReturn(Optional.of(tool));

        assertThrows(InvalidStateTransitionException.class,
                () -> service.transition(1L, PublishingAction.RESTORE, new CurrentActor(7L, false)));
        assertEquals(ToolStatus.DRAFT, tool.getStatus());
    }

    @Test
    void nonAdminCannotReadModerationQueue() {
        assertThrows(AccessDeniedException.class,
                () -> service.listPending(PageRequest.of(0, 20), new CurrentActor(7L, false)));
    }

    @Test
    void adminReadsOnlyPendingTools() {
        var pageable = PageRequest.of(0, 20);
        when(toolRepository.findByStatus(ToolStatus.PENDING, pageable)).thenReturn(Page.empty());

        service.listPending(pageable, new CurrentActor(8L, true));

        verify(toolRepository).findByStatus(ToolStatus.PENDING, pageable);
    }

    @Test
    void rejectDeprecateAndRestoreFollowRoleRules() {
        when(toolRepository.findForUpdateById(1L)).thenReturn(Optional.of(tool));
        CurrentActor owner = new CurrentActor(7L, false);
        CurrentActor admin = new CurrentActor(8L, true);

        service.transition(1L, PublishingAction.SUBMIT, owner);
        service.decide(1L, PublishingAction.REJECT, 1L, admin);
        assertEquals(ToolStatus.DRAFT, tool.getStatus());

        service.transition(1L, PublishingAction.SUBMIT, owner);
        service.decide(1L, PublishingAction.APPROVE, 2L, admin);
        service.transition(1L, PublishingAction.DEPRECATE, admin);
        assertEquals(ToolStatus.DEPRECATED, tool.getStatus());

        assertThrows(AccessDeniedException.class,
                () -> service.transition(1L, PublishingAction.RESTORE, admin));
        service.transition(1L, PublishingAction.RESTORE, owner);
        assertEquals(ToolStatus.DRAFT, tool.getStatus());
    }
}
