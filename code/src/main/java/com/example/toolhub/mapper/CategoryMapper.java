package com.example.toolhub.mapper;

import com.example.toolhub.domain.entity.Category;
import com.example.toolhub.dto.request.CategoryRequest;
import com.example.toolhub.dto.response.CategoryResponse;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getSlug(), category.getDescription());
    }

    public Category toEntity(CategoryRequest request) {
        return new Category(request.name(), request.slug(), request.description());
    }

    public void updateEntity(Category category, CategoryRequest request) {
        category.setName(request.name());
        category.setSlug(request.slug());
        category.setDescription(request.description());
    }
}
