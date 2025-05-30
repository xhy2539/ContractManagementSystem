package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.dto.CustomerCreationRequest;
import com.example.contractmanagementsystem.entity.Customer;
//import com.example.contractmanagementsystem.exception.CustomerNotFoundException;
//import com.example.contractmanagementsystem.exception.DuplicateCustomerException;
import com.example.contractmanagementsystem.service.CustomerService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.List;

@Controller
@RequestMapping("/customers")
public class CustomerController {

    private static final Logger logger = LoggerFactory.getLogger(CustomerController.class);
    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public String listCustomers(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            Model model) {

        try {
            int pageSize = 10;
            Pageable pageable = PageRequest.of(page, pageSize, Sort.by("createdAt").descending());
            Page<Customer> customerPage = customerService.searchCustomers(keyword, pageable);
            List<Customer> customers = customerPage.getContent();

            model.addAttribute("customers", customers);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", customerPage.getTotalPages());
            model.addAttribute("totalElements", customerPage.getTotalElements());
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("keyword", keyword);

        } catch (Exception e) {
            logger.error("Error retrieving customers", e);
            model.addAttribute("errorMessage", "无法加载客户数据: " + e.getMessage());
            model.addAttribute("customers", Collections.emptyList());
        }

        return "customers";
    }

    @PostMapping
    public String addCustomer(
            @Valid CustomerCreationRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    bindingResult.getFieldError().getDefaultMessage());
            return "redirect:/customers";
        }

        try {
            Customer customer = customerService.addCustomer(request);
            redirectAttributes.addFlashAttribute("successMessage",
                    "客户添加成功: " + customer.getCustomerName());
//        } catch (DuplicateCustomerException e) {
//            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "添加客户失败: " + e.getMessage());
        }

        return "redirect:/customers";
    }

    @PostMapping("/update")
    public String updateCustomer(
            @Valid CustomerCreationRequest request,
            @RequestParam("id") Long id,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    bindingResult.getFieldError().getDefaultMessage());
            return "redirect:/customers";
        }

        try {
            Customer updatedCustomer = customerService.updateCustomer(id, request);
            redirectAttributes.addFlashAttribute("successMessage",
                    "客户信息更新成功: " + updatedCustomer.getCustomerName());
//        } catch (CustomerNotFoundException | DuplicateCustomerException e) {
//            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "更新客户信息失败: " + e.getMessage());
        }

        return "redirect:/customers";
    }

    @PostMapping("/delete")
    public String deleteCustomer(
            @RequestParam("id") Long id,
            RedirectAttributes redirectAttributes) {

        try {
            Customer customer = customerService.getCustomerById(id);
            customerService.deleteCustomer(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "客户删除成功: " + customer.getCustomerName());
//        } catch (CustomerNotFoundException e) {
//            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "删除客户失败: " + e.getMessage());
        }

        return "redirect:/customers";
    }
}