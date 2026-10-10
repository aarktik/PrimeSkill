package com.example.toolhub.controller.web;

import com.example.toolhub.dto.request.CreateReviewRequest;
import com.example.toolhub.dto.request.UpdateReviewRequest;
import com.example.toolhub.dto.response.ToolResponse;
import com.example.toolhub.exception.CatalogConflictException;
import com.example.toolhub.exception.DuplicateResourceException;
import com.example.toolhub.security.CurrentActor;
import com.example.toolhub.security.CurrentActorProvider;
import com.example.toolhub.service.ReviewService;
import com.example.toolhub.service.ReviewSummaryService;
import com.example.toolhub.service.ToolService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ReviewWebController {

    private static final int PAGE_SIZE = 20;

    private final ReviewService reviewService;
    private final ToolService toolService;
    private final CurrentActorProvider currentActorProvider;
    private final ReviewSummaryService reviewSummaryService;
    private final com.example.toolhub.service.TagService tagService;

    public ReviewWebController(ReviewService reviewService, ToolService toolService,
                               CurrentActorProvider currentActorProvider,
                               ReviewSummaryService reviewSummaryService, com.example.toolhub.service.TagService tagService) {
        this.reviewService = reviewService;
        this.toolService = toolService;
        this.currentActorProvider = currentActorProvider;
        this.reviewSummaryService = reviewSummaryService;
        this.tagService = tagService;
    }

    @GetMapping("/my/reviews")
    public String myReviews(@RequestParam(defaultValue = "0") int page, Model model) {
        CurrentActor actor = currentActorProvider.requireActor();
        var reviews = reviewService.listAuthoredBy(actor.id(), PageRequest.of(Math.max(page, 0), PAGE_SIZE));
        model.addAttribute("reviews", reviews.getContent());
        model.addAttribute("reviewPage", reviews);
        model.addAttribute("pageTitle", "My reviews");
        model.addAttribute("activeNav", "my-reviews");
        return "reviews/mine";
    }

    @PostMapping("/tools/{toolId}/reviews")
    public String create(@PathVariable Long toolId,
                         @Valid @ModelAttribute("createReviewRequest") CreateReviewRequest request,
                         BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        CurrentActor actor = currentActorProvider.requireActor();
        ToolResponse tool = visibleTool(toolId, actor);
        if (bindingResult.hasErrors()) {
            model.addAttribute("tool", tool);
            model.addAttribute("pageTitle", tool.getName());
            model.addAttribute("activeNav", "explore");
            // Reuse the existing detail view and show only safe validation messages.
            return showDetailWithErrors(tool, actor, request, bindingResult, model);
        }
        try {
            reviewService.create(toolId, request, actor.id(), actor.admin());
        } catch (CatalogConflictException | DuplicateResourceException | AccessDeniedException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            return "redirect:/tools/" + tool.getSlug();
        }
        redirectAttributes.addFlashAttribute("successMessage", "Review published.");
        return "redirect:/tools/" + tool.getSlug();
    }

    @PostMapping("/tools/{toolId}/reviews/{reviewId}")
    public String update(@PathVariable Long toolId, @PathVariable Long reviewId,
                         @RequestParam(defaultValue = "0") int page,
                         @Valid @ModelAttribute("updateReviewRequest") UpdateReviewRequest request,
                         BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        CurrentActor actor = currentActorProvider.requireActor();
        ToolResponse tool = visibleTool(toolId, actor);
        if (bindingResult.hasErrors()) {
            modelForReviewValidation(tool, actor, model, request, reviewId, bindingResult, page);
            return "tools/detail";
        }
        try {
            reviewService.update(toolId, reviewId, request, actor.id(), actor.admin());
        } catch (CatalogConflictException | DuplicateResourceException | AccessDeniedException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            return "redirect:/tools/" + tool.getSlug() + "?page=" + Math.max(page, 0) + "#your-review";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Review updated.");
        return "redirect:/tools/" + tool.getSlug() + "?page=" + Math.max(page, 0) + "#your-review";
    }

    @PostMapping("/tools/{toolId}/reviews/{reviewId}/delete")
    public String delete(@PathVariable Long toolId, @PathVariable Long reviewId,
                         @RequestParam(defaultValue = "0") int page,
                         RedirectAttributes redirectAttributes) {
        CurrentActor actor = currentActorProvider.requireActor();
        try {
            reviewService.delete(toolId, reviewId, actor.id(), actor.admin());
            redirectAttributes.addFlashAttribute("successMessage", "Review deleted.");
        } catch (AccessDeniedException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        try {
            var tool = visibleTool(toolId, actor);
            var remaining = reviewService.listForTool(toolId, actor.id(), actor.admin(), PageRequest.of(0, PAGE_SIZE));
            int lastPage = Math.max(0, remaining.getTotalPages() - 1);
            return "redirect:/tools/" + tool.getSlug() + "?page=" + Math.min(Math.max(page, 0), lastPage) + "#reviews-heading";
        } catch (com.example.toolhub.exception.ResourceNotFoundException exception) { return "redirect:/my/reviews"; }
    }

    @PostMapping("/my/reviews/{toolId}/{reviewId}/delete")
    public String deleteFromMine(@PathVariable Long toolId, @PathVariable Long reviewId,
                                 RedirectAttributes redirectAttributes) {
        CurrentActor actor = currentActorProvider.requireActor();
        try {
            reviewService.delete(toolId, reviewId, actor.id(), actor.admin());
            redirectAttributes.addFlashAttribute("successMessage", "Review deleted.");
        } catch (AccessDeniedException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/my/reviews";
    }

    private String showDetailWithErrors(ToolResponse tool, CurrentActor actor, CreateReviewRequest request,
                                        BindingResult bindingResult, Model model) {
        model.addAttribute("tool", tool);
        model.addAttribute("pageTitle", tool.getName());
        model.addAttribute("activeNav", "explore");
        populateReviewDetail(tool, actor, model, 0);
        model.addAttribute("createReviewRequest", request);
        model.addAttribute(BindingResult.MODEL_KEY_PREFIX + "createReviewRequest", bindingResult);
        model.addAttribute("reviewErrors", bindingResult.getFieldErrors());
        return "tools/detail";
    }

    private void modelForReviewValidation(ToolResponse tool, CurrentActor actor, Model model,
                                         UpdateReviewRequest request, Long reviewId,
                                         BindingResult bindingResult, int page) {
        model.addAttribute("tool", tool);
        model.addAttribute("pageTitle", tool.getName());
        model.addAttribute("activeNav", "explore");
        populateReviewDetail(tool, actor, model, page);
        model.addAttribute("updateReviewRequest", request);
        model.addAttribute("updateReviewId", reviewId);
        model.addAttribute("updateReviewErrors", bindingResult.getFieldErrors());
    }

    private void populateReviewDetail(ToolResponse tool, CurrentActor actor, Model model, int page) {
        var reviews = reviewService.listForTool(tool.getId(), actor.id(), actor.admin(),
                PageRequest.of(Math.max(page, 0), PAGE_SIZE));
        model.addAttribute("reviews", reviews.getContent());
        model.addAttribute("reviewPage", reviews);
        model.addAttribute("reviewSummary", reviewSummaryService.summarizeByToolIds(java.util.List.of(tool.getId()))
                .get(tool.getId()));
        model.addAttribute("myReview", reviewService.findMineForTool(tool.getId(), actor.id()));
        model.addAttribute("currentActorId", actor.id());
        model.addAttribute("currentActorAdmin", actor.admin());
        model.addAttribute("toolTags", tagService.findTagsOfTool(tool.getId(), actor.id(), actor.admin()));
        if (!model.containsAttribute("createReviewRequest")) {
            model.addAttribute("createReviewRequest", new CreateReviewRequest(null, null));
        }
    }

    private ToolResponse visibleTool(Long toolId, CurrentActor actor) {
        return toolService.getById(toolId, actor.id(), actor.admin());
    }
}
