package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.entity.ContractProcess; // 新增导入
import com.example.contractmanagementsystem.service.ContractService; // 新增导入
import org.springframework.beans.factory.annotation.Autowired; // 新增导入
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.List; // 新增导入

@Controller
public class LoginController {

    // 新增：注入 ContractService
    private final ContractService contractService;

    @Autowired
    public LoginController(ContractService contractService) {
        this.contractService = contractService;
    }

    // 处理对 /login 的 GET 请求，显示登录页面
    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error,
                            @RequestParam(value = "logout", required = false) String logout,
                            Model model, Principal principal) {

        // 如果用户已经登录，直接重定向到仪表盘或主页，避免重复显示登录页
        if (principal != null) {
            return "redirect:/dashboard"; // 或者您的应用主页
        }

        if (error != null) {
            // 注意：错误信息现在由 CustomAuthenticationFailureHandler 处理并直接传递到URL参数
            // 这里可以保留，或者依赖Thymeleaf直接从 param.error 获取
            // model.addAttribute("loginError", "无效的用户名或密码！");
            model.addAttribute("loginError", error); // 将错误信息直接传递给模板
        }

        if (logout != null) {
            model.addAttribute("logoutMessage", "您已成功注销。");
        }
        return "login"; // 返回名为 "login.html" 的视图模板
    }

    @GetMapping("/dashboard")
    public String dashboardPage(Principal principal, Model model) {
        if (principal != null) {
            String username = principal.getName();
            model.addAttribute("username", username);

            // --- 新增逻辑：获取并添加待处理任务列表到模型 ---
            // 确保 contractService 已通过构造函数注入
            if (contractService != null) {
                List<ContractProcess> pendingTasks = contractService.getAllPendingTasksForUser(username);
                model.addAttribute("pendingTasks", pendingTasks);
                // 可以在这里添加日志，方便调试
                // System.out.println("为用户 " + username + " 加载了 " + (pendingTasks != null ? pendingTasks.size() : 0) + " 个待处理任务。");
            } else {
                // 处理 contractService 未注入的情况，例如打印错误日志或添加一个空列表
                System.err.println("错误: ContractService 未在 LoginController 中注入!");
                model.addAttribute("pendingTasks", List.of()); // 提供一个空列表以避免模板错误
            }
            // --- 结束新增逻辑 ---

        }
        return "dashboard"; // 返回名为 "dashboard.html" 的视图模板
    }

    @GetMapping("/")
    public String rootPath(Principal principal) {
        if (principal != null) {
            return "redirect:/dashboard";
        }
        return "redirect:/login";
    }

    // auditLogsPage 方法已从此控制器中移除，因为它现在由 AdminController 处理
}