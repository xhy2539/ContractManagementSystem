// Path: src/main/java/com/example/contractmanagementsystem/controller/LoginController.java
package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.entity.ContractProcess;
import com.example.contractmanagementsystem.service.ContractService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

                Map<String, Object> systemStats = new HashMap<>();
                systemStats.put("activeContractsCount", contractService.countActiveContracts(username));
                systemStats.put("expiringSoonCount", contractService.countContractsExpiringSoon(username, 30));
                model.addAttribute("systemStats", systemStats);

                // 检查用户是否为管理员并添加待分配合同数量，作为独立的属性
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
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