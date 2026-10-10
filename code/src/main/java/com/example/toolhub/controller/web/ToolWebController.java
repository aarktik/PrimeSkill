package com.example.toolhub.controller.web;

import com.example.toolhub.dto.request.CreateToolRequest;
import com.example.toolhub.dto.request.UpdateToolRequest;
import com.example.toolhub.dto.response.ToolResponse;
import com.example.toolhub.dto.request.CreateReviewRequest;
import com.example.toolhub.dto.response.ReviewSummary;
import com.example.toolhub.domain.enums.ToolStatus;
import com.example.toolhub.exception.InvalidStateTransitionException;
import com.example.toolhub.security.CurrentActor;
import com.example.toolhub.security.CurrentActorProvider;
import com.example.toolhub.service.CategoryService;
import com.example.toolhub.service.ReviewService;
import com.example.toolhub.service.ReviewSummaryService;
import com.example.toolhub.service.ToolService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.validation.Valid;

@Controller
public class ToolWebController {
    private static final int DASHBOARD_PAGE_SIZE = 20;

    private final ToolService toolService;
    private final CategoryService categoryService;
    private final CurrentActorProvider currentActorProvider;
    private final ReviewService reviewService;
    private final ReviewSummaryService reviewSummaryService;
    private final com.example.toolhub.service.TagService tagService;

    public ToolWebController(ToolService toolService, CategoryService categoryService,
                             CurrentActorProvider currentActorProvider, ReviewService reviewService,
                             ReviewSummaryService reviewSummaryService, com.example.toolhub.service.TagService tagService) {
        this.toolService = toolService;
        this.categoryService = categoryService;
        this.currentActorProvider = currentActorProvider;
        this.reviewService = reviewService;
        this.reviewSummaryService = reviewSummaryService;
        this.tagService = tagService;
    }

    @GetMapping("/tools/{idOrSlug}")
    public String detail(@PathVariable String idOrSlug,
                         @RequestParam(defaultValue = "0") int page, Model model) {
        CurrentActor actor = currentActorProvider.currentActor();
        ToolResponse tool = toolService.getDetailByIdOrSlug(idOrSlug, actor.id(), actor.admin());
        model.addAttribute("tool", tool);
        model.addAttribute("pageTitle", tool.getName());
        model.addAttribute("activeNav", "explore");
        model.addAttribute("toolTags", tagService.findTagsOfTool(tool.getId(), actor.id(), actor.admin()));
        populateReviews(model, tool, actor.id(), actor.admin(), page);
        return "tools/detail";
    }

    private void populateReviews(Model model, ToolResponse tool, Long actorId, boolean admin, int page) {
        var reviews = reviewService.listForTool(tool.getId(), actorId, admin,
                PageRequest.of(Math.max(page, 0), 20));
        ReviewSummary summary = reviewSummaryService.summarizeByToolIds(java.util.List.of(tool.getId()))
                .get(tool.getId());
        model.addAttribute("reviews", reviews.getContent());
        model.addAttribute("reviewPage", reviews);
        model.addAttribute("reviewSummary", summary);
        model.addAttribute("myReview", reviewService.findMineForTool(tool.getId(), actorId));
        model.addAttribute("currentActorId", actorId);
        model.addAttribute("currentActorAdmin", admin);
        model.addAttribute("createReviewRequest", new CreateReviewRequest(null, null));
    }

    @GetMapping("/dashboard/tools")
    public String dashboard(@RequestParam(defaultValue = "0") int page, Model model) {
        CurrentActor actor = currentActorProvider.requireActor();
        int safePage = Math.max(page, 0);
        var tools = toolService.listOwnedBy(actor.id(),
                PageRequest.of(safePage, DASHBOARD_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "updatedAt")));
        model.addAttribute("tools", tools.getContent());
        model.addAttribute("toolPage", tools);
        model.addAttribute("pageTitle", "My tools");
        model.addAttribute("activeNav", "my-tools");
        return "tools/dashboard";
    }

    @GetMapping("/dashboard/tools/new")
    public String createForm(Model model) {
        currentActorProvider.requireActor();
        model.addAttribute("toolRequest", new CreateToolRequest(null, null, null, null, null, null));
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("pageTitle", "New tool");
        model.addAttribute("activeNav", "my-tools");
        model.addAttribute("formAction", "/dashboard/tools");
        return "tools/form";
    }

    @PostMapping("/dashboard/tools")
    public String create(@Valid @ModelAttribute("toolRequest") CreateToolRequest request,
                         BindingResult bindingResult, Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            populateCreateForm(model);
            return "tools/form";
        }
        ToolResponse response;
        try { response = toolService.create(request, currentActorProvider.requireActor().id()); }
        catch (com.example.toolhub.exception.CatalogConflictException exception) {
            bindingResult.rejectValue("slug", "duplicate", "This slug is already in use.");
            populateCreateForm(model); return "tools/form";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Tool draft created.");
        return "redirect:/tools/" + response.getSlug();
    }

    @GetMapping("/dashboard/tools/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        CurrentActor actor = currentActorProvider.requireActor();
        ToolResponse tool = editableTool(id, actor);
        model.addAttribute("tool", tool);
        model.addAttribute("toolRequest", new UpdateToolRequest(tool.getName(), tool.getSlug(),
                tool.getShortDescription(), tool.getDescription(), tool.getCategoryId(), tool.getRepositoryUrl()));
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("pageTitle", "Edit tool");
        model.addAttribute("activeNav", "my-tools");
        model.addAttribute("formAction", "/dashboard/tools/" + id);
        return "tools/form";
    }

    @PostMapping("/dashboard/tools/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("toolRequest") UpdateToolRequest request,
                         BindingResult bindingResult, Model model,
                         RedirectAttributes redirectAttributes) {
        CurrentActor actor = currentActorProvider.requireActor();
        if (bindingResult.hasErrors()) {
            model.addAttribute("tool", editableTool(id, actor));
            model.addAttribute("categories", categoryService.findAll());
            model.addAttribute("pageTitle", "Edit tool");
            model.addAttribute("activeNav", "my-tools");
            model.addAttribute("formAction", "/dashboard/tools/" + id);
            return "tools/form";
        }
        ToolResponse response;
        try { response = toolService.update(id, request, actor.id(), actor.admin()); }
        catch (com.example.toolhub.exception.CatalogConflictException exception) {
            bindingResult.rejectValue("slug", "duplicate", "This slug is already in use.");
            model.addAttribute("tool", editableTool(id, actor));
            model.addAttribute("categories", categoryService.findAll());
            model.addAttribute("pageTitle", "Edit tool"); model.addAttribute("activeNav", "my-tools");
            model.addAttribute("formAction", "/dashboard/tools/" + id); return "tools/form";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Changes saved.");
        return "redirect:/tools/" + response.getSlug();
    }

    @PostMapping("/dashboard/tools/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        CurrentActor actor = currentActorProvider.requireActor();
        toolService.delete(id, actor.id(), actor.admin());
        redirectAttributes.addFlashAttribute("successMessage", "Tool deleted.");
        return "redirect:/dashboard/tools";
    }

    private void populateCreateForm(Model model) {
        model.addAttribute("activeNav", "my-tools");
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("pageTitle", "New tool");
        model.addAttribute("formAction", "/dashboard/tools");
    }

    private ToolResponse editableTool(Long id, CurrentActor actor) {
        ToolResponse tool = toolService.getByIdOrSlug(String.valueOf(id), actor.id(), actor.admin());
        if (!actor.admin() && !actor.id().equals(tool.getOwnerId())) {
            throw new AccessDeniedException("You do not own this tool");
        }
        if (tool.getStatus() != ToolStatus.DRAFT) {
            throw new InvalidStateTransitionException("Tool metadata can only be edited in draft status");
        }
        return tool;
    }

    private String editError(Model model, jakarta.servlet.http.HttpServletRequest request) {
        new WebLayoutAdvice(currentActorProvider).layout(model, request);
        return "tools/edit-error";
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String invalidEditState(Model model, jakarta.servlet.http.HttpServletRequest request) {
        model.addAttribute("message", "Only draft tools can be edited. Return to your workspace to check the latest status.");
        return editError(model, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String deniedEdit(Model model, jakarta.servlet.http.HttpServletRequest request) {
        model.addAttribute("message", "You do not have permission to edit this tool.");
        return editError(model, request);
    }

    @ExceptionHandler(org.springframework.dao.PessimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public String concurrentEdit(Model model, jakarta.servlet.http.HttpServletRequest request) {
        model.addAttribute("message", "This tool is being updated. Check its latest state before trying again.");
        return editError(model, request);
    }
}
