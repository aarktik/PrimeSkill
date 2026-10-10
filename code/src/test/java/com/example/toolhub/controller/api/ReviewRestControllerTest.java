package com.example.toolhub.controller.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.toolhub.dto.request.CreateReviewRequest;
import com.example.toolhub.dto.request.UpdateReviewRequest;
import com.example.toolhub.dto.response.ReviewResponse;
import com.example.toolhub.security.CurrentActor;
import com.example.toolhub.security.CurrentActorProvider;
import com.example.toolhub.service.ReviewService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ReviewRestControllerTest {

    @Mock private ReviewService reviewService;
    @Mock private CurrentActorProvider currentActorProvider;

    private ReviewRestController controller;

    @BeforeEach
    void setUp() {
        controller = new ReviewRestController(reviewService, currentActorProvider);
    }

    @Test
    void list_usesDefaultReviewPageAndCurrentActor() {
        CurrentActor actor = new CurrentActor(7L, false);
        when(currentActorProvider.currentActor()).thenReturn(actor);
        when(reviewService.listForTool(eq(3L), eq(7L), eq(false), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        var response = controller.list(3L, 0, 20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().page());
        assertEquals(20, response.getBody().size());
        verify(reviewService).listForTool(eq(3L), eq(7L), eq(false), eq(PageRequest.of(0, 20)));
    }

    @Test
    void create_returns201AndResourceLocation() {
        CurrentActor actor = new CurrentActor(7L, false);
        ReviewResponse review = new ReviewResponse(19L, 3L, 7L, "Reviewer", (short) 5, null, null, null);
        when(currentActorProvider.requireActor()).thenReturn(actor);
        when(reviewService.create(3L, new CreateReviewRequest((short) 5, null), 7L, false)).thenReturn(review);

        var response = controller.create(3L, new CreateReviewRequest((short) 5, null));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("/api/v1/tools/3/reviews/19", response.getHeaders().getLocation().toString());
        assertEquals(review, response.getBody());
    }

    @Test
    void updateAndDelete_passActorAndNestedIdentifiers() {
        CurrentActor actor = new CurrentActor(7L, true);
        ReviewResponse review = new ReviewResponse(19L, 3L, 7L, "Reviewer", (short) 4, null, null, null);
        when(currentActorProvider.requireActor()).thenReturn(actor);
        when(reviewService.update(3L, 19L, new UpdateReviewRequest((short) 4, null), 7L, true))
                .thenReturn(review);

        var updated = controller.update(3L, 19L, new UpdateReviewRequest((short) 4, null));
        var deleted = controller.delete(3L, 19L);

        assertEquals(HttpStatus.OK, updated.getStatusCode());
        assertEquals(review, updated.getBody());
        assertEquals(HttpStatus.NO_CONTENT, deleted.getStatusCode());
        verify(reviewService).delete(3L, 19L, 7L, true);
    }

    @Test
    void list_rejectsInvalidPageWindowBeforeQuerying() {
        assertThrows(IllegalArgumentException.class, () -> controller.list(3L, -1, 20));
        assertThrows(IllegalArgumentException.class, () -> controller.list(3L, 0, 101));
        verify(reviewService, never()).listForTool(any(), any(), anyBoolean(), any());
    }
}
