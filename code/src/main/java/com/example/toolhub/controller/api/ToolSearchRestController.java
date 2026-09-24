package com.example.toolhub.controller.api;

import com.example.toolhub.dto.response.PagedResponse;
import com.example.toolhub.dto.response.ToolResponse;
import com.example.toolhub.service.ToolSearchService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tools")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Tool search")
public class ToolSearchRestController {
    private final ToolSearchService toolSearchService;

    public ToolSearchRestController(ToolSearchService toolSearchService) {
        this.toolSearchService = toolSearchService;
    }

    @GetMapping
    @Operation(summary = "Search published tools (provisional contract: category=ID, tags=slug ANY)")
    public ResponseEntity<PagedResponse<ToolResponse>> search(
            @RequestParam(name = "q", required = false) String keyword,
            @RequestParam(name = "category", required = false) Long categoryId,
            @RequestParam(name = "tags", required = false) List<String> tagSlugs,
            @RequestParam(name = "sort", defaultValue = "newest") String sort,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        var result = toolSearchService.search(keyword, categoryId, tagSlugs, sort, page, size);
        return ResponseEntity.ok(PagedResponse.from(result));
    }
}
