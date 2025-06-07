package com.example.contractmanagementsystem.controller;

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
                // 在加载仪表盘数据之前，先执行合同过期状态的更新 (这个逻辑可以保留或移至定时任务)
                try {
                    int updatedCount = contractService.updateExpiredContractStatuses();
                    System.out.println("成功在登录时更新了 " + updatedCount + " 份过期合同的状态。");
                } catch (Exception e) {
                    System.err.println("在登录时更新过期合同状态失败: " + e.getMessage());
                }

                // 获取当前用户是否为管理员
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                        .anyMatch(ga -> ga.getAuthority().equals("ROLE_ADMIN"));

                // 获取当前用户的所有待处理任务 (此方法已在上一轮优化)
                List<ContractProcess> pendingTasks = contractService.getAllPendingTasksForUser(username);
                model.addAttribute("pendingTasks", pendingTasks);

                // ==================== 优化点：一次性获取所有统计数据 ====================
                DashboardStatsDto stats = contractService.getDashboardStatistics(username, isAdmin);
                Map<String, Object> systemStats = new HashMap<>();
                if (stats != null) {
                    systemStats.put("activeContractsCount", stats.getActiveContractsCount());
                    systemStats.put("expiringSoonCount", stats.getExpiringSoonCount());
                    systemStats.put("expiredContractsCount", stats.getExpiredContractsCount());
                    systemStats.put("inProcessContractsCount", stats.getInProcessContractsCount());
                    if (isAdmin) {
                        // DTO中已包含此值，可以直接使用或传递给model
                        if (stats.getPendingAssignmentCount() > 0) {
                            model.addAttribute("adminPendingAssignmentCount", stats.getPendingAssignmentCount());
                        }
                    }
                }
                model.addAttribute("systemStats", systemStats);
                // ============================ 优化结束 ============================

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