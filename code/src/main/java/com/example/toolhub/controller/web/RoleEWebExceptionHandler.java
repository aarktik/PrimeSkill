package com.example.toolhub.controller.web;

import com.example.toolhub.exception.CatalogConflictException;
import com.example.toolhub.exception.InvalidStateTransitionException;
import com.example.toolhub.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice(basePackages = "com.example.toolhub.controller.web")
public class RoleEWebExceptionHandler {
    private final WebLayoutAdvice layout;
    public RoleEWebExceptionHandler(WebLayoutAdvice layout) { this.layout = layout; }
    private String errorPage(Model model, jakarta.servlet.http.HttpServletRequest request) {
        layout.layout(model, request);
        if (!model.containsAttribute("recoveryUrl")) recovery(request, model);
        return "versions/error";
    }

    @org.springframework.web.bind.annotation.ModelAttribute
    public void recovery(jakarta.servlet.http.HttpServletRequest request, Model model) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String destination = "/dashboard/tools";
        if (path.startsWith("/admin/categories")) destination = "/admin/categories";
        else if (path.startsWith("/admin/tags")) destination = "/admin/tags";
        else if (path.startsWith("/admin/tools")) destination = "/admin/tools";
        else if (path.equals("/profile")) destination = "/profile";
        else if (path.matches("/dashboard/tools/[0-9]+/tags.*")) destination = path.replaceFirst("(/tags).*", "$1");
        else if (path.matches("/dashboard/tools/[0-9]+/versions.*")) destination = path.replaceFirst("(/versions).*", "$1");
        else if (path.matches("/tools/[0-9]+/reviews.*")) destination = path.replaceFirst("(/reviews).*", "") + "#reviews-heading";
        else if (path.startsWith("/tools/")) destination = "/tools";
        model.addAttribute("recoveryUrl", destination);
    }

    @ExceptionHandler({InvalidStateTransitionException.class, CatalogConflictException.class, com.example.toolhub.exception.StaleReviewRevisionException.class})
    public String conflict(RuntimeException exception, Model model, HttpServletResponse response, jakarta.servlet.http.HttpServletRequest request) {
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        model.addAttribute("message", "This item has changed or conflicts with existing data. Review it again before continuing.");
        return errorPage(model, request);
    }

    @ExceptionHandler({com.example.toolhub.exception.InvalidRequestParameterException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public String badRevision(Exception exception, Model model, HttpServletResponse response, jakarta.servlet.http.HttpServletRequest request) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        model.addAttribute("message", "A required value is missing or invalid. Open the page again and check your input.");
        return errorPage(model, request);
    }

    @ExceptionHandler(org.springframework.dao.PessimisticLockingFailureException.class)
    public String concurrentOperation(Exception exception, Model model, HttpServletResponse response, jakarta.servlet.http.HttpServletRequest request) {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        model.addAttribute("message", "This item is being updated. Reload it before confirming your action.");
        return errorPage(model, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public String forbidden(AccessDeniedException exception, Model model, HttpServletResponse response, jakarta.servlet.http.HttpServletRequest request) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        model.addAttribute("recoveryUrl", "/dashboard/tools");
        model.addAttribute("message", "You do not have permission to perform this action.");
        return errorPage(model, request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public String notFound(ResourceNotFoundException exception, Model model, HttpServletResponse response, jakarta.servlet.http.HttpServletRequest request) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        model.addAttribute("message", "The requested item could not be found.");
        return errorPage(model, request);
    }
}
