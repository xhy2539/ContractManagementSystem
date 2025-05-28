package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.dto.ContractDraftRequest;
import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.entity.ContractProcess;
import com.example.contractmanagementsystem.entity.ContractProcessState;
import com.example.contractmanagementsystem.entity.ContractProcessType;
import com.example.contractmanagementsystem.exception.BusinessLogicException;
import com.example.contractmanagementsystem.service.ContractService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page; // 正确的 Page 导入
import org.springframework.data.domain.Pageable; // 正确的 Pageable 导入
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
            redirectAttributes.addFlashAttribute("successMessage", "合同“" + draftedContract.getContractName() + "”起草成功！");
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
    @PreAuthorize("hasAuthority('CON_VIEW_COUNTERSIGN')")
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
        return "pending-countersign-contracts";
    }
}