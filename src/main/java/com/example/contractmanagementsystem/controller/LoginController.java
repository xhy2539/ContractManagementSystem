
package com.example.contractmanagementsystem.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

@Controller
public class LoginController {

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
            model.addAttribute("loginError", "无效的用户名或密码！");
        }

        if (logout != null) {
            model.addAttribute("logoutMessage", "您已成功注销。");
        }
        return "login"; // 返回名为 "login.html" 的视图模板
    }

    // （可选）一个非常简单的仪表盘/主页的处理器
    // Spring Security 成功登录后，如果 defaultSuccessUrl 指向这里，则会调用此方法
    @GetMapping("/dashboard")
    public String dashboardPage(Principal principal, Model model) {
        if (principal != null) {
            model.addAttribute("username", principal.getName());
        }
        // 这里可以添加更多加载到 dashboard 页面的数据
        return "dashboard"; // 返回名为 "dashboard.html" 的视图模板
    }

    // （可选）如果您有注册功能，并且注册成功后希望跳转到一个特定的成功页面
    // @GetMapping("/register-success")
    // public String registrationSuccessPage() {
    //     return "registration-success"; // 返回名为 "registration-success.html" 的视图模板
    // }

    // （可选）根路径处理器，可以重定向到登录页或仪表盘
    @GetMapping("/")
    public String rootPath(Principal principal) {
        if (principal != null) {
            return "redirect:/dashboard";
        }
        return "redirect:/login";
    }

    @GetMapping("/admin/audit-logs")
    @PreAuthorize("hasRole('ROLE_ADMIN')") // 确保只有管理员能访问
    public String auditLogsPage(Model model) {
        // 可以向模型添加一些初始数据，如果需要的话
        // model.addAttribute("someAttribute", "someValue");
        return "admin/audit-logs"; // Thymeleaf模板的路径 (例如：templates/admin/audit-logs.html)
    }
}
