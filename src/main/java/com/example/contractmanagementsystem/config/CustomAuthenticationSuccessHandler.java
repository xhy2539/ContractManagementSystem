package com.example.contractmanagementsystem.config;

import com.example.contractmanagementsystem.service.AuditLogService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
// 更改继承的类
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;

@Component
// 更改父类为 SavedRequestAwareAuthenticationSuccessHandler
public class CustomAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final AuditLogService auditLogService;

    @Autowired
    public CustomAuthenticationSuccessHandler(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
        // 设置默认的成功URL，如果 determineTargetUrl 逻辑复杂或可能不返回特定URL
        // 或者在 determineTargetUrl 中确保总有一个默认值
        setDefaultTargetUrl("/dashboard");
        setAlwaysUseDefaultTargetUrl(false); // 允许 determineTargetUrl 覆盖默认值
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String username = authentication.getName();
        // 1. 执行您的自定义逻辑，例如记录审计日志
        auditLogService.logAction(username, "USER_LOGIN", "用户 " + username + " 登录成功");

        // 2. 决定目标URL (您的现有逻辑)
        String targetUrl = determineTargetUrl(authentication.getAuthorities());

        // 3. 清除可能导致重定向到错误页面的认证属性
        clearAuthenticationAttributes(request);

        // 4. 使用父类的重定向逻辑，它会处理 SavedRequest 和 JSESSIONID cookie
        //    确保目标URL是有效的
        if (targetUrl == null || targetUrl.isEmpty()) {
            targetUrl = getDefaultTargetUrl(); // 使用之前设置的默认URL
        }

        // 使用 RedirectStrategy 进行重定向，这是 SavedRequestAwareAuthenticationSuccessHandler 内部使用的方式
        // 这能确保会话cookie等被正确处理
        getRedirectStrategy().sendRedirect(request, response, targetUrl);

        // 或者，如果您希望完全利用 SavedRequestAwareAuthenticationSuccessHandler 的逻辑
        // (包括处理 "saved request" 等)，可以这样做：
        // this.setDefaultTargetUrl(targetUrl); // 确保父类知道目标URL
        // super.onAuthenticationSuccess(request, response, authentication);
        // 但是，上面的 getRedirectStrategy().sendRedirect() 通常更直接，如果您只是想重定向到确定好的URL。
    }

    protected String determineTargetUrl(final Collection<? extends GrantedAuthority> authorities) {
        boolean isAdmin = authorities.stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_ADMIN"));
        boolean isUser = authorities.stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_USER"));

        // 您的现有逻辑来决定 targetUrl
        if (isAdmin) {
            return "/dashboard";
        } else if (isUser) {
            return "/dashboard";
        }
        // 默认或新用户等其他情况
        return "/dashboard"; // 确保总有一个返回值
    }
}