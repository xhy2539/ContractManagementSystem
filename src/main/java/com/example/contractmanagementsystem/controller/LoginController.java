package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.entity.ContractProcess;
import com.example.contractmanagementsystem.service.ContractService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper; // 新增导入
import com.fasterxml.jackson.core.JsonProcessingException; // 新增导入

@Controller
public class LoginController {

    private final ContractService contractService;
    private final ObjectMapper objectMapper; // 引入 ObjectMapper

    @Autowired
    public LoginController(ContractService contractService, ObjectMapper objectMapper) { // 注入 ObjectMapper
        this.contractService = contractService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error,
                            @RequestParam(value = "logout", required = false) String logout,
                            Model model, Principal principal) {

        if (principal != null) {
            return "redirect:/dashboard";
        }

        if (error != null) {
            model.addAttribute("loginError", error);
        }

        if (logout != null) {
            model.addAttribute("logoutMessage", "您已成功注销。");
        }
        return "login";
    }

    @GetMapping("/dashboard")
    public String dashboardPage(Principal principal, Model model) {
        if (principal != null) {
            String username = principal.getName();
            model.addAttribute("username", username);

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null && authentication.isAuthenticated()) {
                List<String> authoritiesList = authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toList());
                try {
                    String authoritiesJson = objectMapper.writeValueAsString(authoritiesList);
                    model.addAttribute("currentUserAuthoritiesJson", authoritiesJson); // 传递 JSON 字符串
                } catch (JsonProcessingException e) {
                    // 处理 JSON 序列化异常
                    System.err.println("Error serializing authorities to JSON: " + e.getMessage());
                    model.addAttribute("currentUserAuthoritiesJson", "[]"); // 序列化失败时传递空 JSON 数组
                }
            } else {
                model.addAttribute("currentUserAuthoritiesJson", "[]"); // 未认证用户传递空 JSON 数组
            }

            if (contractService != null) {
                List<ContractProcess> pendingTasks = contractService.getAllPendingTasksForUser(username);
                model.addAttribute("pendingTasks", pendingTasks);

                Map<String, Object> systemStats = new HashMap<>();
                systemStats.put("activeContractsCount", contractService.countActiveContracts(username));
                systemStats.put("expiringSoonCount", contractService.countContractsExpiringSoon(username, 30));
                model.addAttribute("systemStats", systemStats);

                if (authentication != null && authentication.getAuthorities().stream()
                        .anyMatch(ga -> ga.getAuthority().equals("ROLE_ADMIN"))) {
                    long pendingAssignmentCount = contractService.countContractsPendingAssignment();
                    if (pendingAssignmentCount > 0) {
                        model.addAttribute("adminPendingAssignmentCount", pendingAssignmentCount);
                    }
                }

            } else {
                System.err.println("错误: ContractService 未在 LoginController 中注入!");
                model.addAttribute("pendingTasks", List.of());
                model.addAttribute("systemStats", new HashMap<>());
            }
        }
        return "dashboard";
    }

    @GetMapping("/")
    public String rootPath(Principal principal) {
        if (principal != null) {
            return "redirect:/dashboard";
        }
        return "redirect:/login";
    }
}