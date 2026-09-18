package com.example.toolhub.controller.api;

import com.example.toolhub.dto.request.CategoryRequest;
import com.example.toolhub.dto.response.CategoryResponse;
import com.example.toolhub.security.CurrentActorProvider;
import com.example.toolhub.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Categories")
public class CategoryRestController {
    private final CategoryService categoryService;
    private final CurrentActorProvider currentActorProvider;

    public CategoryRestController(CategoryService categoryService, CurrentActorProvider currentActorProvider) {
        this.categoryService = categoryService;
        this.currentActorProvider = currentActorProvider;
    }

    @GetMapping("/categories")
    @Operation(summary = "List tool categories")
    public ResponseEntity<List<CategoryResponse>> findAll() {
        return ResponseEntity.ok(categoryService.findAll());
    }

    @PostMapping("/admin/categories")
    @Operation(summary = "Create a category")
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(categoryService.create(request, currentActorProvider.requireActor().admin()));
    }

    @PutMapping("/admin/categories/{id}")
    @Operation(summary = "Update a category")
    public ResponseEntity<CategoryResponse> update(@PathVariable Long id,
                                                    @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(categoryService.update(id, request, currentActorProvider.requireActor().admin()));
    }

    @DeleteMapping("/admin/categories/{id}")
    @Operation(summary = "Delete a category")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        categoryService.delete(id, currentActorProvider.requireActor().admin());
        return ResponseEntity.noContent().build();
    }
}
