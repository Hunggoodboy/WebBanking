package com.bankingeconomy.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/login")
    public String login() {
        return "Login";
    }

    @GetMapping("/register")
    public String register() {
        return "Register";
    }

    @GetMapping("/historyTransfer")
    public String historyTransfer() {
        return "historyTransfer";
    }

    @GetMapping({"/", "/dashboard"})
    public String home() {
        return "Dashboard";
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard() {
        return "AdminDashboard";
    }

    @GetMapping("/admin/quarterly-growth")
    public String quarterlyGrowth() {
        return "QuarterlyGrowth";
    }

    @GetMapping("/transfer")
    public String transfer() {
        return "Transfer";
    }

    @GetMapping("/profile")
    public String profile() {
        return "profile";
    }

    @GetMapping("/statistics")
    public String statistics() {
        return "Statistics";
    }

    @GetMapping("/beneficiaries")
    public String beneficiaries() {
        return "Beneficiaries";
    }
}
