package com.example.toolhub.service.impl;

import com.example.toolhub.domain.entity.Category;
import com.example.toolhub.dto.request.CategoryRequest;
import com.example.toolhub.dto.response.CategoryResponse;
import com.example.toolhub.exception.CatalogConflictException;
import com.example.toolhub.exception.ResourceNotFoundException;
import com.example.toolhub.mapper.CategoryMapper;
import com.example.toolhub.repository.CategoryRepository;
import com.example.toolhub.repository.ToolRepository;
import com.example.toolhub.service.CategoryService;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryServiceImpl implements CategoryService {
    private final CategoryRepository categoryRepository;
    private final ToolRepository toolRepository;
    private final CategoryMapper categoryMapper;

    public CategoryServiceImpl(CategoryRepository categoryRepository,
                               ToolRepository toolRepository,
                               CategoryMapper categoryMapper) {
        this.categoryRepository = categoryRepository;
        this.toolRepository = toolRepository;
        this.categoryMapper = categoryMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll().stream().map(categoryMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse findById(Long id) {
        return categoryMapper.toResponse(findCategory(id));
    }

    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest request, boolean actorIsAdmin) {
        assertAdmin(actorIsAdmin);
        ensureUnique(request, null);
        return categoryMapper.toResponse(categoryRepository.save(categoryMapper.toEntity(request)));
    }

    @Override
    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request, boolean actorIsAdmin) {
        assertAdmin(actorIsAdmin);
        Category category = findCategory(id);
        ensureUnique(request, id);
        categoryMapper.updateEntity(category, request);
        return categoryMapper.toResponse(category);
    }

    @Override
    @Transactional
    public void delete(Long id, boolean actorIsAdmin) {
        assertAdmin(actorIsAdmin);
        Category category = findCategory(id);
        if (toolRepository.existsByCategoryId(id)) {
            throw new CatalogConflictException("Category is still referenced by a tool");
        }
        categoryRepository.delete(category);
    }

    private Category findCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
    }

    private void ensureUnique(CategoryRequest request, Long currentId) {
        boolean duplicateName = currentId == null
                ? categoryRepository.existsByNameIgnoreCase(request.name())
                : categoryRepository.existsByNameIgnoreCaseAndIdNot(request.name(), currentId);
        boolean duplicateSlug = currentId == null
                ? categoryRepository.existsBySlug(request.slug())
                : categoryRepository.existsBySlugAndIdNot(request.slug(), currentId);
        if (duplicateName || duplicateSlug) {
            throw new CatalogConflictException("Category name or slug already exists");
        }
    }

    private void assertAdmin(boolean actorIsAdmin) {
        if (!actorIsAdmin) {
            throw new AccessDeniedException("Administrator role is required");
        }
    }
}
