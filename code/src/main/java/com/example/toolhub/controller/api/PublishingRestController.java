package com.example.toolhub.controller.api;

import com.example.toolhub.domain.enums.PublishingAction;
import com.example.toolhub.dto.response.ToolResponse;
import com.example.toolhub.exception.InvalidRequestParameterException;
import java.util.Set;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import com.example.toolhub.dto.request.ReviewDecisionRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.example.toolhub.security.CurrentActorProvider;
import com.example.toolhub.service.PublishingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Publishing")
public class PublishingRestController {
    private static final Set<String> SORT_FIELDS = Set.of("id", "name", "createdAt", "updatedAt");
    private final PublishingService publishingService;
    private final CurrentActorProvider currentActorProvider;

    public PublishingRestController(PublishingService publishingService, CurrentActorProvider currentActorProvider) {
        this.publishingService = publishingService;
        this.currentActorProvider = currentActorProvider;
    }

    @PostMapping("/tools/{id}/submit")
    @Operation(summary = "Submit an owned draft for approval")
    public ResponseEntity<ToolResponse> submit(@PathVariable Long id) {
        return transition(id, PublishingAction.SUBMIT);
    }

    @PostMapping("/admin/tools/{id}/approve")
    @Operation(summary = "Approve a pending tool")
    public ResponseEntity<ToolResponse> approve(@PathVariable Long id, @Valid @RequestBody ReviewDecisionRequest request) {
        return ResponseEntity.ok(publishingService.decide(id, PublishingAction.APPROVE,
                request.expectedReviewRevision(), currentActorProvider.requireActor()));
    }

    @PostMapping("/admin/tools/{id}/reject")
    @Operation(summary = "Reject a pending tool")
    public ResponseEntity<ToolResponse> reject(@PathVariable Long id, @Valid @RequestBody ReviewDecisionRequest request) {
        return ResponseEntity.ok(publishingService.decide(id, PublishingAction.REJECT,
                request.expectedReviewRevision(), currentActorProvider.requireActor()));
    }

    @PostMapping("/tools/{id}/deprecate")
    @Operation(summary = "Deprecate a published tool")
    public ResponseEntity<ToolResponse> deprecate(@PathVariable Long id) {
        return transition(id, PublishingAction.DEPRECATE);
    }

    @PostMapping("/tools/{id}/restore")
    @Operation(summary = "Restore an owned deprecated tool to draft")
    public ResponseEntity<ToolResponse> restore(@PathVariable Long id) {
        return transition(id, PublishingAction.RESTORE);
    }

    @GetMapping("/admin/tools/pending")
    @Operation(summary = "List tools awaiting admin approval")
    public ResponseEntity<Page<ToolResponse>> pending(@PageableDefault(size = 20) Pageable pageable) {
        var actor = currentActorProvider.requireActor();
        for (Sort.Order order : pageable.getSort()) {
            if (!SORT_FIELDS.contains(order.getProperty())) {
                throw new InvalidRequestParameterException("Sort must use id, name, createdAt or updatedAt");
            }
        }
        // A unique tie-breaker keeps page boundaries stable when names or timestamps match.
        Sort sort = pageable.getSort();
        if (sort.getOrderFor("id") == null) {
            sort = sort.and(Sort.by("id"));
        }
        return ResponseEntity.ok(publishingService.listPending(
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort), actor));
    }

    private ResponseEntity<ToolResponse> transition(Long id, PublishingAction action) {
        return ResponseEntity.ok(publishingService.transition(id, action, currentActorProvider.requireActor()));
    }
}
