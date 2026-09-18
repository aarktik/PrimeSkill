package com.example.toolhub.controller.api;

import com.example.toolhub.dto.request.CreateToolRequest;
import com.example.toolhub.dto.request.UpdateToolRequest;
import com.example.toolhub.dto.response.ToolResponse;
import com.example.toolhub.security.CurrentActor;
import com.example.toolhub.security.CurrentActorProvider;
import com.example.toolhub.service.ToolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
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
@RequestMapping("/api/v1/tools")
@Tag(name = "Tools")
public class ToolRestController {
    private final ToolService toolService;
    private final CurrentActorProvider currentActorProvider;

    public ToolRestController(ToolService toolService, CurrentActorProvider currentActorProvider) {
        this.toolService = toolService;
        this.currentActorProvider = currentActorProvider;
    }

    @GetMapping("/{idOrSlug}")
    @Operation(summary = "Get a published tool or an owned tool")
    public ResponseEntity<ToolResponse> getByIdOrSlug(@PathVariable String idOrSlug) {
        CurrentActor actor = currentActorProvider.currentActor();
        return ResponseEntity.ok(toolService.getByIdOrSlug(idOrSlug, actor.id(), actor.admin()));
    }

    @PostMapping
    @Operation(summary = "Create a draft tool")
    public ResponseEntity<ToolResponse> create(@Valid @RequestBody CreateToolRequest request) {
        ToolResponse response = toolService.create(request, currentActorProvider.requireActor().id());
        return ResponseEntity.created(URI.create("/api/v1/tools/" + response.getId())).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an owned tool")
    public ResponseEntity<ToolResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody UpdateToolRequest request) {
        CurrentActor actor = currentActorProvider.requireActor();
        return ResponseEntity.ok(toolService.update(id, request, actor.id(), actor.admin()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an owned tool")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        CurrentActor actor = currentActorProvider.requireActor();
        toolService.delete(id, actor.id(), actor.admin());
        return ResponseEntity.noContent().build();
    }
}
