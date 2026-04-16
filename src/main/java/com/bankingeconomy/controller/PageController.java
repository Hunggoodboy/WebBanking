package com.bankingeconomy.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {
    @GetMapping("/")
    public String home() {
        return "profile";
    }

    @GetMapping("/login")
    public String login() {
        return "Login";
    }

    @GetMapping("/register")
    public String register() {
        return "Register";
    }

    @GetMapping("/profile")
    public String profile() {
        return "profile";
    }

    @GetMapping("/historyTransfer")
    public String historyTransfer() {
        return "historyTransfer";
    }
}
