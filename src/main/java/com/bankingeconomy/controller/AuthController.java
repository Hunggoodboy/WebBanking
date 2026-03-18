package com.bankingeconomy.controller;


import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @PostMapping("/login")
    public String login() {
        return "Login successful";
    }

    @PostMapping("/register")
    public String register() {
        return "Register successful";
    }
}
