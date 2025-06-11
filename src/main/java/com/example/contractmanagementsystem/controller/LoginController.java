package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.dto.DashboardPendingTaskDto;
import com.example.contractmanagementsystem.dto.DashboardStatsDto;
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
    public String dashboard(Model model, Principal principal) {
        if (principal != null) {
            String username = principal.getName();
            model.addAttribute("username", username);

            if (contractService != null) {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                        .anyMatch(ga -> ga.getAuthority().equals("ROLE_ADMIN"));

                // [重要修改] 调用新的、为仪表盘优化的方法
                List<DashboardPendingTaskDto> pendingTasks = contractService.getDashboardPendingTasks(username, isAdmin);
                model.addAttribute("pendingTasks", pendingTasks);

                // 全局统计部分保持不变，它已经优化为一次查询
                DashboardStatsDto stats = contractService.getDashboardStatistics(username, isAdmin);
                Map<String, Object> systemStats = new HashMap<>();
                if (stats != null) {
                    systemStats.put("activeContractsCount", stats.getActiveContractsCount());
                    systemStats.put("expiringSoonCount", stats.getExpiringSoonCount());
                    systemStats.put("expiredContractsCount", stats.getExpiredContractsCount());
                    systemStats.put("inProcessContractsCount", stats.getInProcessContractsCount());
                    if (isAdmin) {
                        model.addAttribute("adminPendingAssignmentCount", stats.getPendingAssignmentCount());
                    }
                }
                model.addAttribute("systemStats", systemStats);

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