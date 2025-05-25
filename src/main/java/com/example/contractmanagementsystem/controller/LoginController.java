package com.example.contractmanagementsystem.controller;

// import org.springframework.security.access.prepost.PreAuthorize; // 如果没有其他地方用到，可以移除
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
            model.addAttribute("username", principal.getName());
            // 这里可以添加更多加载到 dashboard 页面的数据, 例如用户角色等
            // Collection<? extends GrantedAuthority> authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
            // model.addAttribute("authorities", authorities);
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