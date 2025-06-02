package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.dto.ContractDraftRequest;
import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.entity.ContractProcess;
import com.example.contractmanagementsystem.entity.ContractProcessState;
import com.example.contractmanagementsystem.entity.ContractProcessType;
import com.example.contractmanagementsystem.entity.ContractStatus;
import com.example.contractmanagementsystem.exception.BusinessLogicException;
import com.example.contractmanagementsystem.exception.ResourceNotFoundException;
import com.example.contractmanagementsystem.service.ContractService;
// import com.example.contractmanagementsystem.service.ContractServiceImpl; // ContractServiceImpl 通常不直接在Controller中引用其实现类

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication; // 新增导入
import org.springframework.security.core.GrantedAuthority; // 新增导入
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
// import org.springframework.web.multipart.MultipartFile; // 不再需要这个导入
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException; // 保留给其他可能抛出IOException的方法
import java.security.Principal;

@Controller
@RequestMapping("/contract-manager")
public class ContractController {

    private final ContractService contractService;

    @Autowired
    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    @GetMapping("/draft-contract")
    @PreAuthorize("hasAuthority('CON_DRAFT_NEW')")
    public String showDraftContractPage(Model model) {
        if (!model.containsAttribute("contractDraftRequest")) {
            model.addAttribute("contractDraftRequest", new ContractDraftRequest());
        }
        return "contract-manager/draft-contract";
    }

    @PostMapping("/draft")
    @PreAuthorize("hasAuthority('CON_DRAFT_NEW')")
    public String draftContract(
            @ModelAttribute("contractDraftRequest") @Valid ContractDraftRequest contractDraftRequest,
            BindingResult bindingResult,
            // @RequestParam(value = "attachment", required = false) MultipartFile attachment, // <--- 移除此参数
            RedirectAttributes redirectAttributes,
            Model model,
            Principal principal
    ) {
        if (bindingResult.hasErrors()) {
            // 如果前端没有正确传递 selectedCustomerId，这里可能会报错
            // 如果 attachmentServerFileName 是必需的（比如业务逻辑要求必须有附件），也需要校验
            // 但当前附件是可选的，所以 contractDraftRequest.getAttachmentServerFileName() 可以为 null 或空
            model.addAttribute("contractDraftRequest", contractDraftRequest); // 将包含错误的对象放回模型
            return "contract-manager/draft-contract";
        }
        try {
            String username = principal.getName();
            // 调用 ContractService.draftContract，它现在不直接处理 MultipartFile
            // 它会依赖 contractDraftRequest 中的 attachmentServerFileName (如果前端已通过分块上传并设置了此值)
            Contract draftedContract = contractService.draftContract(contractDraftRequest, username); // <--- 修改了调用
            redirectAttributes.addFlashAttribute("successMessage", "合同 “" + draftedContract.getContractName() + "” (编号: " + draftedContract.getContractNumber() + ") 已成功起草，等待分配！");
            return "redirect:/contract-manager/draft-contract"; // 成功后重定向到起草页，显示成功消息
        } catch (BusinessLogicException e) {
            model.addAttribute("errorMessage", e.getMessage());
            // 如果错误与附件相关（虽然现在附件名是预设的，但保留以防万一）
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("附件")) {
                model.addAttribute("attachmentError", e.getMessage());
            }
            model.addAttribute("contractDraftRequest", contractDraftRequest);
            return "contract-manager/draft-contract";
        } catch (ResourceNotFoundException e) {
            model.addAttribute("errorMessage", "起草合同失败：" + e.getMessage());
            model.addAttribute("contractDraftRequest", contractDraftRequest);
            return "contract-manager/draft-contract";
        }
        // IOException 现在不太可能由 draftContract 直接抛出，因为文件处理已移至 AttachmentService
        // 但如果 ContractService 的其他部分可能抛出，可以保留
        catch (IOException e) { // 这个 catch 块可能不再那么相关，除非 service 层还有其他IO操作
            model.addAttribute("errorMessage", "处理请求时发生I/O错误：" + e.getMessage());
            model.addAttribute("contractDraftRequest", contractDraftRequest);
            return "contract-manager/draft-contract";
        }
        catch (Exception e) {
            model.addAttribute("errorMessage", "起草合同失败，发生未知系统错误。");
            model.addAttribute("contractDraftRequest", contractDraftRequest);
            e.printStackTrace(); // 打印堆栈以供调试
            return "contract-manager/draft-contract";
        }
    }

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
        return "contract-manager/pending-countersign";
    }

    // 显示会签操作页面
    @GetMapping("/countersign/{contractProcessId}")
    @PreAuthorize("hasAuthority('CON_CSIGN_SUBMIT')")
    public String showCountersignForm(@PathVariable Long contractProcessId, Model model, Principal principal, RedirectAttributes redirectAttributes) {
        try {
            ContractProcess process = contractService.getContractProcessByIdAndOperator(
                    contractProcessId,
                    principal.getName(),
                    ContractProcessType.COUNTERSIGN,
                    ContractProcessState.PENDING
            );
            model.addAttribute("contractProcess", process);
            model.addAttribute("contract", process.getContract());
            return "contract-manager/countersign-contract"; // 假设会签页面名为 countersign-contract.html
        } catch (ResourceNotFoundException | BusinessLogicException | AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "加载会签页面失败: " + e.getMessage());
            return "redirect:/contract-manager/pending-countersign";
        }
    }

    // 处理会签提交
    @PostMapping("/countersign/submit")
    @PreAuthorize("hasAuthority('CON_CSIGN_SUBMIT')")
    public String processCountersignAction(
            @RequestParam Long contractProcessId,
            @RequestParam String decision, // "APPROVED" 或 "REJECTED"
            @RequestParam(required = false) String comments,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        try {
            boolean isApproved = "APPROVED".equalsIgnoreCase(decision);
            // 这里需要调用 ContractService 中的一个方法来处理会签逻辑
            contractService.processCountersign(contractProcessId, comments, principal.getName(), isApproved); // 调用服务层方法
            redirectAttributes.addFlashAttribute("successMessage", "会签意见已成功提交。");
            return "redirect:/contract-manager/pending-countersign";
        } catch (BusinessLogicException | ResourceNotFoundException | AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "会签操作失败: " + e.getMessage());
            return "redirect:/contract-manager/countersign/" + contractProcessId; // 返回会签表单页并显示错误
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "会签过程中发生未知系统错误。");
            e.printStackTrace();
            return "redirect:/contract-manager/pending-countersign";
        }
    }


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
        model.addAttribute("finalizeBaseUrl", "/contract-manager/finalize"); // 用于构建链接
        return "contract-manager/pending-finalization";
    }

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
            // 为表单绑定准备一个空的DTO对象，如果表单需要提交附件信息等
            model.addAttribute("contractDraftRequest", new ContractDraftRequest());
            return "contract-manager/finalize-contract";
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

    @PostMapping("/finalize/{contractId}")
    @PreAuthorize("hasAuthority('CON_FINAL_SUBMIT')")
    public String processFinalizeContract(@PathVariable Long contractId,
                                          @RequestParam(required = false) String finalizationComments,
                                          // MultipartFile 参数已在前端处理，后端通过 DTO 中的 attachmentServerFileName 获取
                                          // @RequestParam(value = "newAttachment", required = false) MultipartFile newAttachment,
                                          @ModelAttribute("contractDraftRequest") ContractDraftRequest contractDraftRequest, // 接收表单中的附件信息
                                          BindingResult bindingResult, // 如果 ContractDraftRequest 中有校验
                                          RedirectAttributes redirectAttributes,
                                          Principal principal,
                                          Model model) {

        // 如果 contractDraftRequest 有校验注解，可以在这里处理 bindingResult
        if (bindingResult.hasErrors()) {
            // 重新加载合同信息以显示表单
            try {
                Contract contractToDisplay = contractService.getContractForFinalization(contractId, principal.getName());
                model.addAttribute("contract", contractToDisplay);
            } catch (Exception loadEx) {
                redirectAttributes.addFlashAttribute("errorMessage", "表单校验失败，且重新加载合同信息时也发生错误：" + loadEx.getMessage());
                return "redirect:/contract-manager/pending-finalization";
            }
            model.addAttribute("errorMessage", "表单提交无效，请检查输入。");
            // 保留 contractDraftRequest 以便回显错误
            return "contract-manager/finalize-contract";
        }

        try {
            String username = principal.getName();
            Contract finalizedContract = contractService.finalizeContract(
                    contractId,
                    finalizationComments,
                    contractDraftRequest.getAttachmentServerFileNames(), // 从DTO获取附件名列表
                    username
            );


            redirectAttributes.addFlashAttribute("successMessage", "合同 “" + finalizedContract.getContractName() + "” (ID: " + contractId + ") 已成功定稿，并进入下一审批流程。");
            return "redirect:/contract-manager/pending-finalization";
        } catch (BusinessLogicException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "定稿失败: " + e.getMessage());
            return "redirect:/contract-manager/finalize/" + contractId;
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "定稿失败: 权限不足。");
            return "redirect:/contract-manager/finalize/" + contractId;
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "定稿失败: " + e.getMessage());
            return "redirect:/contract-manager/pending-finalization";
        } catch (IOException e) {
            model.addAttribute("errorMessage", "附件处理失败: " + e.getMessage());
            try {
                Contract contractToDisplay = contractService.getContractForFinalization(contractId, principal.getName());
                model.addAttribute("contract", contractToDisplay);
                // 确保 contractDraftRequest 再次被添加到模型中，以便表单可以重新填充
                model.addAttribute("contractDraftRequest", contractDraftRequest);
            } catch (Exception loadEx) {
                redirectAttributes.addFlashAttribute("errorMessage", "附件处理失败，且重新加载合同信息以显示错误时也发生错误：" + loadEx.getMessage());
                return "redirect:/contract-manager/pending-finalization";
            }
            return "contract-manager/finalize-contract";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "合同定稿过程中发生未知系统错误。");
            e.printStackTrace();
            return "redirect:/contract-manager/finalize/" + contractId;
        }
    }

    // --- 待审批合同 ---
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
            // 检查当前用户是否是这个合同的当前待审批人之一
            boolean canActuallyApprove = contractService.canUserApproveContract(principal.getName(), contractId);
            model.addAttribute("canActuallyApprove", canActuallyApprove);

            // 检查合同状态是否适合审批
            if (contract.getStatus() != ContractStatus.PENDING_APPROVAL && contract.getStatus() != ContractStatus.REJECTED /* 允许查看已拒绝的 */) {
                // 如果合同不是待审批状态，可能只是查看，或者已经被处理
                model.addAttribute("infoMessage", "提示：此合同当前状态为 “" + contract.getStatus().getDescription() + "”，可能并非处于标准的待审批环节。");
            }
            model.addAttribute("contract", contract);
            return "contract-manager/approval-details";
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "无法加载审批详情：" + e.getMessage());
            return "redirect:/contract-manager/pending-approval";
        } catch (AccessDeniedException e) { // 虽然PreAuthorize应该先捕获，但以防万一
            redirectAttributes.addFlashAttribute("errorMessage","权限不足，无法查看审批详情。");
            return "redirect:/dashboard"; // 或其他合适的页面
        }
    }


    @PostMapping("/approve/{contractId}")
    @PreAuthorize("hasAuthority('CON_APPROVE_SUBMIT')")
    public String approveContract(
            @PathVariable Long contractId,
            @RequestParam String decision, // "APPROVED" 或 "REJECTED"
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
            // 返回到详情页，这样用户可以看到上下文和错误
            return "redirect:/contract-manager/approval-details/" + contractId;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "审批过程中发生未知系统错误。");
            e.printStackTrace(); // 仅用于调试
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
    @PreAuthorize("hasAuthority('CON_SIGN_VIEW') or hasAuthority('CONTRACT_SIGN_SUBMIT')") // 允许查看和提交的人访问表单
    public String showSignContractForm(@PathVariable Long contractProcessId, Model model, Principal principal, RedirectAttributes redirectAttributes) {
        try {
            String username = principal.getName();
            // 获取流程信息，服务层会校验用户、类型和状态
            ContractProcess contractProcess = contractService.getContractProcessByIdAndOperator(
                    contractProcessId, username, ContractProcessType.SIGNING, ContractProcessState.PENDING);

            model.addAttribute("contractProcess", contractProcess);
            // model.addAttribute("contract", contractProcess.getContract()); // 如果页面需要合同的更多信息
            return "contract-manager/sign-contract";
        } catch (ResourceNotFoundException | BusinessLogicException | AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/contract-manager/pending-signing";
        }
    }

    @PostMapping("/sign") // 与HTML表单的 th:action="@{/contracts/sign}" 匹配，但基础路径是 /contract-manager
    @PreAuthorize("hasAuthority('CONTRACT_SIGN_SUBMIT')") // 对应旧版中的权限，确保此权限存在或改为 CON_SIGN_SUBMIT
    public String signContract(@RequestParam Long contractProcessId, // 确保与表单中的 name="contractProcessId" 匹配
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
            // 如果表单页面的路径是 /contract-manager/sign/{id}
            return "redirect:/contract-manager/sign/" + contractProcessId;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "签订过程中发生未知错误。");
            e.printStackTrace(); // 仅用于调试
            return "redirect:/contract-manager/pending-signing";
        }
    }

    @GetMapping("/view-all")
    @PreAuthorize("hasAuthority('CON_VIEW_MY') or hasRole('ROLE_ADMIN')")
    public String viewAllContracts(
            Authentication authentication, // 获取认证信息
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String contractName,
            @RequestParam(required = false) String contractNumber,
            @RequestParam(required = false) String status,
            Model model) {

        String currentUsername = authentication.getName(); // 获取当前用户名
        // 检查用户是否为管理员
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(ga -> ga.getAuthority().equals("ROLE_ADMIN"));

        // 调用已修改的服务方法
        Page<Contract> contractsPage = contractService.searchContracts(currentUsername, isAdmin, contractName, contractNumber, status, pageable);

        model.addAttribute("contractsPage", contractsPage);
        model.addAttribute("contractName", contractName); // 用于在视图中回显搜索条件
        model.addAttribute("contractNumber", contractNumber); // 用于在视图中回显搜索条件
        model.addAttribute("status", status); // 用于在视图中回显搜索条件
        model.addAttribute("listTitle", "合同列表查询");
        // 这里的视图名 "reports/contract-search" 是之前报告模块的，如果需要独立的 "我的合同" 页面，可以改名
        return "reports/contract-search";
    }
}