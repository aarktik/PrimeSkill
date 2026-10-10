package com.example.toolhub.controller.web;

import com.example.toolhub.domain.enums.PublishingAction;
import com.example.toolhub.domain.enums.ToolStatus;
import com.example.toolhub.dto.request.ToolVersionRequest;
import com.example.toolhub.dto.response.ToolResponse;
import com.example.toolhub.dto.response.ToolVersionResponse;
import com.example.toolhub.exception.InvalidStateTransitionException;
import com.example.toolhub.exception.CatalogConflictException;
import com.example.toolhub.security.CurrentActor;
import com.example.toolhub.security.CurrentActorProvider;
import com.example.toolhub.service.PublishingService;
import com.example.toolhub.service.ToolService;
import com.example.toolhub.service.ToolVersionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RoleEWebController {
    private final ToolService toolService;
    private final ToolVersionService versionService;
    private final PublishingService publishingService;
    private final CurrentActorProvider actorProvider;

    public RoleEWebController(ToolService toolService, ToolVersionService versionService,
                              PublishingService publishingService, CurrentActorProvider actorProvider) {
        this.toolService = toolService;
        this.versionService = versionService;
        this.publishingService = publishingService;
        this.actorProvider = actorProvider;
    }

    @GetMapping("/tools/{idOrSlug}/versions")
    public String publicVersions(@PathVariable String idOrSlug, Model model) {
        CurrentActor actor = actorProvider.currentActor();
        ToolResponse tool = toolService.getByIdOrSlug(idOrSlug, actor.id(), actor.admin());
        model.addAttribute("tool", tool);
        model.addAttribute("versions", versionService.list(tool.getId(), actor));
        model.addAttribute("pageTitle", "Versions of " + tool.getName());
        model.addAttribute("activeNav", "explore");
        return "versions/public";
    }

    @GetMapping("/dashboard/tools/{toolId}/versions")
    public String versions(@PathVariable Long toolId, Model model) {
        CurrentActor actor = actorProvider.requireActor();
        ToolResponse tool = ownedTool(toolId, actor);
        model.addAttribute("tool", tool);
        model.addAttribute("versions", versionService.list(toolId, actor));
        model.addAttribute("pageTitle", "Versions of " + tool.getName());
        model.addAttribute("activeNav", "my-tools");
        return "versions/list";
    }

    @GetMapping("/dashboard/tools/{toolId}/versions/new")
    public String createVersionForm(@PathVariable Long toolId, Model model) {
        CurrentActor actor = actorProvider.requireActor();
        ToolResponse tool = ownedTool(toolId, actor);
        requireDraft(tool);
        populateVersionForm(model, tool, new ToolVersionRequest(null, null), null);
        return "versions/form";
    }

    @PostMapping("/dashboard/tools/{toolId}/versions")
    public String createVersion(@PathVariable Long toolId,
                                @Valid @ModelAttribute("versionRequest") ToolVersionRequest request,
                                BindingResult errors, Model model, RedirectAttributes redirect) {
        CurrentActor actor = actorProvider.requireActor();
        ToolResponse tool = ownedTool(toolId, actor);
        requireDraft(tool);
        if (errors.hasErrors()) {
            populateVersionForm(model, tool, request, null);
            return "versions/form";
        }
        try {
            versionService.create(toolId, request, actor);
        } catch (CatalogConflictException exception) {
            errors.rejectValue("version", "duplicate", "This version already exists. Choose another version.");
            populateVersionForm(model, tool, request, null);
            return "versions/form";
        }
        redirect.addFlashAttribute("successMessage", "Version created.");
        return versionsRedirect(toolId);
    }

    @GetMapping("/dashboard/tools/{toolId}/versions/{versionId}/edit")
    public String editVersionForm(@PathVariable Long toolId, @PathVariable Long versionId, Model model) {
        CurrentActor actor = actorProvider.requireActor();
        ToolResponse tool = ownedTool(toolId, actor);
        requireDraft(tool);
        ToolVersionResponse version = versionService.get(toolId, versionId, actor);
        populateVersionForm(model, tool, new ToolVersionRequest(version.version(), version.releaseNotes()), versionId);
        return "versions/form";
    }

    @PostMapping("/dashboard/tools/{toolId}/versions/{versionId}")
    public String updateVersion(@PathVariable Long toolId, @PathVariable Long versionId,
                                @Valid @ModelAttribute("versionRequest") ToolVersionRequest request,
                                BindingResult errors, Model model, RedirectAttributes redirect) {
        CurrentActor actor = actorProvider.requireActor();
        ToolResponse tool = ownedTool(toolId, actor);
        requireDraft(tool);
        if (errors.hasErrors()) {
            populateVersionForm(model, tool, request, versionId);
            return "versions/form";
        }
        try {
            versionService.update(toolId, versionId, request, actor);
        } catch (CatalogConflictException exception) {
            errors.rejectValue("version", "duplicate", "This version already exists. Choose another version.");
            populateVersionForm(model, tool, request, versionId);
            return "versions/form";
        }
        redirect.addFlashAttribute("successMessage", "Version saved.");
        return versionsRedirect(toolId);
    }

    @PostMapping("/dashboard/tools/{toolId}/versions/{versionId}/delete")
    public String deleteVersion(@PathVariable Long toolId, @PathVariable Long versionId,
                                RedirectAttributes redirect) {
        CurrentActor actor = actorProvider.requireActor();
        ownedTool(toolId, actor);
        versionService.delete(toolId, versionId, actor);
        redirect.addFlashAttribute("successMessage", "Version deleted.");
        return versionsRedirect(toolId);
    }

    @PostMapping("/dashboard/tools/{toolId}/submit")
    public String submit(@PathVariable Long toolId, RedirectAttributes redirect) {
        return ownerTransition(toolId, PublishingAction.SUBMIT, "Tool submitted for review.", redirect);
    }

    @PostMapping("/dashboard/tools/{toolId}/deprecate")
    public String deprecate(@PathVariable Long toolId, RedirectAttributes redirect) {
        var actor = actorProvider.requireActor();
        publishingService.transition(toolId, PublishingAction.DEPRECATE, actor);
        redirect.addFlashAttribute("successMessage", "Tool deprecated.");
        return actor.admin() ? "redirect:/tools/" + toolId : versionsRedirect(toolId);
    }

    @PostMapping("/dashboard/tools/{toolId}/restore")
    public String restore(@PathVariable Long toolId, RedirectAttributes redirect) {
        return ownerTransition(toolId, PublishingAction.RESTORE, "Tool restored to draft.", redirect);
    }

    @GetMapping("/admin/tools")
    public String moderation(@RequestParam(defaultValue = "0") int page, Model model) {
        CurrentActor actor = actorProvider.requireActor();
        var pending = publishingService.listPending(PageRequest.of(Math.max(page, 0), 20,
                Sort.by(Sort.Direction.ASC, "updatedAt").and(Sort.by("id"))), actor);
        model.addAttribute("tools", pending.getContent());
        model.addAttribute("toolPage", pending);
        model.addAttribute("pageTitle", "Review submissions");
        model.addAttribute("activeNav", "moderation");
        return "admin/moderation";
    }

    @PostMapping("/admin/tools/{toolId}/approve")
    public String approve(@PathVariable Long toolId, @RequestParam String expectedReviewRevision, RedirectAttributes redirect) {
        return adminTransition(toolId, PublishingAction.APPROVE, parseRevision(expectedReviewRevision), "Tool approved.", redirect);
    }

    @PostMapping("/admin/tools/{toolId}/reject")
    public String reject(@PathVariable Long toolId, @RequestParam String expectedReviewRevision, RedirectAttributes redirect) {
        return adminTransition(toolId, PublishingAction.REJECT, parseRevision(expectedReviewRevision), "Submission returned to draft.", redirect);
    }

    private String ownerTransition(Long toolId, PublishingAction action, String message,
                                   RedirectAttributes redirect) {
        CurrentActor actor = actorProvider.requireActor();
        ownedTool(toolId, actor);
        publishingService.transition(toolId, action, actor);
        redirect.addFlashAttribute("successMessage", message);
        return versionsRedirect(toolId);
    }

    private String adminTransition(Long toolId, PublishingAction action, long revision, String message,
                                   RedirectAttributes redirect) {
        publishingService.decide(toolId, action, revision, actorProvider.requireActor());
        redirect.addFlashAttribute("successMessage", message);
        return "redirect:/admin/tools";
    }

    private long parseRevision(String value) {
        try {
            if (!value.matches("[0-9]+")) throw new NumberFormatException();
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new com.example.toolhub.exception.InvalidRequestParameterException("Invalid submission revision");
        }
    }

    private ToolResponse ownedTool(Long toolId, CurrentActor actor) {
        ToolResponse tool = toolService.getById(toolId, actor.id(), actor.admin());
        if (!actor.id().equals(tool.getOwnerId())) {
            throw new AccessDeniedException("You do not own this tool");
        }
        return tool;
    }

    private void requireDraft(ToolResponse tool) {
        if (tool.getStatus() != ToolStatus.DRAFT) {
            throw new InvalidStateTransitionException("Versions can only be edited while the tool is a draft");
        }
    }

    private void populateVersionForm(Model model, ToolResponse tool, ToolVersionRequest request, Long versionId) {
        model.addAttribute("tool", tool);
        model.addAttribute("versionRequest", request);
        model.addAttribute("versionId", versionId);
        model.addAttribute("formAction", versionId == null
                ? "/dashboard/tools/" + tool.getId() + "/versions"
                : "/dashboard/tools/" + tool.getId() + "/versions/" + versionId);
        model.addAttribute("pageTitle", versionId == null ? "New version" : "Edit version");
        model.addAttribute("activeNav", "my-tools");
    }

    private String versionsRedirect(Long toolId) {
        return "redirect:/dashboard/tools/" + toolId + "/versions";
    }
}
