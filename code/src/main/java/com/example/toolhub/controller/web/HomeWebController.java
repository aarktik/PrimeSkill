package com.example.toolhub.controller.web;

import com.example.toolhub.dto.response.ToolResponse;
import com.example.toolhub.service.CategoryService;
import com.example.toolhub.service.ReviewSummaryService;
import com.example.toolhub.service.ToolSearchService;
import java.util.stream.Stream;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeWebController {
    private final ToolSearchService search;
    private final CategoryService categories;
    private final ReviewSummaryService summaries;

    public HomeWebController(ToolSearchService search, CategoryService categories, ReviewSummaryService summaries) {
        this.search = search; this.categories = categories; this.summaries = summaries;
    }

    @GetMapping("/")
    public String home(Model model) {
        var popular = search.search(null, null, null, "popular", 0, 6).getContent();
        var recent = search.search(null, null, null, "newest", 0, 6).getContent();
        var ids = Stream.concat(popular.stream(), recent.stream()).map(ToolResponse::getId).distinct().toList();
        model.addAttribute("popularTools", popular);
        model.addAttribute("recentTools", recent);
        model.addAttribute("reviewSummaries", ids.isEmpty() ? java.util.Map.of() : summaries.summarizeByToolIds(ids));
        model.addAttribute("categories", categories.findAll());
        model.addAttribute("pageTitle", "Tools for a better AI workflow");
        model.addAttribute("activeNav", "home");
        return "home";
    }
}
