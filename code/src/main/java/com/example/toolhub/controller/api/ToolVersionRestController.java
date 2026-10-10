package com.example.toolhub.controller.api;

import com.example.toolhub.dto.request.ToolVersionRequest;
import com.example.toolhub.dto.response.ToolVersionResponse;
import com.example.toolhub.security.CurrentActorProvider;
import com.example.toolhub.service.ToolVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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
@RequestMapping("/api/v1/tools/{toolId}/versions")
@Tag(name = "Tool versions")
public class ToolVersionRestController {
    private final ToolVersionService service;
    private final CurrentActorProvider currentActorProvider;

    public ToolVersionRestController(ToolVersionService service, CurrentActorProvider currentActorProvider) {
        this.service = service;
        this.currentActorProvider = currentActorProvider;
    }

    @GetMapping
    @Operation(summary = "List versions of a published or owned tool")
    public ResponseEntity<List<ToolVersionResponse>> list(@PathVariable Long toolId) {
        return ResponseEntity.ok(service.list(toolId, currentActorProvider.currentActor()));
    }

    @GetMapping("/{versionId}")
    @Operation(summary = "Get a version of a published or owned tool")
    public ResponseEntity<ToolVersionResponse> get(@PathVariable Long toolId, @PathVariable Long versionId) {
        return ResponseEntity.ok(service.get(toolId, versionId, currentActorProvider.currentActor()));
    }

    @PostMapping
    @Operation(summary = "Add a version to an owned draft")
    public ResponseEntity<ToolVersionResponse> create(@PathVariable Long toolId,
                                                       @Valid @RequestBody ToolVersionRequest request) {
        ToolVersionResponse response = service.create(toolId, request, currentActorProvider.requireActor());
        return ResponseEntity.created(URI.create("/api/v1/tools/" + toolId + "/versions/" + response.id()))
                .body(response);
    }

    @PutMapping("/{versionId}")
    @Operation(summary = "Update a version of an owned draft")
    public ResponseEntity<ToolVersionResponse> update(@PathVariable Long toolId, @PathVariable Long versionId,
                                                       @Valid @RequestBody ToolVersionRequest request) {
        return ResponseEntity.ok(service.update(toolId, versionId, request, currentActorProvider.requireActor()));
    }

    @DeleteMapping("/{versionId}")
    @Operation(summary = "Delete a version of an owned draft")
    public ResponseEntity<Void> delete(@PathVariable Long toolId, @PathVariable Long versionId) {
        service.delete(toolId, versionId, currentActorProvider.requireActor());
        return ResponseEntity.noContent().build();
    }
}
