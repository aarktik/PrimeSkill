package com.example.toolhub.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Browser entry points reuse Role A's session API; no separate authentication implementation. */
@Controller
public class SessionWebController {
    @GetMapping("/")
    public String home() { return "redirect:/dashboard/tools"; }
    @GetMapping("/login")
    public String login() { return "auth/login"; }
    @GetMapping("/register")
    public String register() { return "auth/register"; }
}