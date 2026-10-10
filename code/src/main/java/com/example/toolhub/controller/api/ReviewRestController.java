package com.example.toolhub.controller.api;

import com.example.toolhub.dto.request.CreateReviewRequest;
import com.example.toolhub.dto.request.UpdateReviewRequest;
import com.example.toolhub.dto.response.PagedResponse;
import com.example.toolhub.dto.response.ReviewResponse;
import com.example.toolhub.security.CurrentActor;
import com.example.toolhub.security.CurrentActorProvider;
import com.example.toolhub.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tools/{toolId}/reviews")
@Tag(name = "Reviews")
public class ReviewRestController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ReviewService reviewService;
    private final CurrentActorProvider currentActorProvider;

    public ReviewRestController(ReviewService reviewService, CurrentActorProvider currentActorProvider) {
        this.reviewService = reviewService;
        this.currentActorProvider = currentActorProvider;
    }

    @GetMapping
    @Operation(summary = "List reviews for a visible tool")
    public ResponseEntity<PagedResponse<ReviewResponse>> list(
            @PathVariable Long toolId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = pageable(page, size);
        CurrentActor actor = currentActorProvider.currentActor();
        var result = reviewService.listForTool(toolId, actor.id(), actor.admin(), pageable);
        return ResponseEntity.ok(PagedResponse.from(result));
    }

    @PostMapping
    @Operation(summary = "Create one review for a published tool")
    public ResponseEntity<ReviewResponse> create(@PathVariable Long toolId,
                                                @Valid @RequestBody CreateReviewRequest request) {
        CurrentActor actor = currentActorProvider.requireActor();
        ReviewResponse response = reviewService.create(toolId, request, actor.id(), actor.admin());
        return ResponseEntity.created(URI.create("/api/v1/tools/" + toolId + "/reviews/" + response.id()))
                .body(response);
    }

    @PutMapping("/{reviewId}")
    @Operation(summary = "Edit the current user's review")
    public ResponseEntity<ReviewResponse> update(@PathVariable Long toolId, @PathVariable Long reviewId,
                                                 @Valid @RequestBody UpdateReviewRequest request) {
        CurrentActor actor = currentActorProvider.requireActor();
        return ResponseEntity.ok(reviewService.update(toolId, reviewId, request, actor.id(), actor.admin()));
    }

    @DeleteMapping("/{reviewId}")
    @Operation(summary = "Delete the current user's review or an admin's review")
    public ResponseEntity<Void> delete(@PathVariable Long toolId, @PathVariable Long reviewId) {
        CurrentActor actor = currentActorProvider.requireActor();
        reviewService.delete(toolId, reviewId, actor.id(), actor.admin());
        return ResponseEntity.noContent().build();
    }

    private Pageable pageable(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100");
        }
        return PageRequest.of(page, size);
    }
}
