package com.example.toolhub.controller.web;

import com.example.toolhub.service.CategoryService;
import com.example.toolhub.service.TagService;
import com.example.toolhub.service.ToolSearchService;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ToolBrowseWebController {
    private static final int DEFAULT_SIZE = 20;

    private final ToolSearchService toolSearchService;
    private final CategoryService categoryService;
    private final TagService tagService;

    public ToolBrowseWebController(ToolSearchService toolSearchService,
                                   CategoryService categoryService,
                                   TagService tagService) {
        this.toolSearchService = toolSearchService;
        this.categoryService = categoryService;
        this.tagService = tagService;
    }

    @GetMapping("/tools")
    public String browse(
            @RequestParam(name = "q", required = false) String keyword,
            @RequestParam(name = "category", required = false) Long categoryId,
            @RequestParam(name = "tags", required = false) String tagsParam,
            @RequestParam(name = "sort", defaultValue = "newest") String sort,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            Model model) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, 100);
        List<String> tagSlugs = parseTags(tagsParam);
        var result = toolSearchService.search(keyword, categoryId, tagSlugs, sort, safePage, safeSize);

        model.addAttribute("tools", result.getContent());
        model.addAttribute("totalElements", result.getTotalElements());
        model.addAttribute("page", result.getNumber());
        model.addAttribute("size", result.getSize());
        model.addAttribute("totalPages", result.getTotalPages());
        model.addAttribute("first", result.isFirst());
        model.addAttribute("last", result.isLast());
        model.addAttribute("q", keyword == null ? "" : keyword);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("tags", tagsParam == null ? "" : tagsParam);
        model.addAttribute("sort", sort == null || sort.isBlank() ? "newest" : sort);
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("allTags", tagService.findAll());
        model.addAttribute("pageTitle", "สำรวจเครื่องมือ");
        model.addAttribute("activeNav", "explore");
        return "tools/list";
    }

    private List<String> parseTags(String tagsParam) {
        if (tagsParam == null || tagsParam.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tagsParam.split(","))
                .map(String::trim)
                .filter(slug -> !slug.isEmpty())
                .toList();
    }
}
