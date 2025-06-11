package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.dto.CustomerCreationRequest;
import com.example.contractmanagementsystem.entity.Customer;
//import com.example.contractmanagementsystem.exception.CustomerNotFoundException; // 根据实际情况取消注释
//import com.example.contractmanagementsystem.exception.DuplicateCustomerException; // 根据实际情况取消注释
import com.example.contractmanagementsystem.service.CustomerService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus; // 新增导入
import org.springframework.http.ResponseEntity; // 新增导入
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
            @RequestParam(value = "size", defaultValue = "10") int size, // 允许前端控制size
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            Model model) {

        try {
            // 确保分页参数有效
            if (page < 0) page = 0;
            if (size <= 0) size = 10; // 防止无效的页面大小

            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<Customer> customerPage = customerService.searchCustomers(keyword, pageable);
            List<Customer> customers = customerPage.getContent();

            int start = (int) customerPage.getPageable().getOffset() + 1;
            int end = Math.min(start + size - 1, (int) customerPage.getTotalElements());

            model.addAttribute("customers", customers);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", customerPage.getTotalPages());
            model.addAttribute("totalElements", customerPage.getTotalElements());
            model.addAttribute("pageSize", size);
            model.addAttribute("keyword", keyword);
            model.addAttribute("start", start);
            model.addAttribute("end", end);

        } catch (Exception e) {
            logger.error("Error retrieving customers", e);
            model.addAttribute("errorMessage", "无法加载客户数据: " + e.getMessage());
            model.addAttribute("customers", Collections.emptyList());
        }

        return "baseData/customers";
    }

    // 用于起草合同页面模态框添加客户的API端点
    // 注意：这个端点返回 JSON，而原来的 addCustomer 方法是重定向
    @PostMapping("/api/add") // 使用不同的路径或根据 Accept header 区分
    @ResponseBody //确保返回JSON
    public ResponseEntity<?> addCustomerApi(
            @Valid @ModelAttribute CustomerCreationRequest request, // @ModelAttribute用于x-www-form-urlencoded
            BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            // 返回具体的错误信息，方便前端处理
            return ResponseEntity.badRequest().body(
                    bindingResult.getFieldErrors().stream()
                            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                            .toList()
            );
        }
        try {
            Customer customer = customerService.addCustomer(request);
            return new ResponseEntity<>(customer, HttpStatus.CREATED);
            //} catch (DuplicateCustomerException e) { // 根据您实际的异常类型
            //    return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) { // 其他通用异常
            logger.error("Error adding customer via API: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("添加客户失败: " + e.getMessage());
        }
    }


    // 原有的用于 customers.html 页面的表单提交处理，它进行重定向
    @PostMapping
    public String addCustomerPage(
            @Valid @ModelAttribute CustomerCreationRequest request, // 明确使用 @ModelAttribute
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    bindingResult.getFieldError().getDefaultMessage());
            // 为了在重定向后能恢复表单数据，可以考虑将 request 也加入 flash attributes
            // redirectAttributes.addFlashAttribute("customerCreationRequest", request);
            return "redirect:/customers"; // 重定向回列表页，错误消息会显示
        }

        try {
            Customer customer = customerService.addCustomer(request);
            redirectAttributes.addFlashAttribute("successMessage",
                    "客户添加成功: " + customer.getCustomerName());
            //} catch (DuplicateCustomerException e) {
            //    redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            logger.error("Error adding customer from page: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "添加客户失败: " + e.getMessage());
        }

        return "redirect:/customers";
    }

    @PostMapping("/update")
    @ResponseBody
    public ResponseEntity<?> updateCustomer(
            @Valid @ModelAttribute CustomerCreationRequest request,
            @RequestParam("id") Long id,
            BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest()
                    .body(bindingResult.getFieldError().getDefaultMessage());
        }

        try {
            Customer updatedCustomer = customerService.updateCustomer(id, request);
            return ResponseEntity.ok()
                    .body(updatedCustomer);
        } catch (Exception e) {
            logger.error("Error updating customer {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(e.getMessage());
        }
    }

    @PostMapping("/delete")
    public ResponseEntity<String> deleteCustomer(@RequestParam("id") Long id) {
        try {
            Customer customer = customerService.getCustomerById(id);
            customerService.deleteCustomer(id);
            return ResponseEntity.ok("客户删除成功: " + customer.getCustomerName());
        } catch (Exception e) {
            logger.error("Error deleting customer {}: {}", id, e.getMessage());
            
            // 检查是否是外键约束错误
            String errorMessage = e.getMessage();
            Throwable rootCause = getRootCause(e);
            if (rootCause instanceof org.hibernate.exception.ConstraintViolationException ||
                (errorMessage != null && (
                    errorMessage.contains("ConstraintViolationException") ||
                    errorMessage.contains("foreign key constraint") ||
                    errorMessage.contains("关联")
                ))) {
                return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body("该客户已有关联的合同，无法删除。请先处理相关合同后再尝试删除。");
            }
            
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("删除客户失败: " + e.getMessage());
        }
    }

    // 获取异常的根本原因
    private Throwable getRootCause(Throwable throwable) {
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }

    // 新增：用于起草合同页面动态搜索客户的API
    @GetMapping("/api/search")
    @ResponseBody // 返回JSON数据
    public ResponseEntity<Page<Customer>> searchCustomersApi(
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("customerName").ascending());
            Page<Customer> customerPage = customerService.searchCustomers(keyword, pageable);
            return ResponseEntity.ok(customerPage);
        } catch (Exception e) {
            logger.error("Error searching customers via API: {}", e.getMessage());
            // 返回一个空的Page对象或错误响应
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Page.empty());
        }
    }
}