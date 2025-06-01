package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.dto.ContractDraftRequest;
import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.entity.ContractProcess;
import com.example.contractmanagementsystem.entity.ContractProcessState;
import com.example.contractmanagementsystem.entity.ContractProcessType;
import com.example.contractmanagementsystem.entity.ContractStatus; // 确保导入 ContractStatus
import com.example.contractmanagementsystem.exception.BusinessLogicException;
import com.example.contractmanagementsystem.exception.ResourceNotFoundException;
import com.example.contractmanagementsystem.service.ContractService;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.AccessDeniedException; // 导入 Spring Security 的 AccessDeniedException
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
@RequestMapping("/contract-manager") // 控制器级别的基础路径
public class ContractController {

    private final ContractService contractService;

    @Autowired
    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    /**
     * 显示起草合同页面.
     * 用户需要 'CON_DRAFT_NEW' 权限.
     * @param model Spring MVC 模型对象.
     * @return 指向 'contract-manager/draft-contract.html' 模板的路径.
     */
    @GetMapping("/draft-contract")
    @PreAuthorize("hasAuthority('CON_DRAFT_NEW')")
    public String showDraftContractPage(Model model) {
        // 如果模型中已经存在 contractDraftRequest (例如，因为表单提交错误后返回当前页),
        // 则不再创建一个新的空对象覆盖它，以保留用户已输入的数据和错误信息。
        if (!model.containsAttribute("contractDraftRequest")) {
            model.addAttribute("contractDraftRequest", new ContractDraftRequest());
        }
        return "contract-manager/draft-contract"; // 视图路径
    }

    /**
     * 处理合同起草表单的提交.
     * 用户需要 'CON_DRAFT_NEW' 权限.
     * @param contractDraftRequest 包含合同起草数据的DTO，使用@Valid进行校验.
     * @param bindingResult 校验结果.
     * @param attachment (可选) 上传的合同附件.
     * @param redirectAttributes 用于在重定向时传递flash消息.
     * @param model Spring MVC 模型对象 (用于在当前页面显示错误).
     * @param principal 当前认证用户信息.
     * @return 成功则重定向到起草页，失败则返回起草页并显示错误.
     */
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
            // 如果存在JSR 303校验错误 (例如 @NotBlank, @NotNull),
            // Spring会自动将bindingResult添加到模型中，Thymeleaf的th:errors可以显示它们。
            // contractDraftRequest也通过@ModelAttribute自动添加回模型，保留用户输入。
            return "contract-manager/draft-contract"; // 返回到起草页面，显示校验错误
        }

        try {
            String username = principal.getName();
            Contract draftedContract = contractService.draftContract(contractDraftRequest, attachment, username);
            redirectAttributes.addFlashAttribute("successMessage", "合同 “" + draftedContract.getContractName() + "” (编号: " + draftedContract.getContractNumber() + ") 已成功起草！");
            return "redirect:/contract-manager/draft-contract"; // 操作成功后重定向，避免表单重复提交

        } catch (BusinessLogicException e) {
            // 捕获服务层抛出的特定业务逻辑错误 (例如日期冲突，客户名/编号已存在等)
            model.addAttribute("errorMessage", e.getMessage()); // 将错误信息添加到模型，在当前页面显示
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("附件格式")) {
                model.addAttribute("attachmentError", e.getMessage()); // 对附件格式错误进行特殊处理
            }
            // contractDraftRequest 已通过@ModelAttribute保留在模型中
            return "contract-manager/draft-contract"; // 返回到起草页面，显示错误信息
        } catch (IOException e) {
            // 捕获文件上传相关的IO错误
            model.addAttribute("errorMessage", "文件上传失败，请重试：" + e.getMessage());
            // contractDraftRequest 已通过@ModelAttribute保留在模型中
            return "contract-manager/draft-contract"; // 返回到起草页面，显示错误信息
        } catch (Exception e) {
            // 捕获其他所有未预料到的运行时异常
            model.addAttribute("errorMessage", "起草合同失败，发生未知系统错误。详情请查看服务器日志。");
            e.printStackTrace(); // 在开发阶段，打印堆栈跟踪到服务器控制台，便于调试
            // contractDraftRequest 已通过@ModelAttribute保留在模型中
            return "contract-manager/draft-contract"; // 返回到起草页面，显示通用错误信息
        }
    }

    /**
     * 显示待会签合同列表页面.
     * 用户需要 'CON_CSIGN_VIEW' 权限.
     * @param pageable 分页参数，按合同更新时间降序.
     * @param contractNameSearch (可选) 按合同名称搜索.
     * @param model 模型对象.
     * @param principal 当前用户信息.
     * @return 'contract-manager/pending-countersign.html' 视图.
     */
    @GetMapping("/pending-countersign")
    @PreAuthorize("hasAuthority('CON_CSIGN_VIEW')")
    public String pendingCountersignContracts(
            @PageableDefault(size = 10, sort = "contract.updatedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String contractNameSearch,
            Model model,
            Principal principal
    ) {
        String username = principal.getName();
        Page<ContractProcess> pendingCountersigns = contractService.getPendingProcessesForUser(
                username, ContractProcessType.COUNTERSIGN, ContractProcessState.PENDING, contractNameSearch, pageable);
        model.addAttribute("pendingCountersigns", pendingCountersigns);
        model.addAttribute("contractNameSearch", contractNameSearch);
        model.addAttribute("listTitle", "待会签合同");
        // 注意：实际的会签操作页面 (如 countersign-details.html) 和提交逻辑需要另外实现
        return "contract-manager/pending-countersign";
    }


    // --- 新增“定稿合同”相关 Controller 方法 ---

    /**
     * 显示待定稿合同列表页面.
     * 用户需要 'CON_FINAL_VIEW' 权限才能访问.
     * @param pageable 分页参数，默认按合同的最后更新时间降序排列.
     * @param contractNameSearch (可选) 用于按合同名称进行模糊搜索的关键字.
     * @param model Spring MVC 模型对象，用于向视图传递数据.
     * @param principal Spring Security提供的当前用户信息.
     * @return 视图名称，指向 'contract-manager/pending-finalization.html'.
     */
    @GetMapping("/pending-finalization")
    @PreAuthorize("hasAuthority('CON_FINAL_VIEW')")
    public String showPendingFinalizationContracts(
            @PageableDefault(size = 10, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String contractNameSearch,
            Model model,
            Principal principal) {

        String username = principal.getName();
        Page<Contract> pendingFinalizationContracts = contractService.getContractsPendingFinalizationForUser(username, contractNameSearch, pageable);

        model.addAttribute("pendingFinalizationContracts", pendingFinalizationContracts);
        model.addAttribute("contractNameSearch", contractNameSearch);
        model.addAttribute("listTitle", "待定稿合同");
        model.addAttribute("finalizeBaseUrl", "/contract-manager/finalize"); // 用于模板中构建定稿链接

        return "contract-manager/pending-finalization"; // 指向待定稿列表的Thymeleaf模板
    }

    /**
     * 显示单个合同的定稿页面/表单，供用户审查和执行定稿操作.
     * 用户需要拥有 'CON_FINAL_VIEW' (查看) 或 'CON_FINAL_SUBMIT' (提交定稿) 权限.
     * @param contractId 要进行定稿操作的合同ID (从URL路径中获取).
     * @param model Spring MVC 模型对象.
     * @param principal 当前认证用户信息.
     * @param redirectAttributes 用于在发生错误并重定向时传递flash消息 (例如错误提示).
     * @return 视图名称，成功则指向 'contract-manager/finalize-contract.html'，失败则重定向到列表页.
     */
    @GetMapping("/finalize/{contractId}")
    @PreAuthorize("hasAuthority('CON_FINAL_VIEW') or hasAuthority('CON_FINAL_SUBMIT')")
    public String showFinalizeContractForm(@PathVariable Long contractId,
                                           Model model,
                                           Principal principal,
                                           RedirectAttributes redirectAttributes) {
        try {
            String username = principal.getName();
            Contract contract = contractService.getContractForFinalization(contractId, username);
            model.addAttribute("contract", contract);
            // 如果需要在表单中预填可修改的字段，可以创建一个DTO
            // model.addAttribute("finalizeRequestDto", new SomeDto(contract));
            return "contract-manager/finalize-contract"; // 指向合同定稿操作页面的Thymeleaf模板
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "无法加载定稿页面：指定的合同未找到。");
            return "redirect:/contract-manager/pending-finalization";
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "权限不足：您无权对该合同执行定稿操作。");
            return "redirect:/contract-manager/pending-finalization";
        } catch (BusinessLogicException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "操作失败：" + e.getMessage());
            return "redirect:/contract-manager/pending-finalization";
        }
    }

    /**
     * 处理用户提交的合同定稿操作.
     * 用户需要 'CON_FINAL_SUBMIT' 权限.
     * @param contractId 要定稿的合同ID.
     * @param finalizationComments 用户填写的定稿意见 (可选).
     * @param newAttachment 用户上传的新附件 (可选, 会替换旧附件).
     * @param redirectAttributes 用于在重定向时传递flash消息.
     * @param principal 当前用户信息.
     * @param model Spring MVC模型 (用于在IO异常等无法重定向的错误时，返回当前页面并显示错误).
     * @return 操作成功则重定向到待定稿列表页，失败则可能重定向回定稿详情页或列表页.
     */
    @PostMapping("/finalize/{contractId}")
    @PreAuthorize("hasAuthority('CON_FINAL_SUBMIT')")
    public String processFinalizeContract(@PathVariable Long contractId,
                                          @RequestParam(required = false) String finalizationComments,
                                          @RequestParam(value = "newAttachment", required = false) MultipartFile newAttachment,
                                          RedirectAttributes redirectAttributes,
                                          Principal principal,
                                          Model model) {
        try {
            String username = principal.getName();
            Contract finalizedContract = contractService.finalizeContract(contractId, finalizationComments, newAttachment, username);
            redirectAttributes.addFlashAttribute("successMessage", "合同 “" + finalizedContract.getContractName() + "” (ID: " + contractId + ") 已成功定稿，并进入下一审批流程。");
            return "redirect:/contract-manager/pending-finalization"; // 成功后重定向到待定稿列表
        } catch (BusinessLogicException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "定稿失败: " + e.getMessage());
            return "redirect:/contract-manager/finalize/" + contractId; // 业务逻辑错误，重定向回详情页
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "定稿失败: 权限不足。");
            return "redirect:/contract-manager/finalize/" + contractId; // 权限错误，重定向回详情页
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "定稿失败: " + e.getMessage());
            return "redirect:/contract-manager/pending-finalization"; // 资源未找到，重定向回列表页
        } catch (IOException e) {
            model.addAttribute("errorMessage", "附件上传或处理失败: " + e.getMessage());
            // 发生IO异常时，需要重新加载合同数据以再次显示表单
            try {
                Contract contractToDisplay = contractService.getContractForFinalization(contractId, principal.getName());
                model.addAttribute("contract", contractToDisplay);
                // 如果有表单绑定的DTO，也应重新设置
                // model.addAttribute("finalizeRequestDto", someDto);
            } catch (Exception loadEx) {
                redirectAttributes.addFlashAttribute("errorMessage", "附件处理失败，且重新加载合同信息以显示错误时也发生错误：" + loadEx.getMessage());
                return "redirect:/contract-manager/pending-finalization";
            }
            return "contract-manager/finalize-contract"; // 返回到定稿表单页面，显示IO错误
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "合同定稿过程中发生未知系统错误，请联系管理员。");
            e.printStackTrace(); // 记录详细错误到日志
            return "redirect:/contract-manager/finalize/" + contractId; // 尝试重定向回详情页
        }
    }

    // --- 待审批合同 ---
    // (此部分及后续方法与之前版本基本一致，但对错误处理和信息提示做了一些微调)
    @GetMapping("/pending-approval")
    @PreAuthorize("hasAuthority('CON_APPROVE_VIEW')")
    public String pendingApprovalContracts(
            @PageableDefault(size = 10, sort = "contract.updatedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String contractNameSearch,
            Model model,
            Principal principal
    ) {
        String username = principal.getName();
        Page<ContractProcess> pendingApprovals = contractService.getPendingProcessesForUser(
                username, ContractProcessType.APPROVAL, ContractProcessState.PENDING, contractNameSearch, pageable);
        model.addAttribute("pendingApprovals", pendingApprovals);
        model.addAttribute("contractNameSearch", contractNameSearch);
        model.addAttribute("listTitle", "待审批合同");
        return "contract-manager/pending-approval";
    }

    @GetMapping("/approval-details/{contractId}")
    @PreAuthorize("hasAuthority('CON_APPROVE_VIEW') or hasAuthority('CON_APPROVE_SUBMIT')")
    public String showApprovalDetails(@PathVariable Long contractId, Model model, Principal principal, RedirectAttributes redirectAttributes) {
        try {
            Contract contract = contractService.getContractById(contractId); // 获取合同基本信息
            boolean canActuallyApprove = contractService.canUserApproveContract(principal.getName(), contractId);
            model.addAttribute("canActuallyApprove", canActuallyApprove); // 将能否操作的状态传给模板

            if (contract.getStatus() != ContractStatus.PENDING_APPROVAL && contract.getStatus() != ContractStatus.REJECTED) {
                model.addAttribute("infoMessage", "提示：此合同当前状态为 “" + contract.getStatus().getDescription() + "”，可能并非处于标准的待审批环节。");
            }
            model.addAttribute("contract", contract);
            return "contract-manager/approval-details";
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "无法加载审批详情：" + e.getMessage());
            return "redirect:/contract-manager/pending-approval";
        } catch (AccessDeniedException e) { // 虽然 @PreAuthorize 应该先捕获，但以防万一
            redirectAttributes.addFlashAttribute("errorMessage","权限不足，无法查看审批详情。");
            return "redirect:/dashboard";
        }
    }

    @PostMapping("/approve/{contractId}")
    @PreAuthorize("hasAuthority('CON_APPROVE_SUBMIT')")
    public String approveContract(
            @PathVariable Long contractId,
            @RequestParam String decision, // "APPROVED" or "REJECTED"
            @RequestParam(required=false) String comments,
            RedirectAttributes redirectAttributes,
            Principal principal
    ) {
        try {
            boolean isApproved = "APPROVED".equalsIgnoreCase(decision);
            contractService.processApproval(contractId, principal.getName(), isApproved, comments);
            redirectAttributes.addFlashAttribute("successMessage", "合同 (ID: " + contractId + ") 已成功" + (isApproved ? "批准" : "拒绝") + "。");
            return "redirect:/contract-manager/pending-approval";
        } catch (BusinessLogicException | AccessDeniedException | ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "审批操作失败: " + e.getMessage());
            return "redirect:/contract-manager/approval-details/" + contractId;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "审批过程中发生未知系统错误，请联系管理员。");
            e.printStackTrace();
            return "redirect:/contract-manager/approval-details/" + contractId;
        }
    }

    // --- 待签订合同 ---
    @GetMapping("/pending-signing")
    @PreAuthorize("hasAuthority('CON_SIGN_VIEW')")
    public String pendingSigningContracts(
            @PageableDefault(size = 10, sort = "contract.updatedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String contractNameSearch,
            Model model,
            Principal principal
    ) {
        String username = principal.getName();
        Page<ContractProcess> pendingSignings = contractService.getPendingProcessesForUser(
                username, ContractProcessType.SIGNING, ContractProcessState.PENDING, contractNameSearch, pageable);
        model.addAttribute("pendingSignings", pendingSignings);
        model.addAttribute("contractNameSearch", contractNameSearch);
        model.addAttribute("listTitle", "待签订合同");
        return "contract-manager/pending-signing";
    }

    @GetMapping("/sign/{contractProcessId}")
    @PreAuthorize("hasAuthority('CON_SIGN_VIEW')")
    public String showSignContractForm(@PathVariable Long contractProcessId, Model model, Principal principal, RedirectAttributes redirectAttributes) {
        try {
            String username = principal.getName();
            ContractProcess contractProcess = contractService.getContractProcessByIdAndOperator(
                    contractProcessId, username, ContractProcessType.SIGNING, ContractProcessState.PENDING);
            model.addAttribute("contractProcess", contractProcess);
            return "contract-manager/sign-contract";
        } catch (ResourceNotFoundException | BusinessLogicException | AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/contract-manager/pending-signing";
        }
    }

    @PostMapping("/sign")
    // 权限点 CONTRACT_SIGN_SUBMIT 对应 DataInitializer.java 中的 "提交签订信息"
    @PreAuthorize("hasAuthority('CONTRACT_SIGN_SUBMIT')")
    public String signContract(@RequestParam Long contractProcessId,
                               @RequestParam(required = false) String signingOpinion,
                               RedirectAttributes redirectAttributes,
                               Principal principal) {
        try {
            String username = principal.getName();
            contractService.signContract(contractProcessId, signingOpinion, username);
            redirectAttributes.addFlashAttribute("successMessage", "合同已成功标记为签订！");
            return "redirect:/contract-manager/pending-signing";
        } catch (ResourceNotFoundException | BusinessLogicException | AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "签订失败: " + e.getMessage());
            return "redirect:/contract-manager/sign/" + contractProcessId;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "签订过程中发生未知错误，请联系管理员。");
            e.printStackTrace();
            return "redirect:/contract-manager/pending-signing";
        }
    }

    // 查看“我的合同” (或所有合同，取决于权限和业务逻辑)
    @GetMapping("/view-all")
    @PreAuthorize("hasAuthority('CON_VIEW_MY') or hasRole('ROLE_ADMIN')")
    public String viewAllContracts(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String contractName,
            @RequestParam(required = false) String contractNumber,
            @RequestParam(required = false) String status,
            Model model,
            Principal principal) {

        // 此处调用通用的搜索方法。如果 CON_VIEW_MY 权限意味着只能看自己起草的，
        // 那么服务层 contractService.searchContracts 需要能接收一个 drafterUsername 参数，
        // 或者您在这里根据用户角色决定调用哪个服务方法。
        // 为简化，此处仍使用现有 searchContracts，依赖 @PreAuthorize 控制整体访问。
        Page<Contract> contractsPage = contractService.searchContracts(contractName, contractNumber, status, pageable);

        model.addAttribute("contractsPage", contractsPage);
        model.addAttribute("contractName", contractName);
        model.addAttribute("contractNumber", contractNumber);
        model.addAttribute("status", status); // 回传状态以便搜索表单能保持
        model.addAttribute("listTitle", "合同列表查询"); // 页面标题
        // 复用已有的合同查询页面 reports/contract-search.html
        // 您可能需要根据此上下文调整该模板，或者创建一个新的模板
        return "reports/contract-search";
    }
}