package com.example.toolhub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.example.toolhub.domain.entity.Category;
import com.example.toolhub.domain.entity.Tool;
import com.example.toolhub.domain.entity.ToolVersion;
import com.example.toolhub.domain.enums.ToolStatus;
import com.example.toolhub.dto.request.ToolVersionRequest;
import com.example.toolhub.exception.CatalogConflictException;
import com.example.toolhub.exception.InvalidStateTransitionException;
import com.example.toolhub.exception.ResourceNotFoundException;
import com.example.toolhub.repository.ToolRepository;
import com.example.toolhub.repository.ToolVersionRepository;
import com.example.toolhub.security.CurrentActor;
import com.example.toolhub.service.impl.ToolVersionServiceImpl;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ToolVersionServiceImplTest {
    @Mock private ToolRepository toolRepository;
    @Mock private ToolVersionRepository versionRepository;
    private ToolVersionServiceImpl service;
    private Tool tool;

    @BeforeEach
    void setUp() {
        service = new ToolVersionServiceImpl(toolRepository, versionRepository);
        tool = new Tool(7L, new Category("Automation", "automation", null),
                "Tool", "tool", "Short", "Description", null);
        ReflectionTestUtils.setField(tool, "id", 1L);
    }

    @Test
    void ownerCanCreateVersionAndWhitespaceIsNormalized() {
        when(toolRepository.findForUpdateById(1L)).thenReturn(Optional.of(tool));
        when(versionRepository.save(any(ToolVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(1L, new ToolVersionRequest(" 1.0.0 ", "First release"),
                new CurrentActor(7L, false));

        assertEquals("1.0.0", response.version());
        assertEquals(1L, response.toolId());
    }

    @Test
    void duplicateVersionIsRejected() {
        when(toolRepository.findForUpdateById(1L)).thenReturn(Optional.of(tool));
        when(versionRepository.existsByToolIdAndVersion(1L, "1.0.0")).thenReturn(true);

        assertThrows(CatalogConflictException.class,
                () -> service.create(1L, new ToolVersionRequest("1.0.0", null), new CurrentActor(7L, false)));
    }

    @Test
    void nonOwnerCannotEditVersions() {
        when(toolRepository.findForUpdateById(1L)).thenReturn(Optional.of(tool));

        assertThrows(AccessDeniedException.class,
                () -> service.create(1L, new ToolVersionRequest("1.0.0", null), new CurrentActor(8L, false)));
    }

    @Test
    void publishedToolVersionsAreImmutable() {
        tool.changeStatus(ToolStatus.PUBLISHED);
        when(toolRepository.findForUpdateById(1L)).thenReturn(Optional.of(tool));

        assertThrows(InvalidStateTransitionException.class,
                () -> service.create(1L, new ToolVersionRequest("1.0.0", null), new CurrentActor(7L, false)));
    }

    @Test
    void draftVersionsAreHiddenFromAnonymousUsers() {
        when(toolRepository.findById(1L)).thenReturn(Optional.of(tool));

        assertThrows(ResourceNotFoundException.class,
                () -> service.list(1L, new CurrentActor(null, false)));
    }

    @Test
    void publishedVersionsArePublic() {
        tool.changeStatus(ToolStatus.PUBLISHED);
        when(toolRepository.findById(1L)).thenReturn(Optional.of(tool));
        when(versionRepository.findByToolIdOrderByIdDesc(1L))
                .thenReturn(List.of(new ToolVersion(tool, "1.0.0", "First release")));

        assertEquals("1.0.0", service.list(1L, new CurrentActor(null, false)).get(0).version());
    }

    @Test
    void cannotReadDraftVersionAsNonOwner() {
        when(toolRepository.findById(1L)).thenReturn(Optional.of(tool));

        assertThrows(ResourceNotFoundException.class,
                () -> service.get(1L, 3L, new CurrentActor(8L, false)));
    }

    @Test
    void ownerCanUpdateAndDeleteDraftVersion() {
        ToolVersion version = new ToolVersion(tool, "1.0.0", "Initial");
        ReflectionTestUtils.setField(version, "id", 3L);
        when(toolRepository.findForUpdateById(1L)).thenReturn(Optional.of(tool));
        when(versionRepository.findByIdAndToolId(3L, 1L)).thenReturn(Optional.of(version));
        CurrentActor owner = new CurrentActor(7L, false);

        var response = service.update(1L, 3L, new ToolVersionRequest("1.0.1", "Fix"), owner);
        service.delete(1L, 3L, owner);

        assertEquals("1.0.1", response.version());
        assertEquals("Fix", version.getReleaseNotes());
        verify(versionRepository).delete(version);
    }

    @Test
    void versionFromAnotherToolIsNotAccessible() {
        when(toolRepository.findForUpdateById(1L)).thenReturn(Optional.of(tool));

        assertThrows(ResourceNotFoundException.class,
                () -> service.delete(1L, 3L, new CurrentActor(7L, false)));
    }
}
