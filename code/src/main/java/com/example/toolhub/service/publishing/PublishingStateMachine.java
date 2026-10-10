package com.example.toolhub.service.publishing;

import com.example.toolhub.domain.enums.PublishingAction;
import com.example.toolhub.domain.enums.ToolStatus;
import com.example.toolhub.exception.InvalidStateTransitionException;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** State handlers are the only place that defines valid publishing transitions. */
@Component
public class PublishingStateMachine {
    private final Map<ToolStatus, PublishingState> states = new EnumMap<>(ToolStatus.class);

    public PublishingStateMachine() {
        register(new DraftState());
        register(new PendingState());
        register(new PublishedState());
        register(new DeprecatedState());
    }

    public ToolStatus transition(ToolStatus current, PublishingAction action) {
        if (current == null || action == null) {
            throw new InvalidStateTransitionException("Publishing state and action are required");
        }
        return states.get(current).transition(action);
    }

    private void register(PublishingState state) {
        states.put(state.status(), state);
    }

    private abstract static class BaseState implements PublishingState {
        protected ToolStatus invalid(PublishingAction action) {
            throw new InvalidStateTransitionException(
                    "Cannot " + action.name().toLowerCase() + " a " + status().name().toLowerCase() + " tool");
        }
    }

    private static final class DraftState extends BaseState {
        public ToolStatus status() { return ToolStatus.DRAFT; }
        public ToolStatus transition(PublishingAction action) {
            return action == PublishingAction.SUBMIT ? ToolStatus.PENDING : invalid(action);
        }
    }

    private static final class PendingState extends BaseState {
        public ToolStatus status() { return ToolStatus.PENDING; }
        public ToolStatus transition(PublishingAction action) {
            return switch (action) {
                case APPROVE -> ToolStatus.PUBLISHED;
                case REJECT -> ToolStatus.DRAFT;
                default -> invalid(action);
            };
        }
    }

    private static final class PublishedState extends BaseState {
        public ToolStatus status() { return ToolStatus.PUBLISHED; }
        public ToolStatus transition(PublishingAction action) {
            return action == PublishingAction.DEPRECATE ? ToolStatus.DEPRECATED : invalid(action);
        }
    }

    private static final class DeprecatedState extends BaseState {
        public ToolStatus status() { return ToolStatus.DEPRECATED; }
        public ToolStatus transition(PublishingAction action) {
            return action == PublishingAction.RESTORE ? ToolStatus.DRAFT : invalid(action);
        }
    }
}
