package com.example.toolhub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.toolhub.domain.entity.Tag;
import com.example.toolhub.domain.entity.Tool;
import com.example.toolhub.dto.request.TagRequest;
import com.example.toolhub.exception.CatalogConflictException;
import com.example.toolhub.mapper.TagMapper;
import com.example.toolhub.repository.TagRepository;
import com.example.toolhub.repository.ToolRepository;
import com.example.toolhub.repository.ToolTagRepository;
import com.example.toolhub.service.impl.TagServiceImpl;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class TagServiceImplTest {
    @Mock private TagRepository tagRepository;
    @Mock private ToolRepository toolRepository;
    @Mock private ToolTagRepository toolTagRepository;

    private TagServiceImpl tagService;

    @BeforeEach
    void setUp() {
        tagService = new TagServiceImpl(tagRepository, toolRepository, toolTagRepository, new TagMapper());
    }

    @Test
    void create_whenDuplicateSlug_throwsConflict() {
        when(tagRepository.existsBySlug("calendar")).thenReturn(true);

        assertThrows(CatalogConflictException.class,
                () -> tagService.create(new TagRequest("Calendar", "calendar"), true));
    }

    @Test
    void create_whenNotAdmin_throwsAccessDenied() {
        assertThrows(AccessDeniedException.class,
                () -> tagService.create(new TagRequest("Calendar", "calendar"), false));
    }

    @Test
    void create_whenValid_savesTag() {
        when(tagRepository.existsByNameIgnoreCase("Calendar")).thenReturn(false);
        when(tagRepository.existsBySlug("calendar")).thenReturn(false);
        when(tagRepository.save(any(Tag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = tagService.create(new TagRequest("Calendar", "calendar"), true);

        assertEquals("calendar", response.slug());
        verify(tagRepository).save(any(Tag.class));
    }

    @Test
    void delete_whenTagIsReferenced_throwsConflict() {
        when(tagRepository.findById(1L)).thenReturn(Optional.of(new Tag("Calendar", "calendar")));
        when(toolTagRepository.countByIdTagId(1L)).thenReturn(2L);

        assertThrows(CatalogConflictException.class, () -> tagService.delete(1L, true));
    }

    @Test
    void assignTag_whenAlreadyAssigned_throwsConflict() {
        Tool tool = org.mockito.Mockito.mock(Tool.class);
        when(tool.getOwnerId()).thenReturn(7L);
        when(toolRepository.findById(1L)).thenReturn(Optional.of(tool));
        when(tagRepository.findById(2L)).thenReturn(Optional.of(new Tag("Calendar", "calendar")));
        when(toolTagRepository.existsByIdToolIdAndIdTagId(1L, 2L)).thenReturn(true);

        assertThrows(CatalogConflictException.class,
                () -> tagService.assignTag(1L, 2L, 7L, false));
    }

    @Test
    void assignTag_whenNonOwner_throwsAccessDenied() {
        Tool tool = org.mockito.Mockito.mock(Tool.class);
        when(tool.getOwnerId()).thenReturn(7L);
        when(toolRepository.findById(1L)).thenReturn(Optional.of(tool));
        when(tagRepository.findById(2L)).thenReturn(Optional.of(new Tag("Calendar", "calendar")));

        assertThrows(AccessDeniedException.class,
                () -> tagService.assignTag(1L, 2L, 9L, false));
    }
}
