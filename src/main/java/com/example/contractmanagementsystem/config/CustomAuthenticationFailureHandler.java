package com.example.contractmanagementsystem.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
// 新增导入
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class CustomAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    public CustomAuthenticationFailureHandler() {
        // 可以设置一个默认的失败URL，但我们会在onAuthenticationFailure中覆盖它
        // super.setDefaultFailureUrl("/login?error=true");
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String errorMessage = "登录失败，请联系管理员。"; // 默认错误信息

        if (exception instanceof UsernameNotFoundException) {
            errorMessage = "用户不存在，请检查用户名。";
        } else if (exception instanceof BadCredentialsException) {
            errorMessage = "密码错误，请重新输入。";
        } else if (exception instanceof LockedException) {
            errorMessage = "账户已被锁定，请联系管理员。";
        } else if (exception instanceof DisabledException) {
            errorMessage = "账户已被禁用，请联系管理员。";
        } else if (exception instanceof SessionAuthenticationException) { // <--- 新增判断
            // SessionAuthenticationException 是并发会话限制导致错误时可能抛出的异常的基类
            // 例如 MaximumSessionsExceededException 是它的子类
            errorMessage = "此账户已在别处登录，或已达到最大会话数，不允许新的登录。";
        }
        // 可以根据需要添加更多类型的异常判断，例如账户过期、凭证过期等

        // 将错误信息编码后作为URL参数传递
        String encodedErrorMessage = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8.toString());
        String failureUrl = "/login?error=" + encodedErrorMessage;

        // 设置重定向的URL
        super.setDefaultFailureUrl(failureUrl);
        super.onAuthenticationFailure(request, response, exception);
    }
}