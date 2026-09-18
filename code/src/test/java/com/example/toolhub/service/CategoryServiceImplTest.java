package com.example.toolhub.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.toolhub.domain.entity.Category;
import com.example.toolhub.dto.request.CategoryRequest;
import com.example.toolhub.exception.CatalogConflictException;
import com.example.toolhub.mapper.CategoryMapper;
import com.example.toolhub.repository.CategoryRepository;
import com.example.toolhub.repository.ToolRepository;
import com.example.toolhub.service.impl.CategoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {
    @Mock private CategoryRepository categoryRepository;
    @Mock private ToolRepository toolRepository;
    @Mock private CategoryMapper categoryMapper;
    private CategoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CategoryServiceImpl(categoryRepository, toolRepository, categoryMapper);
    }

    @Test
    void create_whenActorIsNotAdmin_throwsAccessDeniedBeforeDatabaseWork() {
        var request = new CategoryRequest("AI", "ai", null);

        assertThrows(AccessDeniedException.class, () -> service.create(request, false));
        verify(categoryRepository, never()).existsByNameIgnoreCase("AI");
    }

    @Test
    void create_whenSlugExists_throwsConflict() {
        var request = new CategoryRequest("AI", "ai", null);
        when(categoryRepository.existsByNameIgnoreCase("AI")).thenReturn(false);
        when(categoryRepository.existsBySlug("ai")).thenReturn(true);

        assertThrows(CatalogConflictException.class, () -> service.create(request, true));
    }

    @Test
    void delete_whenCategoryIsReferenced_throwsConflict() {
        Category category = new Category("AI", "ai", null);
        when(categoryRepository.findById(1L)).thenReturn(java.util.Optional.of(category));
        when(toolRepository.existsByCategoryId(1L)).thenReturn(true);

        assertThrows(CatalogConflictException.class, () -> service.delete(1L, true));
    }
}
