package com.example.toolhub.controller.web;

import com.example.toolhub.dto.request.CreateToolRequest;
import com.example.toolhub.dto.request.UpdateToolRequest;
import com.example.toolhub.dto.response.ToolResponse;
import com.example.toolhub.security.CurrentActor;
import com.example.toolhub.security.CurrentActorProvider;
import com.example.toolhub.service.CategoryService;
import com.example.toolhub.service.ToolService;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
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

    public ToolWebController(ToolService toolService, CategoryService categoryService,
                             CurrentActorProvider currentActorProvider) {
        this.toolService = toolService;
        this.categoryService = categoryService;
        this.currentActorProvider = currentActorProvider;
    }

    /** Public listing is intentionally supplied by Role C's search/browse flow. */
    @GetMapping("/tools")
    public String list(Model model) {
        model.addAttribute("tools", List.of());
        model.addAttribute("pageTitle", "สำรวจเครื่องมือ");
        model.addAttribute("activeNav", "explore");
        return "tools/list";
    }

    @GetMapping("/tools/{idOrSlug}")
    public String detail(@PathVariable String idOrSlug, Model model) {
        CurrentActor actor = currentActorProvider.currentActor();
        ToolResponse tool = toolService.getByIdOrSlug(idOrSlug, actor.id(), actor.admin());
        model.addAttribute("tool", tool);
        model.addAttribute("pageTitle", tool.getName());
        model.addAttribute("activeNav", "explore");
        return "tools/detail";
    }

    @GetMapping("/dashboard/tools")
    public String dashboard(@RequestParam(defaultValue = "0") int page, Model model) {
        CurrentActor actor = currentActorProvider.requireActor();
        int safePage = Math.max(page, 0);
        var tools = toolService.listOwnedBy(actor.id(),
                PageRequest.of(safePage, DASHBOARD_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "updatedAt")));
        model.addAttribute("tools", tools.getContent());
        model.addAttribute("toolPage", tools);
        model.addAttribute("pageTitle", "เครื่องมือของฉัน");
        model.addAttribute("activeNav", "my-tools");
        return "tools/dashboard";
    }

    @GetMapping("/dashboard/tools/new")
    public String createForm(Model model) {
        currentActorProvider.requireActor();
        model.addAttribute("toolRequest", new CreateToolRequest(null, null, null, null, null, null));
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("pageTitle", "เพิ่มเครื่องมือ");
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
        ToolResponse response = toolService.create(request, currentActorProvider.requireActor().id());
        redirectAttributes.addFlashAttribute("successMessage", "สร้างแบบร่างเครื่องมือแล้ว");
        return "redirect:/tools/" + response.getSlug();
    }

    @GetMapping("/dashboard/tools/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        CurrentActor actor = currentActorProvider.requireActor();
        ToolResponse tool = toolService.getByIdOrSlug(String.valueOf(id), actor.id(), actor.admin());
        model.addAttribute("tool", tool);
        model.addAttribute("toolRequest", new UpdateToolRequest(tool.getName(), tool.getSlug(),
                tool.getShortDescription(), tool.getDescription(), tool.getCategoryId(), tool.getRepositoryUrl()));
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("pageTitle", "แก้ไขเครื่องมือ");
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
            model.addAttribute("categories", categoryService.findAll());
            model.addAttribute("pageTitle", "แก้ไขเครื่องมือ");
            model.addAttribute("formAction", "/dashboard/tools/" + id);
            return "tools/form";
        }
        ToolResponse response = toolService.update(id, request, actor.id(), actor.admin());
        redirectAttributes.addFlashAttribute("successMessage", "บันทึกการเปลี่ยนแปลงแล้ว");
        return "redirect:/tools/" + response.getSlug();
    }

    @PostMapping("/dashboard/tools/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        CurrentActor actor = currentActorProvider.requireActor();
        toolService.delete(id, actor.id(), actor.admin());
        redirectAttributes.addFlashAttribute("successMessage", "ลบเครื่องมือแล้ว");
        return "redirect:/dashboard/tools";
    }

    private void populateCreateForm(Model model) {
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("pageTitle", "เพิ่มเครื่องมือ");
        model.addAttribute("formAction", "/dashboard/tools");
    }
}
