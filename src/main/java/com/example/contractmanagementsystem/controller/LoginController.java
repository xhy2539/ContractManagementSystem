package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.entity.ContractProcess;
import com.example.contractmanagementsystem.service.ContractService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.HashMap; // 新增导入
import java.util.List;
import java.util.Map;    // 新增导入

@Controller
public class LoginController {

    private final ContractService contractService;

    @Autowired
    public LoginController(ContractService contractService) {
        this.contractService = contractService;
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

            if (contractService != null) {
                List<ContractProcess> pendingTasks = contractService.getAllPendingTasksForUser(username);
                model.addAttribute("pendingTasks", pendingTasks);

                // --- 新增：获取并添加系统统计信息 ---
                Map<String, Object> systemStats = new HashMap<>();
                systemStats.put("activeContractsCount", contractService.countActiveContracts());
                systemStats.put("expiringSoonCount", contractService.countContractsExpiringSoon(30)); // 30天内到期
                model.addAttribute("systemStats", systemStats);
                // --- 结束新增 ---

            } else {
                System.err.println("错误: ContractService 未在 LoginController 中注入!");
                model.addAttribute("pendingTasks", List.of());
                model.addAttribute("systemStats", new HashMap<>()); // 提供一个空的Map
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