package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.dto.ChangePasswordRequest;
import com.example.contractmanagementsystem.dto.UserProfileDTO;
import com.example.contractmanagementsystem.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/profile")
public class ProfileController {

    private final UserService userService;

    @Autowired
    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String showProfile(Model model, Authentication authentication) {
        String username = authentication.getName();
        UserProfileDTO userProfile = userService.getUserProfile(username);
        model.addAttribute("userProfile", userProfile);
        return "profile";
    }

    @PostMapping("/update")
    public String updateProfile(
            @ModelAttribute("userProfile") @Valid UserProfileDTO userProfile,
            BindingResult bindingResult,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return "profile";
        }

        try {
            userService.updateUserProfile(authentication.getName(), userProfile);
            redirectAttributes.addFlashAttribute("successMessage", "个人信息更新成功！");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "更新失败：" + e.getMessage());
        }

        return "redirect:/profile";
    }

    @PostMapping("/change-password")
    public String changePassword(
            @ModelAttribute ChangePasswordRequest passwordRequest,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            userService.changePassword(
                authentication.getName(),
                passwordRequest.getCurrentPassword(),
                passwordRequest.getNewPassword(),
                passwordRequest.getConfirmPassword()
            );
            redirectAttributes.addFlashAttribute("successMessage", "密码修改成功！");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "密码修改失败：" + e.getMessage());
        }

        return "redirect:/profile";
    }
} 