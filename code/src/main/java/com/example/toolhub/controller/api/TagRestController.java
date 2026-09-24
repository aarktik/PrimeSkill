package com.example.toolhub.controller.api;

import com.example.toolhub.dto.request.TagRequest;
import com.example.toolhub.dto.response.TagResponse;
import com.example.toolhub.security.CurrentActorProvider;
import com.example.toolhub.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
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
@io.swagger.v3.oas.annotations.tags.Tag(name = "Tags")
public class TagRestController {
    private final TagService tagService;
    private final CurrentActorProvider currentActorProvider;

    public TagRestController(TagService tagService, CurrentActorProvider currentActorProvider) {
        this.tagService = tagService;
        this.currentActorProvider = currentActorProvider;
    }

    @GetMapping("/tags")
    @Operation(summary = "List all tags")
    public ResponseEntity<List<TagResponse>> findAll() {
        return ResponseEntity.ok(tagService.findAll());
    }

    @PostMapping("/admin/tags")
    @Operation(summary = "Create a tag")
    public ResponseEntity<TagResponse> create(@Valid @RequestBody TagRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tagService.create(request, currentActorProvider.requireActor().admin()));
    }

    @PutMapping("/admin/tags/{id}")
    @Operation(summary = "Update a tag")
    public ResponseEntity<TagResponse> update(@PathVariable Long id,
                                              @Valid @RequestBody TagRequest request) {
        return ResponseEntity.ok(tagService.update(id, request, currentActorProvider.requireActor().admin()));
    }

    @DeleteMapping("/admin/tags/{id}")
    @Operation(summary = "Delete a tag")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        tagService.delete(id, currentActorProvider.requireActor().admin());
        return ResponseEntity.noContent().build();
    }
}
