package com.example.contractmanagementsystem.controller;

import org.springframework.stereotype.Controller; // 导入 @Controller
import org.springframework.web.bind.annotation.GetMapping;

@Controller // <-- 注意这里是 @Controller，用于返回视图
public class ViewController {

    /**
     * 处理 /register GET 请求，返回注册页面
     */
    @GetMapping("/register")
    public String showRegistrationPage() {
        return "register"; // 返回 Thymeleaf 模板名，Spring 会解析为 src/main/resources/templates/register.html
    }
    /*
     * 也可以处理根路径，例如显示一个欢迎页
     */
    /*
    @GetMapping("/")
    public String showHomePage() {
        return "index"; // 假设你有一个 index.html 页面
    }*/
}