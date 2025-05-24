package com.example.contractmanagementsystem.config; // 或者 com.example.contractmanagementsystem.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity // 启用Spring Security的Web安全支持
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // 使用 BCrypt 进行密码加密
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        // 这里定义你的URL授权规则
                        // 例如：允许匿名访问注册和登录页面
                        .requestMatchers("/register", "/login", "/css/**", "/js/**").permitAll()
                        // 其他所有请求都需要认证
                        .anyRequest().authenticated()
                )
                .formLogin(formLogin -> formLogin
                        // 自定义登录页面URL (如果需要)
                        .loginPage("/login")
                        // 登录成功后的默认跳转URL
                        .defaultSuccessUrl("/dashboard", true) // 跳转到仪表盘或主页
                        .permitAll()
                )
                .logout(logout -> logout
                        // 自定义登出URL
                        .logoutUrl("/logout")
                        // 登出成功后跳转的URL
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )
        // 如果你的应用是无状态的 (例如纯API)，可以禁用CSRF
        // .csrf(csrf -> csrf.disable()) // 对于传统的Web应用，建议启用CSRF保护
        ;
        return http.build();
    }
}