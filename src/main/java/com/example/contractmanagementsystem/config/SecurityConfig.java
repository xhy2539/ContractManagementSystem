package com.example.contractmanagementsystem.config;

import com.example.contractmanagementsystem.service.UserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
public class SecurityConfig {


    @Autowired
    private AuthenticationSuccessHandler customAuthenticationSuccessHandler;

    @Autowired
    private AuthenticationFailureHandler customAuthenticationFailureHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http, UserDetailsServiceImpl userDetailsService, PasswordEncoder passwordEncoder) throws Exception {
        AuthenticationManagerBuilder authenticationManagerBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
        authenticationManagerBuilder.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder);
        return authenticationManagerBuilder.build();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/", "/register", "/api/auth/register", "/login", "/perform_login", "/css/**", "/js/**", "/error/**").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        // 确保 /api/attachments/** 路径需要认证，但不要重复
                        .requestMatchers("/api/attachments/**").authenticated()
                        // /contract-manager/** 路径应该由控制器处理，这里不需要特殊配置，
                        // anyRequest().authenticated() 会确保其需要认证
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // 新增：延期请求审批的路径，仅管理员可见
                        .requestMatchers("/admin/approve-extension-request/**").hasAuthority("CON_EXTEND_APPROVAL_VIEW")
                        // 新增：合同模板管理页面和API
                        .requestMatchers("/admin/templates/**").hasAuthority("TEMP_VIEW_LIST") // View access to template management page
                        .requestMatchers("/admin/templates/api/**").hasRole("ADMIN") // API access restricted to ADMIN for now, finer-grained with @PreAuthorize in controller
                        .requestMatchers("/api/system/**").authenticated()
                        .anyRequest().authenticated() // 任何其他请求都需要认证
                )
                .authenticationManager(authenticationManager)
                .formLogin(formLogin -> formLogin
                        .loginPage("/login")
                        .loginProcessingUrl("/perform_login")
                        .successHandler(customAuthenticationSuccessHandler)
                        .failureHandler(customAuthenticationFailureHandler)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/perform_logout")
                        .logoutSuccessUrl("/login?logout")
                        .deleteCookies("JSESSIONID")
                        .invalidateHttpSession(true)
                        .permitAll()
                )
                // --- 并发会话控制配置开始 ---
                .sessionManagement(session -> session
                        .maximumSessions(1) // 每个用户最多允许一个活动会话
                        .expiredUrl("/login?expired") // 当由于新登录导致旧会话过期时，重定向到此URL
                        .maxSessionsPreventsLogin(true) // 可选：如果设置为true，则在达到最大会话数时阻止新登录，而不是使旧会话无效。默认为false。
                );
        // --- 并发会话控制结束 ---
        return http.build();
    }


    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        // 确保这里只忽略真正的静态资源目录，不包含动态路由
        return (web) -> web.ignoring().requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico");
    }
}