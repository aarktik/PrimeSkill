package com.example.toolhub.controller.web;

import com.example.toolhub.dto.response.ToolResponse;
import com.example.toolhub.exception.CatalogConflictException;
import com.example.toolhub.security.CurrentActor;
import com.example.toolhub.security.CurrentActorProvider;
import com.example.toolhub.service.TagService;
import com.example.toolhub.service.ToolService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ToolTagWebController {
    private final ToolService tools;
    private final TagService tags;
    private final CurrentActorProvider actors;
    public ToolTagWebController(ToolService tools, TagService tags, CurrentActorProvider actors) {
        this.tools = tools; this.tags = tags; this.actors = actors;
    }
    @GetMapping("/dashboard/tools/{id}/tags")
    public String show(@PathVariable Long id, Model model) {
        var actor = actors.requireActor(); var tool = authorizedTool(id, actor);
        var assigned = tags.findTagsOfTool(id, actor.id(), actor.admin());
        var assignedIds = assigned.stream().map(tag -> tag.id()).collect(java.util.stream.Collectors.toSet());
        model.addAttribute("tool", tool); model.addAttribute("assignedTags", assigned);
        model.addAttribute("availableTags", tags.findAll().stream().filter(tag -> !assignedIds.contains(tag.id())).toList());
        model.addAttribute("pageTitle", "Tags for " + tool.getName()); model.addAttribute("activeNav", "my-tools");
        return "tools/tags";
    }
    @PostMapping("/dashboard/tools/{id}/tags/assign")
    public String assign(@PathVariable Long id, @RequestParam Long tagId, RedirectAttributes redirect) {
        var actor = actors.requireActor();
        try { tags.assignTag(id, tagId, actor.id(), actor.admin()); redirect.addFlashAttribute("successMessage", "Tag added."); }
        catch (CatalogConflictException exception) { redirect.addFlashAttribute("errorMessage", "This tag is already assigned. The current list is shown below."); }
        return "redirect:/dashboard/tools/" + id + "/tags";
    }
    @PostMapping("/dashboard/tools/{id}/tags/{tagId}/unassign")
    public String unassign(@PathVariable Long id, @PathVariable Long tagId, RedirectAttributes redirect) {
        var actor = actors.requireActor(); tags.unassignTag(id, tagId, actor.id(), actor.admin());
        redirect.addFlashAttribute("successMessage", "Tag removed."); return "redirect:/dashboard/tools/" + id + "/tags";
    }
    private ToolResponse authorizedTool(Long id, CurrentActor actor) {
        var tool = tools.getById(id, actor.id(), actor.admin());
        if (!actor.admin() && !actor.id().equals(tool.getOwnerId())) throw new AccessDeniedException("You do not own this tool");
        return tool;
    }
}
