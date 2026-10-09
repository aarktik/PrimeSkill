package com.example.toolhub.service.publishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.toolhub.domain.enums.PublishingAction;
import com.example.toolhub.domain.enums.ToolStatus;
import com.example.toolhub.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;

class PublishingStateMachineTest {
    private final PublishingStateMachine stateMachine = new PublishingStateMachine();

    @Test
    void supportsEveryAllowedTransition() {
        assertEquals(ToolStatus.PENDING, stateMachine.transition(ToolStatus.DRAFT, PublishingAction.SUBMIT));
        assertEquals(ToolStatus.PUBLISHED, stateMachine.transition(ToolStatus.PENDING, PublishingAction.APPROVE));
        assertEquals(ToolStatus.DRAFT, stateMachine.transition(ToolStatus.PENDING, PublishingAction.REJECT));
        assertEquals(ToolStatus.DEPRECATED, stateMachine.transition(ToolStatus.PUBLISHED, PublishingAction.DEPRECATE));
        assertEquals(ToolStatus.DRAFT, stateMachine.transition(ToolStatus.DEPRECATED, PublishingAction.RESTORE));
    }

    @Test
    void rejectsActionsOutsideCurrentState() {
        for (ToolStatus status : ToolStatus.values()) {
            for (PublishingAction action : PublishingAction.values()) {
                boolean allowed = switch (status) {
                    case DRAFT -> action == PublishingAction.SUBMIT;
                    case PENDING -> action == PublishingAction.APPROVE || action == PublishingAction.REJECT;
                    case PUBLISHED -> action == PublishingAction.DEPRECATE;
                    case DEPRECATED -> action == PublishingAction.RESTORE;
                };
                if (!allowed) {
                    assertThrows(InvalidStateTransitionException.class,
                            () -> stateMachine.transition(status, action), status + " / " + action);
                }
            }
        }
    }
}
