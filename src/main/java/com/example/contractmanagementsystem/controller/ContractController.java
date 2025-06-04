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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

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
// import org.springframework.security.core.GrantedAuthority; // 未在此控制器中直接使用，可移除
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
// import java.util.Collections; // 未在此控制器中直接使用，可移除
import java.util.List;

@Controller
@RequestMapping("/contract-manager")
public class ContractController {

    private static final Logger logger = LoggerFactory.getLogger(ContractController.class);
    private final ContractService contractService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ContractController(ContractService contractService, ObjectMapper objectMapper) {
        this.contractService = contractService;
        this.objectMapper = objectMapper;
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
            RedirectAttributes redirectAttributes,
            Model model,
            Principal principal
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("contractDraftRequest", contractDraftRequest);
            return "contract-manager/draft-contract";
        }
        try {
            String username = principal.getName();
            Contract draftedContract = contractService.draftContract(contractDraftRequest, username);
            redirectAttributes.addFlashAttribute("successMessage", "合同 “" + draftedContract.getContractName() + "” (编号: " + draftedContract.getContractNumber() + ") 已成功起草，等待分配！");
            return "redirect:/contract-manager/draft-contract";
        } catch (BusinessLogicException e) {
            model.addAttribute("errorMessage", e.getMessage());
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
        catch (IOException e) {
            model.addAttribute("errorMessage", "处理请求时发生I/O错误：" + e.getMessage());
            model.addAttribute("contractDraftRequest", contractDraftRequest);
            return "contract-manager/draft-contract";
        }
        catch (Exception e) {
            model.addAttribute("errorMessage", "起草合同失败，发生未知系统错误。");
            model.addAttribute("contractDraftRequest", contractDraftRequest);
            logger.error("起草合同未知错误", e);
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
        model.addAttribute("contractNameSearch", contractNameSearch != null ? contractNameSearch : "");
        model.addAttribute("listTitle", "待会签合同");
        return "contract-manager/pending-countersign";
    }

    @GetMapping("/countersign/{contractProcessId}")
    @PreAuthorize("hasAuthority('CON_CSIGN_VIEW') or hasAuthority('CON_CSIGN_SUBMIT')")
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
            return "contract-manager/countersign-contract";
        } catch (ResourceNotFoundException | BusinessLogicException | AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "加载会签页面失败: " + e.getMessage());
            return "redirect:/contract-manager/pending-countersign";
        }
    }

    @PostMapping("/countersign/submit")
    @PreAuthorize("hasAuthority('CON_CSIGN_SUBMIT')")
    public String processCountersignAction(
            @RequestParam Long contractProcessId,
            @RequestParam String decision,
            @RequestParam(required = false) String comments,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        try {
            boolean isApproved = "APPROVED".equalsIgnoreCase(decision);
            contractService.processCountersign(contractProcessId, comments, principal.getName(), isApproved);
            redirectAttributes.addFlashAttribute("successMessage", "会签意见已成功提交。");
            return "redirect:/contract-manager/pending-countersign";
        } catch (BusinessLogicException | ResourceNotFoundException | AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "会签操作失败: " + e.getMessage());
            return "redirect:/contract-manager/countersign/" + contractProcessId;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "会签过程中发生未知系统错误。");
            logger.error("处理会签提交未知错误", e);
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

        java.util.Map<String, Object> additionalParamsMap = new java.util.HashMap<>();
        if (contractNameSearch != null && !contractNameSearch.isEmpty()) {
            additionalParamsMap.put("contractNameSearch", contractNameSearch);
        }
        model.addAttribute("additionalParamsMap", additionalParamsMap); // 将Map传递给模板
        // 为了向后兼容或如果其他地方仍然直接使用 contractNameSearch, 可以保留它
        model.addAttribute("contractNameSearch", contractNameSearch != null ? contractNameSearch : "");


        model.addAttribute("listTitle", "待定稿合同");
        model.addAttribute("finalizeBaseUrl", "/contract-manager/finalize");
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

            List<ContractProcess> countersignOpinions = contractService.getContractCountersignOpinions(contractId);
            model.addAttribute("countersignOpinions", countersignOpinions);

            ContractDraftRequest draftRequest = new ContractDraftRequest();
            List<String> currentAttachmentFileNames = new ArrayList<>();

            if (contract != null) {
                draftRequest.setUpdatedContent(contract.getContent());

                String attachmentPathJson = contract.getAttachmentPath();
                if (attachmentPathJson != null && !attachmentPathJson.trim().isEmpty() &&
                        !attachmentPathJson.equals("[]") && !attachmentPathJson.equalsIgnoreCase("null")) {
                    try {
                        currentAttachmentFileNames = objectMapper.readValue(attachmentPathJson, new TypeReference<List<String>>(){});
                    } catch (JsonProcessingException e) {
                        logger.warn("加载定稿表单时解析附件JSON失败 (Contract ID: {}): {}", contractId, e.getMessage());
                    }
                }
            }
            draftRequest.setAttachmentServerFileNames(new ArrayList<>(currentAttachmentFileNames));
            model.addAttribute("contractDraftRequest", draftRequest);
            model.addAttribute("currentAttachmentFileNames", currentAttachmentFileNames);
            try {
                model.addAttribute("initialAttachmentsJson", objectMapper.writeValueAsString(currentAttachmentFileNames));
            } catch (JsonProcessingException e) {
                logger.warn("序列化初始附件列表到JSON时出错 (Contract ID: {}): {}", contractId, e.getMessage());
                model.addAttribute("initialAttachmentsJson", "[]");
            }

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
        } catch (Exception e) {
            logger.error("加载定稿合同表单时发生未知错误 (Contract ID: {})", contractId, e);
            redirectAttributes.addFlashAttribute("errorMessage", "加载定稿页面时发生内部错误。");
            return "redirect:/contract-manager/pending-finalization";
        }
    }

    @PostMapping("/finalize/{contractId}")
    @PreAuthorize("hasAuthority('CON_FINAL_SUBMIT')")
    public String processFinalizeContract(@PathVariable Long contractId,
                                          @RequestParam(required = false) String finalizationComments,
                                          @ModelAttribute("contractDraftRequest") @Valid ContractDraftRequest contractDraftRequest,
                                          BindingResult bindingResult,
                                          RedirectAttributes redirectAttributes,
                                          Principal principal,
                                          Model model) {

        if (bindingResult.hasErrors()) {
            try {
                Contract contractToDisplay = contractService.getContractForFinalization(contractId, principal.getName());
                model.addAttribute("contract", contractToDisplay);
                List<ContractProcess> countersignOpinions = contractService.getContractCountersignOpinions(contractId);
                model.addAttribute("countersignOpinions", countersignOpinions);
                List<String> currentAttachmentFileNames = new ArrayList<>();
                if (contractToDisplay != null && contractToDisplay.getAttachmentPath() != null &&
                        !contractToDisplay.getAttachmentPath().trim().isEmpty() &&
                        !contractToDisplay.getAttachmentPath().equals("[]") &&
                        !contractToDisplay.getAttachmentPath().equalsIgnoreCase("null")) {
                    try {
                        currentAttachmentFileNames = objectMapper.readValue(contractToDisplay.getAttachmentPath(), new TypeReference<List<String>>(){});
                    } catch (JsonProcessingException e) {
                        logger.warn("校验失败后重新加载附件JSON时出错 (Contract ID: {}): {}", contractId, e.getMessage());
                    }
                }
                model.addAttribute("currentAttachmentFileNames", currentAttachmentFileNames);
                try {
                    model.addAttribute("initialAttachmentsJson", objectMapper.writeValueAsString(currentAttachmentFileNames));
                } catch (JsonProcessingException e) {
                    model.addAttribute("initialAttachmentsJson", "[]");
                }

            } catch (Exception loadEx) {
                redirectAttributes.addFlashAttribute("errorMessage", "表单校验失败，且重新加载合同信息时也发生错误：" + loadEx.getMessage());
                return "redirect:/contract-manager/pending-finalization";
            }
            model.addAttribute("errorMessage", "表单提交无效，请检查输入。");
            return "contract-manager/finalize-contract";
        }

        try {
            String username = principal.getName();
            String updatedContent = contractDraftRequest.getUpdatedContent();

            Contract finalizedContract = contractService.finalizeContract(
                    contractId,
                    finalizationComments,
                    contractDraftRequest.getAttachmentServerFileNames(),
                    updatedContent,
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
                List<ContractProcess> countersignOpinions = contractService.getContractCountersignOpinions(contractId);
                model.addAttribute("countersignOpinions", countersignOpinions);

                List<String> currentAttachmentFileNames = new ArrayList<>();
                if (contractToDisplay != null && contractToDisplay.getAttachmentPath() != null &&
                        !contractToDisplay.getAttachmentPath().trim().isEmpty() &&
                        !contractToDisplay.getAttachmentPath().equals("[]") &&
                        !contractToDisplay.getAttachmentPath().equalsIgnoreCase("null")) {
                    try {
                        currentAttachmentFileNames = objectMapper.readValue(contractToDisplay.getAttachmentPath(), new TypeReference<List<String>>(){});
                    } catch (JsonProcessingException jsonEx) {
                        logger.warn("IO异常后重新加载附件JSON时出错 (Contract ID: {}): {}", contractId, jsonEx.getMessage());
                    }
                }
                model.addAttribute("currentAttachmentFileNames", currentAttachmentFileNames);
                try {
                    model.addAttribute("initialAttachmentsJson", objectMapper.writeValueAsString(currentAttachmentFileNames));
                } catch (JsonProcessingException jsonEx) {
                    model.addAttribute("initialAttachmentsJson", "[]");
                }
            } catch (Exception loadEx) {
                redirectAttributes.addFlashAttribute("errorMessage", "附件处理失败，且重新加载合同信息以显示错误时也发生错误：" + loadEx.getMessage());
                return "redirect:/contract-manager/pending-finalization";
            }
            return "contract-manager/finalize-contract";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "合同定稿过程中发生未知系统错误。");
            logger.error("处理定稿合同未知错误 (Contract ID: {})", contractId, e);
            return "redirect:/contract-manager/finalize/" + contractId;
        }
    }

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
        model.addAttribute("contractNameSearch", contractNameSearch != null ? contractNameSearch : "");
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

            if (contract.getStatus() != ContractStatus.PENDING_APPROVAL && contract.getStatus() != ContractStatus.REJECTED ) {
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
            @RequestParam String decision,
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
            logger.error("处理审批合同未知错误 (Contract ID: {})", contractId, e);
            return "redirect:/contract-manager/approval-details/" + contractId;
        }
    }

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
        model.addAttribute("contractNameSearch", contractNameSearch != null ? contractNameSearch : "");
        model.addAttribute("listTitle", "待签订合同");
        return "contract-manager/pending-signing";
    }

    @GetMapping("/sign/{contractProcessId}")
    @PreAuthorize("hasAuthority('CON_SIGN_VIEW') or hasAuthority('CONTRACT_SIGN_SUBMIT')")
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
            redirectAttributes.addFlashAttribute("errorMessage", "签订过程中发生未知错误。");
            logger.error("处理签订合同未知错误 (Process ID: {})", contractProcessId, e);
            return "redirect:/contract-manager/pending-signing";
        }
    }

    @GetMapping("/view-all")
    @PreAuthorize("hasAuthority('CON_VIEW_MY') or hasRole('ROLE_ADMIN')")
    public String viewAllContracts(
            Authentication authentication,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String contractName,
            @RequestParam(required = false) String contractNumber,
            @RequestParam(required = false) String status,
            Model model) {

        String currentUsername = authentication.getName();
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(ga -> ga.getAuthority().equals("ROLE_ADMIN"));

        Page<Contract> contractsPage = contractService.searchContracts(currentUsername, isAdmin, contractName, contractNumber, status, pageable);

        model.addAttribute("contractsPage", contractsPage);
        model.addAttribute("contractName", contractName != null ? contractName : "");
        model.addAttribute("contractNumber", contractNumber != null ? contractNumber : "");
        model.addAttribute("status", status != null ? status : "");
        model.addAttribute("listTitle", "合同列表查询");
        // 确保指向正确的模板，根据您的项目结构，这应该是 "reports/contract-search" 或 "contract-manager/view-all-contracts" 等
        return "reports/contract-search";
    }
}