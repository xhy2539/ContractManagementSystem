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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
@RequestMapping("/contract-manager") // 控制器级别的基础路径已更新
public class ContractController {

    private final ContractService contractService;

    @Autowired
    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    // 显示起草合同页面
    // URL: /contract-manager/draft-contract
    @GetMapping("/draft-contract")
    @PreAuthorize("hasAuthority('CON_DRAFT_NEW')")
    public String showDraftContractPage(Model model) {
        model.addAttribute("contractDraftRequest", new ContractDraftRequest());
        // 确保移除了末尾多余的空格
        return "contract-manager/draft-contract"; // 视图路径正确
    }

    // 处理合同起草提交
    // POST URL: /contract-manager/draft (这个Mapping似乎与上面的GET不完全对应，通常会是 /contract-manager/draft-contract 或者 /contract-manager/drafts)
    // 如果您希望 POST 到 /contract-manager/draft，那么这个Mapping本身是OK的。
    // 但请注意，成功或失败后重定向的路径也需要与新的基础路径一致。
    @PostMapping("/draft")
    @PreAuthorize("hasAuthority('CON_DRAFT_NEW')")
    public String draftContract(
            @ModelAttribute("contractDraftRequest") @Valid ContractDraftRequest contractDraftRequest,
            BindingResult bindingResult,
            @RequestParam(value = "attachment", required = false) MultipartFile attachment,
            RedirectAttributes redirectAttributes,
            Model model,
            Principal principal
    ) {
        if (bindingResult.hasErrors()) {
            return "contract-manager/draft-contract"; // 视图路径正确
        }

        try {
            String username = principal.getName();
            Contract draftedContract = contractService.draftContract(contractDraftRequest, attachment, username);
            redirectAttributes.addFlashAttribute("successMessage", "合同" + draftedContract.getContractName() + "起草成功！");
            // 重定向到显示起草页面的URL
            return "redirect:/contract-manager/draft-contract"; // 修改1: 重定向到 GET /draft-contract

        } catch (BusinessLogicException e) {
            if (e.getMessage().contains("附件格式")) {
                model.addAttribute("attachmentError", e.getMessage());
            } else {
                model.addAttribute("errorMessage", e.getMessage());
            }
            model.addAttribute("contractDraftRequest", contractDraftRequest);
            return "contract-manager/draft-contract"; // 视图路径正确
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "文件上传失败，请重试。");
            // 重定向到显示起草页面的URL
            return "redirect:/contract-manager/draft-contract"; // 修改2: 重定向到 GET /draft-contract
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "起草合同失败，系统发生未知错误。");
            // 考虑重定向到仪表盘或一个更通用的错误页面
            return "redirect:/dashboard"; // 或者 "redirect:/contract-manager/errorPage" (如果errorPage也在此控制器下)
        }
    }

    // 假设的错误页面映射，如果需要，可以保留或调整
    // URL: /contract-manager/errorPage
    @GetMapping("/errorPage")
    public String errorPage(Model model) {
        model.addAttribute("errorMessage", "很抱歉，系统发生错误，请联系管理员。");
        // 假设 error.html 在 templates 根目录
        return "error"; // 如果 error.html 在 templates/contract-manager/ 下，则应为 "contract-manager/error"
    }

    // 待会签合同列表页面
    // URL: /contract-manager/pending-countersign
    @GetMapping("/pending-countersign")
    @PreAuthorize("hasAuthority('CON_CSIGN_VIEW')")
    public String pendingCountersignContracts(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String contractNameSearch,
            Model model,
            Principal principal
    ) {
        String username = principal.getName();
        Page<ContractProcess> pendingCountersigns = contractService.getPendingProcessesForUser(
                username, ContractProcessType.COUNTERSIGN, ContractProcessState.PENDING, contractNameSearch, pageable);
        model.addAttribute("pendingCountersigns", pendingCountersigns);
        model.addAttribute("contractNameSearch", contractNameSearch);
        return "contract-manager/pending-countersign"; // 视图路径正确
    }

    // 待签订合同列表页面
    // URL: /contract-manager/pending-signing
    @GetMapping("/pending-signing")
    @PreAuthorize("hasAuthority('CON_SIGN_VIEW')")
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
        return "contract-manager/pending-signing"; // 视图路径正确
    }

    // 显示合同签订详情页面
    // URL: /contract-manager/sign/{contractProcessId}
    @GetMapping("/sign/{contractProcessId}")
    @PreAuthorize("hasAuthority('CON_SIGN_VIEW')")
    public String showSignContractForm(@PathVariable Long contractProcessId, Model model, Principal principal, RedirectAttributes redirectAttributes) {
        try {
            String username = principal.getName();
            ContractProcess contractProcess = contractService.getContractProcessByIdAndOperator(
                    contractProcessId, username, ContractProcessType.SIGNING, ContractProcessState.PENDING);
            model.addAttribute("contractProcess", contractProcess);
            return "contract-manager/sign-contract"; // 视图路径正确
        } catch (BusinessLogicException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            // 重定向到待签订列表的URL
            return "redirect:/contract-manager/pending-signing"; // 修改3
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "加载签订页面失败，系统错误。");
            // 重定向到待签订列表的URL
            return "redirect:/contract-manager/pending-signing"; // 修改4
        }
    }

    // 处理合同签订提交
    // POST URL: /contract-manager/sign
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
            // 重定向到待签订列表的URL
            return "redirect:/contract-manager/pending-signing"; // 修改5
        } catch (BusinessLogicException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            // 重定向到显示签订页面的URL
            return "redirect:/contract-manager/sign/" + contractProcessId; // 修改6
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "合同签订失败，系统发生未知错误。");
            // 重定向到待签订列表的URL
            return "redirect:/contract-manager/pending-signing"; // 修改7
        }
    }

    // 待审批合同列表页面
    // URL: /contract-manager/pending-approval
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
        return "contract-manager/pending-approval"; // 视图路径正确
    }

    // 审批详情页面
    // URL: /contract-manager/approval-details/{id}
    @GetMapping("/approval-details/{id}")
    @PreAuthorize("hasAuthority('CON_APPROVE_SUBMIT')")
    public String showApprovalDetails(@PathVariable Long id, Model model, Principal principal) {
        Contract contract = contractService.getContractById(id);
        if (contract == null) {
            throw new ResourceNotFoundException("合同不存在");
        }
        if (!contractService.canUserApproveContract(principal.getName(), id)) {
            throw new AccessDeniedException("您没有权限审批此合同");
        }
        model.addAttribute("contract", contract);
        return "contract-manager/approval-details"; // 视图路径正确
    }

    // 处理审批提交
    // POST URL: /contract-manager/approve/{id}
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
            // 原代码是 redirect:/ending-approval，这里应该是 redirect:/contract-manager/pending-approval (列表页)
            return "redirect:/contract-manager/pending-approval"; // 修改8
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "审批处理失败：" + e.getMessage());
            // 原代码是 redirect:   /approval-details/" + id，注意前面的空格和斜杠
            // 应该重定向到审批详情页的正确URL
            return "redirect:/contract-manager/approval-details/" + id; // 修改9
        }
    }
}