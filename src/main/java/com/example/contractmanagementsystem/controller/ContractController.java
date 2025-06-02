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
            // 假设 ContractService 有一个 processCountersign 方法
            // ((ContractServiceImpl) contractService).processCountersign(contractProcessId, comments, principal.getName(), isApproved);
            // 由于不能直接转型，我们需要确保 ContractService 接口有相应方法，或者 ContractServiceImpl 有一个公共方法被正确调用
            // 暂时注释掉这一行，因为我们没有修改 ContractService 接口来添加 processCountersign
            // 你需要在 ContractService 和 ContractServiceImpl 中添加类似下面的方法：
            // void processCountersign(Long contractProcessId, String comments, String username, boolean isApproved);
            // 并由 ContractController 调用。鉴于我们之前关注的是附件上传，这里暂时不实现会签的具体逻辑。

            // 模拟成功
            // 注意：实际的会签逻辑需要在 ContractServiceImpl 中实现，包括更新 ContractProcess 和 Contract 状态
            redirectAttributes.addFlashAttribute("successMessage", "会签意见已成功提交 (模拟)。实际逻辑需在服务层实现。");
            return "redirect:/contract-manager/pending-countersign";
        } catch (BusinessLogicException | ResourceNotFoundException | AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "会签操作失败: " + e.getMessage());
            return "redirect:/contract-manager/countersign/" + contractProcessId;
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
                                          @ModelAttribute ContractDraftRequest contractDraftRequest, // 假设我们复用此DTO的部分字段或创建一个新的
                                          RedirectAttributes redirectAttributes,
                                          Principal principal,
                                          Model model) {
        try {
            String username = principal.getName();
            // finalizeContract 方法的签名需要调整，不再直接接收 MultipartFile
            // 它应该从 contractDraftRequest (或一个专门的 FinalizeRequest DTO) 中获取附件信息 (如果附件在定稿时可被替换)
            // 如果附件在定稿时不应改变，则从数据库加载合同，并使用其现有的 attachmentPath.
            // 如果附件可以在定稿时通过分块上传替换，则 AttachmentService 应处理，并通过一个标识符传给 finalizeContract

            // 简化：假设定稿时不直接处理文件上传，而是使用之前通过分块上传并记录在
            // contractDraftRequest.attachmentServerFileName (或类似字段) 的附件名
            // 这意味着 finalizeContract 方法可能需要新的参数或 DTO
            // Contract finalizedContract = contractService.finalizeContract(contractId, finalizationComments, newAttachment, username);

            // **临时修改：假设 finalizeContract 接收 ContractDraftRequest 来获取 attachmentServerFileName **
            // **这需要 ContractService.finalizeContract 接口和实现进行相应修改**
            // **或者，更合理的做法是，finalizeContract 应该只关心定稿意见，附件的替换应该是另一个独立的操作或通过 AttachmentService 完成**
            // **这里我们假设如果前端上传了新文件并通过JS设置了 contractDraftRequest.attachmentServerFileName，
            // 则 ContractService.finalizeContract 内部会使用它。否则使用合同已有的附件。**
            // **这是一个需要您根据具体业务逻辑完善的地方。**

            Contract finalizedContract = contractService.finalizeContract(
                    contractId,
                    finalizationComments,
                    contractDraftRequest.getAttachmentServerFileNames(),
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
        } catch (IOException e) { // 这个 catch 块现在更可能与 finalizeContract 内部逻辑有关（如果它还尝试了I/O）
            model.addAttribute("errorMessage", "附件处理失败: " + e.getMessage());
            try {
                Contract contractToDisplay = contractService.getContractForFinalization(contractId, principal.getName());
                model.addAttribute("contract", contractToDisplay);
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
            Contract contract = contractService.getContractById(contractId);
            boolean canActuallyApprove = contractService.canUserApproveContract(principal.getName(), contractId);
            model.addAttribute("canActuallyApprove", canActuallyApprove);

            if (contract.getStatus() != ContractStatus.PENDING_APPROVAL && contract.getStatus() != ContractStatus.REJECTED) {
                model.addAttribute("infoMessage", "提示：此合同当前状态为 “" + contract.getStatus().getDescription() + "”，可能并非处于标准的待审批环节。");
            }
            model.addAttribute("contract", contract);
            return "contract-manager/approval-details";
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "无法加载审批详情：" + e.getMessage());
            return "redirect:/contract-manager/pending-approval";
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("errorMessage","权限不足，无法查看审批详情。");
            return "redirect:/dashboard";
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
            return "redirect:/contract-manager/approval-details/" + contractId;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "审批过程中发生未知系统错误。");
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
    @PreAuthorize("hasAuthority('CON_SIGN_VIEW')") // 或者 'CONTRACT_SIGN_SUBMIT' 如果查看和提交权限不同
    public String showSignContractForm(@PathVariable Long contractProcessId, Model model, Principal principal, RedirectAttributes redirectAttributes) {
        try {
            String username = principal.getName();
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
    @PreAuthorize("hasAuthority('CONTRACT_SIGN_SUBMIT')") // 对应旧版中的权限
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
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String contractName,
            @RequestParam(required = false) String contractNumber,
            @RequestParam(required = false) String status,
            Model model,
            Principal principal) {

        Page<Contract> contractsPage = contractService.searchContracts(contractName, contractNumber, status, pageable);

        model.addAttribute("contractsPage", contractsPage);
        model.addAttribute("contractName", contractName); // 用于在视图中回显搜索条件
        model.addAttribute("contractNumber", contractNumber); // 用于在视图中回显搜索条件
        model.addAttribute("status", status); // 用于在视图中回显搜索条件
        model.addAttribute("listTitle", "合同列表查询");
        // 这里的视图名 "reports/contract-search" 是之前报告模块的，如果需要独立的 "我的合同" 页面，可以改名
        return "reports/contract-search";
    }
}