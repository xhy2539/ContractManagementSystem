package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.dto.ContractDraftRequest;
import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.entity.ContractProcess;
import com.example.contractmanagementsystem.entity.ContractProcessState;
import com.example.contractmanagementsystem.entity.ContractProcessType;
import com.example.contractmanagementsystem.exception.BusinessLogicException;
import com.example.contractmanagementsystem.exception.ResourceNotFoundException;
import com.example.contractmanagementsystem.service.ContractService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page; // 正确的 Page 导入
import org.springframework.data.domain.Pageable; // 正确的 Pageable 导入
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.security.Principal;

@Controller
@RequestMapping("/contracts")
public class ContractController {

    private final ContractService contractService;

    @Autowired
    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    // 显示起草合同页面
    @GetMapping("/draft")
    @PreAuthorize("hasAuthority('CON_DRAFT_NEW')") // 假设有创建合同的权限
    public String showDraftContractPage(Model model) {
        // 为表单提供一个空的 ContractDraftRequest 对象
        model.addAttribute("contractDraftRequest", new ContractDraftRequest());
        return "draft-contract"; // 返回名为 "draft-contract.html" 的视图模板
    }

    // 处理合同起草提交
    @PostMapping("/draft")
    @PreAuthorize("hasAuthority('CON_DRAFT_NEW')")
    public String draftContract(
            @ModelAttribute("contractDraftRequest") @Valid ContractDraftRequest contractDraftRequest,
            BindingResult bindingResult,
            @RequestParam(value = "attachment", required = false) MultipartFile attachment,
            RedirectAttributes redirectAttributes,
            Model model,
            Principal principal // 获取当前登录用户
    ) {
        if (bindingResult.hasErrors()) {
            // 如果有验证错误，重新返回表单页面并显示错误信息
            return "draft-contract";
        }

        try {
            // 获取当前登录用户的用户名
            String username = principal.getName();

            // 1. 调用服务层起草合同
            Contract draftedContract = contractService.draftContract(contractDraftRequest, attachment, username);

            // 2. 成功处理
            redirectAttributes.addFlashAttribute("successMessage", "合同" + draftedContract.getContractName() + "起草成功！");
            return "redirect:/contracts/draft";

        } catch (BusinessLogicException e) {
            // 3. 处理业务逻辑错误（如日期不符、文件格式不正确）
            if (e.getMessage().contains("附件格式")) {
                model.addAttribute("attachmentError", e.getMessage());
            } else {
                model.addAttribute("errorMessage", e.getMessage());
            }
            // 重新将请求对象添加到模型，以便表单回显
            model.addAttribute("contractDraftRequest", contractDraftRequest);
            return "draft-contract";
        } catch (IOException e) {
            // 4. 处理文件操作异常
            redirectAttributes.addFlashAttribute("errorMessage", "文件上传失败，请重试。");
            return "redirect:/contracts/draft";
        } catch (Exception e) {
            // 5. 处理其他未预料的系统异常
            redirectAttributes.addFlashAttribute("errorMessage", "起草合同失败，系统发生未知错误。");
            return "redirect:/errorPage";
        }
    }

    // 假设的错误页面映射，实际项目中可以更完善
    @GetMapping("/errorPage")
    public String errorPage(Model model) {
        model.addAttribute("errorMessage", "很抱歉，系统发生错误，请联系管理员。");
        return "error";
    }

    // 待会签合同列表页面
    @GetMapping("/pending-countersign")
    @PreAuthorize("hasAuthority('CON_CSIGN_VIEW')")
    public String pendingCountersignContracts(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String contractNameSearch,
            Model model,
            Principal principal
    ) {
        // 获取当前登录用户的用户名
        String username = principal.getName();

        // 调用服务层获取待会签合同
        Page<ContractProcess> pendingCountersigns = contractService.getPendingProcessesForUser(
                username, ContractProcessType.COUNTERSIGN, ContractProcessState.PENDING, contractNameSearch, pageable);

        model.addAttribute("pendingCountersigns", pendingCountersigns);
        model.addAttribute("contractNameSearch", contractNameSearch);
        return "pending-countersign";
    }


    @GetMapping("/pending-signing")
    @PreAuthorize("hasAuthority('CON_SIGN_VIEW')") // 假设签订的权限编号为 CON_SIGN_VIEW
    public String pendingSigningContracts(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String contractNameSearch,
            Model model,
            Principal principal
    ) {
        String username = principal.getName();
        Page<ContractProcess> pendingSignings = contractService.getPendingProcessesForUser(
                username, ContractProcessType.SIGNING, ContractProcessState.PENDING, contractNameSearch, pageable);

        model.addAttribute("pendingSignings", pendingSignings);
        model.addAttribute("contractNameSearch", contractNameSearch);
        return "contracts/pending-signing"; // 返回新创建的 HTML 模板
    }

    // --- 新增：显示合同签订详情页面 ---
    @GetMapping("/sign/{contractProcessId}")
    @PreAuthorize("hasAuthority('CON_SIGN_VIEW')")
    public String showSignContractForm(@PathVariable Long contractProcessId, Model model, Principal principal, RedirectAttributes redirectAttributes) {
        try {
            String username = principal.getName();
            ContractProcess contractProcess = contractService.getContractProcessByIdAndOperator(
                    contractProcessId, username, ContractProcessType.SIGNING, ContractProcessState.PENDING);

            model.addAttribute("contractProcess", contractProcess);
            return "contracts/sign-contract"; // 返回新创建的 HTML 模板
        } catch (BusinessLogicException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/contracts/pending-signing";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "加载签订页面失败，系统错误。");
            return "redirect:/contracts/pending-signing";
        }
    }

    // --- 新增：处理合同签订提交 ---
    @PostMapping("/sign")
    @PreAuthorize("hasAuthority('CON_SIGN_VIEW')")
    public String signContract(@RequestParam Long contractProcessId,
                               @RequestParam(required = false) String signingOpinion,
                               RedirectAttributes redirectAttributes,
                               Principal principal) {
        try {
            String username = principal.getName();
            contractService.signContract(contractProcessId, signingOpinion, username);
            redirectAttributes.addFlashAttribute("successMessage", "合同签订成功！");
            return "redirect:/contracts/pending-signing"; // 签订成功后重定向回待签订列表
        } catch (BusinessLogicException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/contracts/sign/" + contractProcessId; // 保持在当前页面，显示错误
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "合同签订失败，系统发生未知错误。");
            return "redirect:/contracts/pending-signing";
        }
    }
    // 待审批合同列表页面
    @GetMapping("/pending-approval")
    @PreAuthorize("hasAuthority('CON_APPROVE_VIEW')")
    public String pendingApprovalContracts(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String contractNameSearch,
            Model model,
            Principal principal
    ) {
        String username = principal.getName();
        Page<ContractProcess> pendingApprovals = contractService.getPendingProcessesForUser(
                username, ContractProcessType.APPROVAL, ContractProcessState.PENDING, contractNameSearch, pageable);
        
        model.addAttribute("pendingApprovals", pendingApprovals);
        model.addAttribute("contractNameSearch", contractNameSearch);
        return "pending-approval";
    }

    // 审批详情页面
    @GetMapping("/approval-details/{id}")
    @PreAuthorize("hasAuthority('CON_APPROVE_SUBMIT')")
    public String showApprovalDetails(@PathVariable Long id, Model model, Principal principal) {
        Contract contract = contractService.getContractById(id);
        if (contract == null) {
            throw new ResourceNotFoundException("合同不存在");
        }
        
        // 验证当前用户是否有权限审批该合同
        if (!contractService.canUserApproveContract(principal.getName(), id)) {
            throw new AccessDeniedException("您没有权限审批此合同");
        }
        
        model.addAttribute("contract", contract);
        return "approval-details";
    }

    // 处理审批提交
    @PostMapping("/approve/{id}")
    @PreAuthorize("hasAuthority('CON_APPROVE_SUBMIT')")
    public String approveContract(
            @PathVariable Long id,
            @RequestParam String decision,
            @RequestParam String comments,
            RedirectAttributes redirectAttributes,
            Principal principal
    ) {
        try {
            boolean isApproved = "APPROVED".equals(decision);
            contractService.processApproval(id, principal.getName(), isApproved, comments);
            
            String resultMessage = isApproved ? "合同审批通过" : "合同已拒绝";
            redirectAttributes.addFlashAttribute("successMessage", resultMessage);
            return "redirect:/contracts/pending-approval";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "审批处理失败：" + e.getMessage());
            return "redirect:/contracts/approval-details/" + id;
        }
    }
}