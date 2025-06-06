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
    public String dashboard(Model model, Principal principal) { // Changed method name to 'dashboard' for consistency
        if (principal != null) {
            String username = principal.getName();
            model.addAttribute("username", username);

            if (contractService != null) {
                // 在加载仪表盘数据之前，先执行合同过期状态的更新
                // 这将确保仪表盘上显示的“已过期合同总数”是最新的
                try {
                    int updatedCount = contractService.updateExpiredContractStatuses();
                    System.out.println("成功在登录时更新了 " + updatedCount + " 份过期合同的状态。");
                } catch (Exception e) {
                    System.err.println("在登录时更新过期合同状态失败: " + e.getMessage());
                    // 实际应用中，您可能需要记录更详细的日志或向用户显示一个不显眼的警告
                }

                List<ContractProcess> pendingTasks = contractService.getAllPendingTasksForUser(username);
                model.addAttribute("pendingTasks", pendingTasks);

                // 获取当前用户是否为管理员
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                        .anyMatch(ga -> ga.getAuthority().equals("ROLE_ADMIN"));

                Map<String, Object> systemStats = new HashMap<>();
                // 传递 username 和 isAdmin 参数以过滤统计结果
                systemStats.put("activeContractsCount", contractService.countActiveContracts(username, isAdmin));
                systemStats.put("expiringSoonCount", contractService.countContractsExpiringSoon(30, username, isAdmin));
                systemStats.put("expiredContractsCount", contractService.countExpiredContracts(username, isAdmin));
                // 新增：流程中合同数量的统计
                systemStats.put("inProcessContractsCount", contractService.countInProcessContracts(username, isAdmin));
                model.addAttribute("systemStats", systemStats);

                // 检查用户是否为管理员并添加待分配合同数量，作为独立的属性
                // 这个统计通常是全局的，不按客户过滤，所以保持不变
                if (isAdmin) { // 只有管理员才需要看到待分配合同数量
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
