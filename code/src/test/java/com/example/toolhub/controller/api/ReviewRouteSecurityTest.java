package com.example.toolhub.controller.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.toolhub.config.PasswordConfig;
import com.example.toolhub.config.SecurityConfig;
import com.example.toolhub.controller.web.ReviewWebController;
import com.example.toolhub.domain.enums.ToolStatus;
import com.example.toolhub.dto.request.CreateReviewRequest;
import com.example.toolhub.dto.request.UpdateReviewRequest;
import com.example.toolhub.dto.response.ReviewResponse;
import com.example.toolhub.dto.response.ReviewSummary;
import com.example.toolhub.dto.response.ToolResponse;
import com.example.toolhub.security.CurrentActor;
import com.example.toolhub.security.CurrentActorProvider;
import com.example.toolhub.security.JpaUserDetailsService;
import com.example.toolhub.service.ReviewService;
import com.example.toolhub.service.ReviewSummaryService;
import com.example.toolhub.service.ToolService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({ReviewRestController.class, ReviewWebController.class})
@Import({
        com.example.toolhub.controller.web.UiText.class,
        SecurityConfig.class,
        PasswordConfig.class,
        com.example.toolhub.security.RestSecurityExceptionHandler.class
})
class ReviewRouteSecurityTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ReviewService reviewService;
    @MockitoBean private ReviewSummaryService reviewSummaryService;
    @MockitoBean private ToolService toolService;
    @MockitoBean private com.example.toolhub.service.TagService tagService;
    @MockitoBean private CurrentActorProvider currentActorProvider;
    @MockitoBean private JpaUserDetailsService userDetailsService;
    @MockitoBean(name = "jpaMappingContext") private JpaMetamodelMappingContext jpaMappingContext;

    @Test
    void anonymous_canReadReviewsForPublishedTool() throws Exception {
        when(currentActorProvider.currentActor()).thenReturn(new CurrentActor(null, false));
        when(reviewService.listForTool(eq(3L), isNull(), eq(false), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/tools/3/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(reviewService).listForTool(eq(3L), isNull(), eq(false), any(Pageable.class));
    }

    @Test
    void anonymous_cannotMutateReviewApiOrSubmitWebReview() throws Exception {
        mockMvc.perform(post("/api/v1/tools/3/reviews")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"comment\":\"ok\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/tools/3/reviews/8")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":4,\"comment\":\"ok\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/tools/3/reviews/8").with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/tools/3/reviews").with(csrf()).param("rating", "5"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(reviewService);
    }

    @Test
    void anonymous_cannotReadPersonalReviews() throws Exception {
        mockMvc.perform(get("/my/reviews"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(reviewService);
    }

    @Test
    void authenticatedMember_canUseCreateUpdateAndDeleteApiRoutes() throws Exception {
        CurrentActor actor = new CurrentActor(7L, false);
        ReviewResponse review = new ReviewResponse(8L, 3L, 7L, "Reviewer", (short) 5,
                "ok", Instant.now(), Instant.now());
        when(currentActorProvider.requireActor()).thenReturn(actor);
        when(reviewService.create(3L, new CreateReviewRequest((short) 5, "ok"), 7L, false))
                .thenReturn(review);
        when(reviewService.update(3L, 8L, new UpdateReviewRequest((short) 4, "updated"), 7L, false))
                .thenReturn(new ReviewResponse(8L, 3L, 7L, "Reviewer", (short) 4,
                        "updated", review.createdAt(), Instant.now()));

        mockMvc.perform(post("/api/v1/tools/3/reviews")
                        .with(user("member").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"comment\":\"ok\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/v1/tools/3/reviews/8")
                        .with(user("member").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":4,\"comment\":\"updated\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/tools/3/reviews/8")
                        .with(user("member").roles("USER"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(reviewService).create(3L, new CreateReviewRequest((short) 5, "ok"), 7L, false);
        verify(reviewService).update(3L, 8L, new UpdateReviewRequest((short) 4, "updated"), 7L, false);
        verify(reviewService).delete(3L, 8L, 7L, false);
    }

    @Test
    void invalidWebUpdate_rendersDetailAndPreservesSubmittedValues() throws Exception {
        CurrentActor actor = new CurrentActor(7L, false);
        ToolResponse tool = ToolResponse.builder()
                .id(3L).name("Review Tool").slug("review-tool").ownerId(9L)
                .status(ToolStatus.PUBLISHED).shortDescription("Short").description("Description")
                .build();
        ReviewResponse existingReview = new ReviewResponse(8L, 3L, 7L, "Reviewer", (short) 2,
                "old comment", Instant.now(), Instant.now());
        String submittedComment = "keep this invalid value " + "x".repeat(2001);

        when(currentActorProvider.requireActor()).thenReturn(actor);
        when(toolService.getById(3L, 7L, false)).thenReturn(tool);
        when(reviewService.listForTool(eq(3L), eq(7L), eq(false), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(existingReview), PageRequest.of(0, 20), 1));
        when(reviewSummaryService.summarizeByToolIds(List.of(3L)))
                .thenReturn(Map.of(3L, new ReviewSummary(3L, 2.0, 1)));
        when(reviewService.findMineForTool(3L, 7L)).thenReturn(existingReview);

        mockMvc.perform(post("/tools/3/reviews/8")
                        .with(user("member").roles("USER"))
                        .with(csrf())
                        .param("rating", "4")
                        .param("comment", submittedComment))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(submittedComment)))
                .andExpect(content().string(containsString("Use at most 2,000 characters.")))
                .andExpect(content().string(containsString("selected=\"selected\">4</option>")));

        verify(reviewService, never())
                .update(eq(3L), eq(8L), any(), eq(7L), eq(false));
    }
}
