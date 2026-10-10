package com.example.toolhub.controller.web;

import com.example.toolhub.dto.request.CategoryRequest;
import com.example.toolhub.dto.request.TagRequest;
import com.example.toolhub.exception.CatalogConflictException;
import com.example.toolhub.security.CurrentActorProvider;
import com.example.toolhub.service.CategoryService;
import com.example.toolhub.service.TagService;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ReferenceWebController {
    private final CategoryService categories;
    private final TagService tags;
    private final CurrentActorProvider actors;

    public ReferenceWebController(CategoryService categories, TagService tags, CurrentActorProvider actors) {
        this.categories = categories; this.tags = tags; this.actors = actors;
    }

    @GetMapping("/admin/categories")
    public String categories(Model model) { admin(); return list(model, "categories"); }
    @GetMapping("/admin/tags")
    public String tags(Model model) { admin(); return list(model, "tags"); }

    @GetMapping("/admin/categories/new")
    public String newCategory(Model model) {
        admin(); model.addAttribute("referenceRequest", new CategoryRequest(null, null, null));
        return form(model, "categories", null);
    }
    @GetMapping("/admin/tags/new")
    public String newTag(Model model) {
        admin(); model.addAttribute("referenceRequest", new TagRequest(null, null));
        return form(model, "tags", null);
    }
    @GetMapping("/admin/categories/{id}/edit")
    public String editCategory(@PathVariable Long id, Model model) {
        admin(); var category = categories.findById(id);
        model.addAttribute("referenceRequest", new CategoryRequest(category.name(), category.slug(), category.description()));
        return form(model, "categories", id);
    }
    @GetMapping("/admin/tags/{id}/edit")
    public String editTag(@PathVariable Long id, Model model) {
        admin(); var tag = tags.findById(id);
        model.addAttribute("referenceRequest", new TagRequest(tag.name(), tag.slug()));
        return form(model, "tags", id);
    }

    @PostMapping("/admin/categories")
    public String createCategory(@Valid @ModelAttribute("referenceRequest") CategoryRequest request,
                                 BindingResult errors, Model model, RedirectAttributes redirect) {
        return saveCategory(null, request, errors, model, redirect);
    }
    @PostMapping("/admin/categories/{id}")
    public String updateCategory(@PathVariable Long id, @Valid @ModelAttribute("referenceRequest") CategoryRequest request,
                                 BindingResult errors, Model model, RedirectAttributes redirect) {
        return saveCategory(id, request, errors, model, redirect);
    }
    private String saveCategory(Long id, CategoryRequest request, BindingResult errors, Model model, RedirectAttributes redirect) {
        admin();
        if (!errors.hasErrors()) {
            try {
                if (id == null) categories.create(request, true); else categories.update(id, request, true);
                redirect.addFlashAttribute("successMessage", "Category saved."); return "redirect:/admin/categories";
            } catch (CatalogConflictException exception) {
                errors.reject("conflict", "A category with this name or slug already exists. Choose another name or slug.");
            }
        }
        return form(model, "categories", id);
    }

    @PostMapping("/admin/tags")
    public String createTag(@Valid @ModelAttribute("referenceRequest") TagRequest request,
                            BindingResult errors, Model model, RedirectAttributes redirect) {
        return saveTag(null, request, errors, model, redirect);
    }
    @PostMapping("/admin/tags/{id}")
    public String updateTag(@PathVariable Long id, @Valid @ModelAttribute("referenceRequest") TagRequest request,
                            BindingResult errors, Model model, RedirectAttributes redirect) {
        return saveTag(id, request, errors, model, redirect);
    }
    private String saveTag(Long id, TagRequest request, BindingResult errors, Model model, RedirectAttributes redirect) {
        admin();
        if (!errors.hasErrors()) {
            try {
                if (id == null) tags.create(request, true); else tags.update(id, request, true);
                redirect.addFlashAttribute("successMessage", "Tag saved."); return "redirect:/admin/tags";
            } catch (CatalogConflictException exception) {
                errors.reject("conflict", "A tag with this name or slug already exists. Choose another name or slug.");
            }
        }
        return form(model, "tags", id);
    }

    @PostMapping("/admin/categories/{id}/delete")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes redirect) {
        admin();
        try { categories.delete(id, true); redirect.addFlashAttribute("successMessage", "Category deleted."); }
        catch (CatalogConflictException exception) {
            redirect.addFlashAttribute("errorMessage", "This category is still used by a tool and cannot be deleted.");
        }
        return "redirect:/admin/categories";
    }
    @PostMapping("/admin/tags/{id}/delete")
    public String deleteTag(@PathVariable Long id, RedirectAttributes redirect) {
        admin();
        try { tags.delete(id, true); redirect.addFlashAttribute("successMessage", "Tag deleted."); }
        catch (CatalogConflictException exception) {
            redirect.addFlashAttribute("errorMessage", "This tag is still used by a tool. Remove its associations before deleting it.");
        }
        return "redirect:/admin/tags";
    }

    private String list(Model model, String kind) {
        model.addAttribute("items", kind.equals("categories") ? categories.findAll() : tags.findAll());
        model.addAttribute("kind", kind); model.addAttribute("pageTitle", kind.equals("categories") ? "Categories" : "Tags");
        model.addAttribute("activeNav", kind); return "admin/reference-list";
    }
    private String form(Model model, String kind, Long id) {
        String singular = kind.equals("categories") ? "category" : "tag";
        model.addAttribute("kind", kind); model.addAttribute("activeNav", kind);
        model.addAttribute("pageTitle", (id == null ? "New " : "Edit ") + singular);
        model.addAttribute("formAction", "/admin/" + kind + (id == null ? "" : "/" + id));
        return "admin/reference-form";
    }
    private void admin() {
        if (!actors.requireActor().admin()) throw new AccessDeniedException("Administrator role is required");
    }
}
