package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.dto.ContractExtensionApprovalRequest;
import com.example.contractmanagementsystem.entity.ContractProcess;
import com.example.contractmanagementsystem.entity.ContractProcessState;
import com.example.contractmanagementsystem.entity.ContractProcessType;
import com.example.contractmanagementsystem.service.ContractService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller //
@RequestMapping("/admin/approve-extension-request") //
@PreAuthorize("hasRole('ROLE_ADMIN')") // 确保只有管理员角色可以访问此控制器的所有端点
public class AdminContractExtensionController { //

    private static final Logger logger = LoggerFactory.getLogger(AdminContractExtensionController.class); //

    private final ContractService contractService; //

    @Autowired //
    public AdminContractExtensionController(ContractService contractService) { //
        this.contractService = contractService; //
    } //

    /**
     * 显示待审批的合同延期请求列表。
     * 权限：CON_EXTEND_APPROVAL_VIEW
     * @param pageable 分页和排序参数。
     * @param contractNameSearch 可选的合同名称搜索关键词。
     * @param model Model对象。
     * @param authentication 认证信息。
     * @return 模板路径。
     */
    @GetMapping("/admin/approve-extension-request") //
    @PreAuthorize("hasAuthority('CON_EXTEND_APPROVAL_VIEW')") //
    public String showPendingExtensionRequests( //
                                                @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable, //
                                                @RequestParam(required = false) String contractNameSearch, //
                                                Model model, //
                                                Authentication authentication //
    ) { //
        String username = authentication.getName(); //
        Page<ContractProcess> pendingRequests = contractService.getPendingProcessesForUser( //
                username, ContractProcessType.EXTENSION_REQUEST, ContractProcessState.PENDING, contractNameSearch, pageable //
        ); //

        model.addAttribute("pendingRequests", pendingRequests); //
        model.addAttribute("contractNameSearch", contractNameSearch != null ? contractNameSearch : ""); //
        model.addAttribute("listTitle", "待审批延期请求"); //
        return "approve-extension-request"; //
    } //

    /**
     * 显示合同延期请求的详细信息和审批表单。
     * 权限：CON_EXTEND_APPROVAL_VIEW 或 CON_EXTEND_APPROVAL_SUBMIT
     * @param processId 合同流程ID。
     * @param model Model对象。
     * @param authentication 认证信息。
     * @param redirectAttributes 重定向属性。
     * @return 模板路径。
     */
    @GetMapping("/{processId}") //
    @PreAuthorize("hasAuthority('CON_EXTEND_APPROVAL_VIEW') or hasAuthority('CON_EXTEND_APPROVAL_SUBMIT')") //
    public String showApproveExtensionRequestForm( //
                                                   @PathVariable Long processId, //
                                                   Model model, //
                                                   Authentication authentication, //
                                                   RedirectAttributes redirectAttributes //
    ) { //
        String username = authentication.getName(); //
        try { //
            // 获取具体的延期请求流程，并进行权限和状态验证
            ContractProcess requestProcess = contractService.getContractProcessByIdAndOperator( //
                    processId, username, ContractProcessType.EXTENSION_REQUEST, ContractProcessState.PENDING //
            ); //
            model.addAttribute("requestProcess", requestProcess); //
            model.addAttribute("contract", requestProcess.getContract()); //
            return "admin/approve-extension-request"; //
        } catch (Exception e) { //
            logger.error("加载延期请求审批表单失败 (Process ID: {}): {}", processId, e.getMessage(), e); //
            redirectAttributes.addFlashAttribute("errorMessage", "加载延期请求详情失败: " + e.getMessage()); //
            return "redirect:/admin/admin/approve-extension-request"; //
        } //
    } //

    /**
     * 处理合同延期请求的审批操作（批准或拒绝）。
     * 权限：CON_EXTEND_APPROVAL_SUBMIT
     * @param processId 合同流程ID。
     * @param request 审批请求DTO。
     * @param bindingResult 校验结果。
     * @param authentication 认证信息。
     * @param redirectAttributes 重定向属性。
     * @return 重定向路径。
     */
    @PostMapping("/{processId}") //
    @PreAuthorize("hasAuthority('CON_EXTEND_APPROVAL_SUBMIT')") //
    public String approveExtensionRequest( //
                                           @PathVariable Long processId, //
                                           @Valid @ModelAttribute ContractExtensionApprovalRequest request, //
                                           BindingResult bindingResult, //
                                           Authentication authentication, //
                                           RedirectAttributes redirectAttributes //
    ) { //
        if (bindingResult.hasErrors()) { //
            redirectAttributes.addFlashAttribute("errorMessage", "审批决定不能为空。"); //
            return "redirect:/admin/approve-extension-request/" + processId; //
        } //

        String username = authentication.getName(); //
        boolean isApproved = "APPROVED".equalsIgnoreCase(request.getDecision()); //
        String comments = request.getComments(); //

        try { //
            contractService.processExtensionRequest(processId, username, isApproved, comments); //
            redirectAttributes.addFlashAttribute("successMessage", //
                    "延期请求 (ID: " + processId + ") 已成功" + (isApproved ? "批准" : "拒绝") + "。"); //
        } catch (AccessDeniedException e) { //
            logger.warn("用户 '{}' 无权审批延期请求 {}: {}", username, processId, e.getMessage()); //
            redirectAttributes.addFlashAttribute("errorMessage", "权限不足: " + e.getMessage()); //
        } catch (Exception e) { //
            logger.error("处理延期请求 {} 失败: {}", processId, e.getMessage(), e); //
            redirectAttributes.addFlashAttribute("errorMessage", "处理延期请求失败: " + e.getMessage()); //
        } //
        return "redirect:/admin/approve-extension-request"; //
    } //
}