package com.example.toolhub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

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
