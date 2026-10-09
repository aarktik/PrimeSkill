package com.example.toolhub.controller.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.example.toolhub.dto.response.ToolResponse;
import com.example.toolhub.domain.enums.ToolStatus;
import com.example.toolhub.exception.InvalidStateTransitionException;
import com.example.toolhub.security.CurrentActor;
import com.example.toolhub.security.CurrentActorProvider;
import com.example.toolhub.service.CategoryService;
import com.example.toolhub.service.ReviewService;
import com.example.toolhub.service.ReviewSummaryService;
import com.example.toolhub.service.ToolService;
import com.example.toolhub.dto.response.ReviewSummary;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class ToolWebControllerTest {
    @Mock private ToolService toolService;
    @Mock private CategoryService categoryService;
    @Mock private CurrentActorProvider currentActorProvider;
    @Mock private ReviewService reviewService;
    @Mock private ReviewSummaryService reviewSummaryService;

    private ToolWebController controller;

    @BeforeEach
    void setUp() {
        controller = new ToolWebController(toolService, categoryService, currentActorProvider,
                reviewService, reviewSummaryService);
    }

    @Test
    void dashboard_usesRequestedPageAndExposesPageMetadata() {
        CurrentActor actor = new CurrentActor(7L, false);
        Pageable requestedPage = PageRequest.of(2, 20, Sort.by(Sort.Direction.DESC, "updatedAt"));
        ToolResponse tool = ToolResponse.builder().id(1L).name("Calendar").build();
        var resultPage = new PageImpl<>(List.of(tool), requestedPage, 45);
        when(currentActorProvider.requireActor()).thenReturn(actor);
        when(toolService.listOwnedBy(eq(7L), any(Pageable.class))).thenReturn(resultPage);
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.dashboard(2, model);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(toolService).listOwnedBy(eq(7L), pageableCaptor.capture());
        assertEquals(2, pageableCaptor.getValue().getPageNumber());
        assertEquals(20, pageableCaptor.getValue().getPageSize());
        assertEquals(Sort.Direction.DESC,
                pageableCaptor.getValue().getSort().getOrderFor("updatedAt").getDirection());
        assertEquals("tools/dashboard", view);
        assertEquals(List.of(tool), model.get("tools"));
        assertSame(resultPage, model.get("toolPage"));
    }

    @Test
    void dashboard_whenPageIsNegative_clampsToFirstPage() {
        CurrentActor actor = new CurrentActor(7L, false);
        when(currentActorProvider.requireActor()).thenReturn(actor);
        when(toolService.listOwnedBy(eq(7L), any(Pageable.class))).thenAnswer(invocation -> {
            Pageable pageable = invocation.getArgument(1);
            return new PageImpl<ToolResponse>(List.of(), pageable, 0);
        });
        ExtendedModelMap model = new ExtendedModelMap();

        controller.dashboard(-3, model);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(toolService).listOwnedBy(eq(7L), pageableCaptor.capture());
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
    }

    @Test
    void detail_usesViewCountingServicePath() {
        CurrentActor actor = new CurrentActor(8L, false);
        ToolResponse tool = ToolResponse.builder().id(1L).name("Calendar").slug("calendar").build();
        when(currentActorProvider.currentActor()).thenReturn(actor);
        when(toolService.getDetailByIdOrSlug("calendar", 8L, false)).thenReturn(tool);
        when(reviewService.listForTool(eq(1L), eq(8L), eq(false), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(reviewSummaryService.summarizeByToolIds(List.of(1L)))
                .thenReturn(java.util.Map.of(1L, new ReviewSummary(1L, null, 0)));
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.detail("calendar", 0, model);

        verify(toolService).getDetailByIdOrSlug("calendar", 8L, false);
        assertEquals("tools/detail", view);
        assertSame(tool, model.get("tool"));
        assertEquals(0L, ((ReviewSummary) model.get("reviewSummary")).reviewCount());
    }

    @ParameterizedTest
    @EnumSource(value = ToolStatus.class, names = {"PENDING", "PUBLISHED", "DEPRECATED"})
    void editForm_rejectsNonDraftForOwnerAndAdmin(ToolStatus status) {
        ToolResponse tool = ToolResponse.builder().id(1L).ownerId(7L).status(status).build();
        for (CurrentActor actor : List.of(new CurrentActor(7L, false), new CurrentActor(8L, true))) {
            when(currentActorProvider.requireActor()).thenReturn(actor);
            when(toolService.getByIdOrSlug("1", actor.id(), actor.admin())).thenReturn(tool);
            assertThrows(InvalidStateTransitionException.class, () -> controller.editForm(1L, new ExtendedModelMap()));
        }
        verify(categoryService, never()).findAll();
    }

    @Test
    void editForm_doesNotExposePublishedEditorToOtherUser() {
        when(currentActorProvider.requireActor()).thenReturn(new CurrentActor(9L, false));
        when(toolService.getByIdOrSlug("1", 9L, false))
                .thenReturn(ToolResponse.builder().id(1L).ownerId(7L).status(ToolStatus.PUBLISHED).build());
        assertThrows(AccessDeniedException.class, () -> controller.editForm(1L, new ExtendedModelMap()));
        verify(categoryService, never()).findAll();
    }

    @Test
    void editForm_allowsOwnerToEditDraftWithoutCountingView() {
        when(currentActorProvider.requireActor()).thenReturn(new CurrentActor(7L, false));
        when(toolService.getByIdOrSlug("1", 7L, false))
                .thenReturn(ToolResponse.builder().id(1L).ownerId(7L).status(ToolStatus.DRAFT).build());
        when(categoryService.findAll()).thenReturn(List.of());
        ExtendedModelMap model = new ExtendedModelMap();
        assertEquals("tools/form", controller.editForm(1L, model));
        assertEquals("/dashboard/tools/1", model.get("formAction"));
        verify(toolService, never()).getDetailByIdOrSlug(any(), any(), eq(false));
    }
}
