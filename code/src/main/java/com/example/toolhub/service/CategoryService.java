package com.example.toolhub.service;

import com.example.toolhub.dto.request.CategoryRequest;
import com.example.toolhub.dto.response.CategoryResponse;
import java.util.List;

public interface CategoryService {
    List<CategoryResponse> findAll();
    CategoryResponse findById(Long id);
    CategoryResponse create(CategoryRequest request, boolean actorIsAdmin);
    CategoryResponse update(Long id, CategoryRequest request, boolean actorIsAdmin);
    void delete(Long id, boolean actorIsAdmin);
}
