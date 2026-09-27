package com.example.toolhub.controller.web;

import com.example.toolhub.exception.CatalogConflictException;
import com.example.toolhub.exception.InvalidStateTransitionException;
import com.example.toolhub.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice(assignableTypes = RoleEWebController.class)
public class RoleEWebExceptionHandler {
    @ExceptionHandler({InvalidStateTransitionException.class, CatalogConflictException.class})
    public String conflict(RuntimeException exception, Model model, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        model.addAttribute("message", "รายการนี้เปลี่ยนไปแล้วหรือมีข้อมูลซ้ำ โปรดกลับไปตรวจสอบแล้วลองอีกครั้ง");
        return "versions/error";
    }

    @ExceptionHandler(AccessDeniedException.class)
    public String forbidden(AccessDeniedException exception, Model model, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        model.addAttribute("message", "คุณไม่มีสิทธิ์ดำเนินการกับรายการนี้");
        return "versions/error";
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public String notFound(ResourceNotFoundException exception, Model model, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        model.addAttribute("message", "ไม่พบรายการที่ต้องการ");
        return "versions/error";
    }
}
