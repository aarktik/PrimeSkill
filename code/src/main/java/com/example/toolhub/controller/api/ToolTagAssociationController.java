package com.example.toolhub.controller.api;

import com.example.toolhub.dto.response.TagResponse;
import com.example.toolhub.security.CurrentActor;
import com.example.toolhub.security.CurrentActorProvider;
import com.example.toolhub.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tools/{toolId}/tags")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Tool tags")
public class ToolTagAssociationController {
    private final TagService tagService;
    private final CurrentActorProvider currentActorProvider;

    public ToolTagAssociationController(TagService tagService, CurrentActorProvider currentActorProvider) {
        this.tagService = tagService;
        this.currentActorProvider = currentActorProvider;
    }

    @GetMapping
    @Operation(summary = "List tags assigned to a tool")
    public ResponseEntity<List<TagResponse>> findTagsOfTool(@PathVariable Long toolId) {
        return ResponseEntity.ok(tagService.findTagsOfTool(toolId));
    }

    @PostMapping("/{tagId}")
    @Operation(summary = "Assign a tag to an owned tool")
    public ResponseEntity<Void> assign(@PathVariable Long toolId, @PathVariable Long tagId) {
        CurrentActor actor = currentActorProvider.requireActor();
        tagService.assignTag(toolId, tagId, actor.id(), actor.admin());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{tagId}")
    @Operation(summary = "Remove a tag from an owned tool")
    public ResponseEntity<Void> unassign(@PathVariable Long toolId, @PathVariable Long tagId) {
        CurrentActor actor = currentActorProvider.requireActor();
        tagService.unassignTag(toolId, tagId, actor.id(), actor.admin());
        return ResponseEntity.noContent().build();
    }
}
