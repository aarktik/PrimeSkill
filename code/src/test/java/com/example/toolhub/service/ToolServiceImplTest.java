package com.example.toolhub.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.toolhub.domain.entity.Category;
import com.example.toolhub.domain.entity.Tool;
import com.example.toolhub.domain.enums.ToolStatus;
import com.example.toolhub.dto.request.CreateToolRequest;
import com.example.toolhub.dto.request.UpdateToolRequest;
import com.example.toolhub.dto.response.ToolResponse;
import com.example.toolhub.exception.CatalogConflictException;
import com.example.toolhub.exception.ResourceNotFoundException;
import com.example.toolhub.mapper.ToolMapper;
import com.example.toolhub.repository.CategoryRepository;
import com.example.toolhub.repository.ToolRepository;
import com.example.toolhub.service.impl.ToolServiceImpl;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ToolServiceImplTest {
    @Mock private ToolRepository toolRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ToolMapper toolMapper;

    private ToolServiceImpl service;
    private Category category;

    @BeforeEach
    void setUp() {
        service = new ToolServiceImpl(toolRepository, categoryRepository, toolMapper);
        category = new Category("Automation", "automation", "Automation tools");
    }

    @Test
    void create_whenValid_persistsDraftForActor() {
        var request = new CreateToolRequest("Calendar", "calendar", "Calendar helper",
                "A calendar tool", 4L, "https://example.com/repo");
        when(categoryRepository.findById(4L)).thenReturn(java.util.Optional.of(category));
        when(toolRepository.save(any(Tool.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(toolMapper.toResponse(any(Tool.class))).thenReturn(ToolResponse.builder().id(1L).build());

        service.create(request, 7L);

        ArgumentCaptor<Tool> captor = ArgumentCaptor.forClass(Tool.class);
        verify(toolRepository).save(captor.capture());
        Tool saved = captor.getValue();
        assertEquals(ToolStatus.DRAFT, saved.getStatus());
        assertEquals(7L, saved.getOwnerId());
        assertEquals(category, saved.getCategory());
        assertEquals("calendar", saved.getSlug());
        assertEquals(0L, saved.getViewCount());
    }

    @Test
    void create_whenSlugExists_throwsConflict() {
        var request = new CreateToolRequest("Calendar", "calendar", "Calendar helper",
                "A calendar tool", 4L, null);
        when(toolRepository.existsBySlug("calendar")).thenReturn(true);

        assertThrows(CatalogConflictException.class, () -> service.create(request, 7L));
        verify(categoryRepository, never()).findById(any());
    }

    @Test
    void update_whenActorIsNotOwner_throwsAccessDenied() {
        Tool tool = new Tool(7L, category, "Calendar", "calendar", "Helper", "Details", null);
        when(toolRepository.findById(1L)).thenReturn(java.util.Optional.of(tool));
        var request = new UpdateToolRequest("Changed", "changed", "Changed", "Details", 4L, null);

        assertThrows(AccessDeniedException.class, () -> service.update(1L, request, 8L, false));
        verify(categoryRepository, never()).findById(any());
    }

    @Test
    void update_whenAdmin_updatesTool() {
        Tool tool = new Tool(7L, category, "Calendar", "calendar", "Helper", "Details", null);
        when(toolRepository.findById(1L)).thenReturn(java.util.Optional.of(tool));
        when(categoryRepository.findById(4L)).thenReturn(java.util.Optional.of(category));
        when(toolMapper.toResponse(tool)).thenReturn(ToolResponse.builder().name("Changed").build());
        var request = new UpdateToolRequest("Changed", "changed", "Changed", "New details", 4L, null);

        service.update(1L, request, 8L, true);

        assertEquals("Changed", tool.getName());
        assertEquals("changed", tool.getSlug());
    }

    /**
     * Records the existing B implementation, not the future B1 policy.
     * Replace the owner/admin expectations after B/E confirm the policy in
     * doc/role-b-tool-edit-policy-proposal.md; no production behavior changes here.
     */
    @Tag("b1-baseline")
    @ParameterizedTest(name = "current behavior: {0}, {1}")
    @MethodSource("currentUpdateCases")
    void update_currentBehaviorAcrossStatusAndActor(ToolStatus status, UpdateActor actor) {
        Tool tool = new Tool(7L, category, "Calendar", "calendar", "Helper",
                "Details", "https://example.com/original");
        ReflectionTestUtils.setField(tool, "id", 1L);
        ReflectionTestUtils.setField(tool, "status", status);
        ReflectionTestUtils.setField(tool, "viewCount", 17L);
        when(toolRepository.findById(1L)).thenReturn(Optional.of(tool));
        var request = new UpdateToolRequest("Changed", "changed", "Changed helper",
                "New details", 4L, "https://example.com/changed");

        if (actor == UpdateActor.OTHER_USER) {
            assertThrows(AccessDeniedException.class,
                    () -> service.update(1L, request, actor.id, actor.admin));
            verify(toolRepository, never()).existsBySlugAndIdNot(any(), any());
            verify(categoryRepository, never()).findById(any());
            verify(toolMapper, never()).toResponse(any());
            assertAll(
                    () -> assertEquals("Calendar", tool.getName()),
                    () -> assertEquals("calendar", tool.getSlug()),
                    () -> assertEquals("Helper", tool.getShortDescription()),
                    () -> assertEquals("Details", tool.getDescription()),
                    () -> assertEquals("https://example.com/original", tool.getRepositoryUrl()),
                    () -> assertSame(category, tool.getCategory()));
        } else {
            Category replacement = new Category("Productivity", "productivity", "New category");
            ReflectionTestUtils.setField(replacement, "id", 4L);
            when(categoryRepository.findById(4L)).thenReturn(Optional.of(replacement));
            when(toolMapper.toResponse(tool)).thenAnswer(invocation -> new ToolMapper().toResponse(tool));

            ToolResponse response = service.update(1L, request, actor.id, actor.admin);

            assertAll(
                    () -> assertEquals("Changed", tool.getName()),
                    () -> assertEquals("changed", tool.getSlug()),
                    () -> assertEquals("Changed helper", tool.getShortDescription()),
                    () -> assertEquals("New details", tool.getDescription()),
                    () -> assertEquals("https://example.com/changed", tool.getRepositoryUrl()),
                    () -> assertSame(replacement, tool.getCategory()),
                    () -> assertEquals("Changed", response.getName()),
                    () -> assertEquals(4L, response.getCategoryId()),
                    () -> assertEquals(status, response.getStatus()),
                    () -> assertEquals(7L, response.getOwnerId()));
        }

        assertAll(
                () -> assertEquals(1L, tool.getId()),
                () -> assertEquals(7L, tool.getOwnerId()),
                () -> assertEquals(status, tool.getStatus()),
                () -> assertEquals(17L, tool.getViewCount()));
    }

    private static Stream<Arguments> currentUpdateCases() {
        return Arrays.stream(ToolStatus.values()).flatMap(status ->
                Arrays.stream(UpdateActor.values()).map(actor -> Arguments.of(status, actor)));
    }

    private enum UpdateActor {
        OWNER(7L, false), ADMIN(8L, true), OTHER_USER(9L, false);

        private final Long id;
        private final boolean admin;

        UpdateActor(Long id, boolean admin) {
            this.id = id;
            this.admin = admin;
        }
    }

    @Test
    void delete_whenOwner_deletesTool() {
        Tool tool = new Tool(7L, category, "Calendar", "calendar", "Helper", "Details", null);
        when(toolRepository.findById(1L)).thenReturn(java.util.Optional.of(tool));

        service.delete(1L, 7L, false);

        verify(toolRepository).delete(tool);
    }

    @Test
    void get_whenToolDoesNotExist_throwsNotFound() {
        when(toolRepository.findBySlug("missing")).thenReturn(java.util.Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getByIdOrSlug("missing", null, false));
    }

    @Test
    void getDetail_whenAnonymousViewsPublishedTool_incrementsAtomically() {
        Tool tool = mock(Tool.class);
        ToolResponse response = ToolResponse.builder().id(1L).status(ToolStatus.PUBLISHED).build();
        when(tool.getId()).thenReturn(1L);
        when(tool.getStatus()).thenReturn(ToolStatus.PUBLISHED);
        when(toolRepository.findBySlug("calendar")).thenReturn(java.util.Optional.of(tool));
        when(toolMapper.toResponse(tool)).thenReturn(response);

        assertEquals(response, service.getDetailByIdOrSlug("calendar", null, false));

        verify(toolRepository).incrementPublishedViewCount(1L, ToolStatus.PUBLISHED);
    }

    @Test
    void getDetail_whenSignedInNonOwnerViewsPublishedTool_incrementsAtomically() {
        Tool tool = mock(Tool.class);
        when(tool.getId()).thenReturn(1L);
        when(tool.getOwnerId()).thenReturn(7L);
        when(tool.getStatus()).thenReturn(ToolStatus.PUBLISHED);
        when(toolRepository.findBySlug("calendar")).thenReturn(java.util.Optional.of(tool));
        when(toolMapper.toResponse(tool)).thenReturn(ToolResponse.builder().id(1L).build());

        service.getDetailByIdOrSlug("calendar", 8L, false);

        verify(toolRepository).incrementPublishedViewCountExcludingOwner(
                1L, ToolStatus.PUBLISHED, 8L);
    }

    @Test
    void getDetail_whenOwnerViewsPublishedTool_doesNotIncrement() {
        Tool tool = mock(Tool.class);
        when(tool.getOwnerId()).thenReturn(7L);
        when(tool.getStatus()).thenReturn(ToolStatus.PUBLISHED);
        when(toolRepository.findBySlug("calendar")).thenReturn(java.util.Optional.of(tool));
        when(toolMapper.toResponse(tool)).thenReturn(ToolResponse.builder().id(1L).build());

        service.getDetailByIdOrSlug("calendar", 7L, false);

        verify(toolRepository, never()).incrementPublishedViewCount(any(), any());
        verify(toolRepository, never()).incrementPublishedViewCountExcludingOwner(any(), any(), any());
    }

    @Test
    void getDetail_whenOwnerViewsDraftTool_doesNotIncrement() {
        Tool tool = mock(Tool.class);
        when(tool.getOwnerId()).thenReturn(7L);
        when(tool.getStatus()).thenReturn(ToolStatus.DRAFT);
        when(toolRepository.findBySlug("calendar")).thenReturn(java.util.Optional.of(tool));
        when(toolMapper.toResponse(tool)).thenReturn(ToolResponse.builder().id(1L).build());

        service.getDetailByIdOrSlug("calendar", 7L, false);

        verify(toolRepository, never()).incrementPublishedViewCount(any(), any());
        verify(toolRepository, never()).incrementPublishedViewCountExcludingOwner(any(), any(), any());
    }
}
