package com.example.contractmanagementsystem.config;

import com.example.contractmanagementsystem.service.AuditLogService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Autowired
    private AuditLogService auditLogService; // 注入AuditLogService来记录登录日志

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String username = authentication.getName();
        // 记录登录成功的审计日志
        auditLogService.logAction(username, "USER_LOGIN", "用户 " + username + " 登录成功");


        // 根据用户角色决定重定向的URL
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        String targetUrl = determineTargetUrl(authorities);

        if (response.isCommitted()) {
            // logger.debug("Response has already been committed. Unable to redirect to " + targetUrl);
            return;
        }
        response.sendRedirect(request.getContextPath() + targetUrl);
    }

    protected String determineTargetUrl(final Collection<? extends GrantedAuthority> authorities) {
        boolean isAdmin = authorities.stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_ADMIN"));
        boolean isUser = authorities.stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_USER"));
        // 您可以根据需求文档中的角色（合同管理员、合同操作员）添加更多判断
        // 例如： boolean isContractOperator = authorities.stream().anyMatch(ga -> ga.getAuthority().equals("ROLE_CONTRACT_OPERATOR"));

        if (isAdmin) {
            // 根据需求文档，管理员登录后转向管理员操作页面
            // 假设管理员的仪表盘是 /admin/dashboard 或就是 /dashboard (如果所有角色共享仪表盘，但内容不同)
            return "/dashboard"; // 或者特定的管理员仪表盘URL
        } else if (isUser) {
            // 根据需求文档，合同操作员登录后转向操作员页面
            // 假设普通用户/合同操作员的仪表盘是 /user/dashboard 或也是 /dashboard
            return "/dashboard"; // 或者特定的用户仪表盘URL
        }
        // 对于新用户（没有明确角色或只有ROLE_NEW_USER）的情况
        // 需求文档中提到 "新用户：没有任何权限，等待合同管理员分配权限！"
        // 可以跳转到一个特定的提示页面，或者还是仪表盘但仪表盘会显示相应提示。
        // 您的 login.html 已经有处理新用户的逻辑，所以跳转到仪表盘即可。
        // 需求文档图3-5 新用户页面，显示 "您是新用户,没有合同操作权限,等待管理员为您配置权限!"
        // 这个提示是在 /dashboard 页面根据用户角色来动态显示的（如果dashboard能获取用户角色信息）。
        // 或者，您可以定义一个特定的 "/new-user-info" 页面。
        // 为了简化，这里也导向 /dashboard，由 dashboard 页面或其 Controller 决定如何显示。
        boolean isNewUserWithoutOtherRoles = authorities.stream()
                .allMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_NEW_USER")); // 假设有这样一个角色
        if (authorities.isEmpty() || isNewUserWithoutOtherRoles) {
            return "/dashboard"; // 或者 "/new_user_landing_page"
        }


        // 默认跳转到仪表盘
        return "/dashboard";
    }
}