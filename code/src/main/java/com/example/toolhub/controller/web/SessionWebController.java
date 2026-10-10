package com.example.toolhub.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.ui.Model;

/** Browser entry points reuse Role A's session API; no separate authentication implementation. */
@Controller
public class SessionWebController {
    @GetMapping("/login")
    public String login(@RequestParam(required = false) String next, Model model) {
        model.addAttribute("returnTarget", ReturnTarget.safe(next));
        return "auth/login";
    }
    @GetMapping("/register")
    public String register(@RequestParam(required = false) String next, Model model) {
        model.addAttribute("returnTarget", ReturnTarget.safe(next));
        return "auth/register";
    }
}
