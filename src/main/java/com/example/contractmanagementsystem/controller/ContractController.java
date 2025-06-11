package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.dto.ContractDraftRequest;
import com.example.contractmanagementsystem.dto.ContractExtendRequest;
import com.example.contractmanagementsystem.dto.ContractExtendRequestOperator;
import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.entity.ContractProcess;
import com.example.contractmanagementsystem.entity.ContractProcessState;
import com.example.contractmanagementsystem.entity.ContractProcessType;
import com.example.contractmanagementsystem.entity.ContractStatus;
import com.example.contractmanagementsystem.entity.ContractTemplate; // 新增导入
import com.example.contractmanagementsystem.exception.BusinessLogicException;
import com.example.contractmanagementsystem.exception.ResourceNotFoundException;
import com.example.contractmanagementsystem.service.ContractService;
import com.example.contractmanagementsystem.service.TemplateService; // 新增导入 TemplateService
import com.example.contractmanagementsystem.service.TemplateParsingService;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.example.contractmanagementsystem.dto.PendingApprovalItemDto;

import org.springframework.transaction.annotation.Transactional;
import org.hibernate.Hibernate;

@Controller
@RequestMapping({"/contract-manager", "/contracts"})
public class ContractController {

    private static final Logger logger = LoggerFactory.getLogger(ContractController.class);
    private final ContractService contractService;
    private final ObjectMapper objectMapper;
    private final TemplateService templateService; // 新增注入
    private final TemplateParsingService templateParsingService;

    @Autowired
    public ContractController(ContractService contractService, ObjectMapper objectMapper, TemplateService templateService, TemplateParsingService templateParsingService) { // 新增注入
        this.contractService = contractService;
        this.objectMapper = objectMapper;
        this.templateService = templateService; // 初始化
        this.templateParsingService = templateParsingService;
    }

    // This old download logic is replaced by the one in AttachmentController for consistency
    // The one in AttachmentController should be the single source of truth.
    // The one in AttachmentController should be the single source of truth.
    /*
    @GetMapping("/api/attachments/download/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> downloadAttachment(@PathVariable String filename) {
        // ... This method should be removed to avoid duplication with AttachmentController
    }
    */


    @GetMapping("/draft-contract")
    @PreAuthorize("hasAuthority('CON_DRAFT_NEW')")
    public String showDraftContractPage(Model model) {
        if (!model.containsAttribute("contractDraftRequest")) {
            model.addAttribute("contractDraftRequest", new ContractDraftRequest());
        }
        // 新增：加载所有合同模板并添加到模型中
        model.addAttribute("contractTemplates", templateService.getAllTemplates());
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
            // 重新添加模板列表，以便页面重新渲染时可用
            model.addAttribute("contractTemplates", templateService.getAllTemplates());
            return "contract-manager/draft-contract";
        }
        try {
            String username = principal.getName();
            Contract draftedContract = contractService.draftContract(contractDraftRequest, username);
            redirectAttributes.addFlashAttribute("successMessage", "合同 " + draftedContract.getContractName() + " (编号: " + draftedContract.getContractNumber() + ") 已成功起草，等待分配！");
            return "redirect:/contract-manager/draft-contract";
        } catch (BusinessLogicException e) {
            model.addAttribute("errorMessage", e.getMessage());
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("附件")) {
                model.addAttribute("attachmentError", e.getMessage());
            }
            model.addAttribute("contractDraftRequest", contractDraftRequest);
            // 重新添加模板列表
            model.addAttribute("contractTemplates", templateService.getAllTemplates());
            return "contract-manager/draft-contract";
        } catch (ResourceNotFoundException e) {
            model.addAttribute("errorMessage", "起草合同失败：" + e.getMessage());
            model.addAttribute("contractDraftRequest", contractDraftRequest);
            // 重新添加模板列表
            model.addAttribute("contractTemplates", templateService.getAllTemplates());
            return "contract-manager/draft-contract";
        }
        catch (IOException e) {
            model.addAttribute("errorMessage", "处理请求时发生I/O错误：" + e.getMessage());
            model.addAttribute("contractDraftRequest", contractDraftRequest);
            // 重新添加模板列表
            model.addAttribute("contractTemplates", templateService.getAllTemplates());
            return "contract-manager/draft-contract";
        }
        catch (Exception e) {
            model.addAttribute("errorMessage", "起草合同失败，发生未知系统错误。");
            model.addAttribute("contractDraftRequest", contractDraftRequest);
            logger.error("起草合同未知错误", e);
            // 重新添加模板列表
            model.addAttribute("contractTemplates", templateService.getAllTemplates());
            return "contract-manager/draft-contract";
        }
    }

    // 新增：获取合同模板详情的API端点
    @GetMapping("/api/templates/{templateId}")
    @ResponseBody
    @PreAuthorize("hasAuthority('CON_DRAFT_NEW')") // 允许有起草权限的用户访问
    public ResponseEntity<ContractTemplate> getContractTemplateDetails(@PathVariable Long templateId) {
        return templateService.getTemplateById(templateId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
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

    @PostMapping("/countersign-form")
    @PreAuthorize("hasAuthority('CON_CSIGN_SUBMIT')")
    public String processCountersignAction(
            @RequestParam Long contractProcessId,
            @RequestParam String decision,
            @RequestParam(required = false) String comments,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        logger.info("进入 processCountersignAction 方法.");
        logger.info("接收到参数：contractProcessId={}, decision={}, comments={}", contractProcessId, decision, comments);
        try {
            boolean isApproved = "APPROVED".equalsIgnoreCase(decision);
            contractService.processCountersign(contractProcessId, comments, principal.getName(), isApproved);
            redirectAttributes.addFlashAttribute("successMessage", "会签意见已成功提交。");
            return "redirect:/contract-manager/pending-countersign";
        } catch (BusinessLogicException | ResourceNotFoundException | AccessDeniedException e) {
            logger.error("会签操作失败，错误信息: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "会签操作失败: " + e.getMessage());
            return "redirect:/contract-manager/pending-countersign";
        } catch (Exception e) {
            logger.error("会签过程中发生未知系统错误。", e);
            redirectAttributes.addFlashAttribute("errorMessage", "会签过程中发生未知系统错误。");
            return "redirect:/contract-manager/pending-countersign";
        }
    }

    @GetMapping("/countersign-form/{contractId}")
    @PreAuthorize("hasAuthority('CON_CSIGN_VIEW')or hasAuthority('CON_CSIGN_SUBMIT')")
    @Transactional(readOnly = true)
    public String showCountersignForm(
            @PathVariable Long contractId,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {

        String currentUsername = authentication.getName();
        try {
            Contract contract = contractService.getContractById(contractId);

            Optional<ContractProcess> currentProcessOpt = contractService.getContractProcessDetails(
                    contractId, currentUsername, ContractProcessType.COUNTERSIGN, ContractProcessState.PENDING
            );

            if (currentProcessOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "未找到您需要会签的该合同流程，或您已完成会签。");
                return "redirect:/contract-manager/pending-countersign";
            }
            ContractProcess currentProcess = currentProcessOpt.get();
            
            // 初始化延迟加载的属性
            Hibernate.initialize(currentProcess.getOperator());
            Hibernate.initialize(contract.getDrafter());

            if (!contractService.canUserCountersignContract(contract.getId(), currentUsername)) {
                redirectAttributes.addFlashAttribute("errorMessage", "您无权会签此合同或该合同不处于待会签状态。");
                return "redirect:/contract-manager/pending-countersign";
            }

            List<ContractProcess> allCountersignProcesses = contractService.getAllContractProcessesByContractAndType(contract, ContractProcessType.COUNTERSIGN);
            
            // 确保所有会签流程的操作者都被初始化
            allCountersignProcesses.forEach(process -> {
                Hibernate.initialize(process.getOperator());
            });

            List<String> attachmentPaths = Collections.emptyList();
            if (contract.getAttachmentPath() != null && !contract.getAttachmentPath().isEmpty()) {
                try {
                    attachmentPaths = objectMapper.readValue(contract.getAttachmentPath(), new TypeReference<List<String>>() {});
                } catch (JsonProcessingException e) {
                    logger.error("Error parsing attachment paths for contract ID {}: {}", contractId, e.getMessage());
                    model.addAttribute("errorMessage", "解析附件信息失败。");
                }
            }

            // 解析合同内容中的占位符
            String parsedContent = templateParsingService.parseContractContent(contract.getContent(), contract);
            contract.setContent(parsedContent);

            model.addAttribute("contract", contract);
            model.addAttribute("currentProcess", currentProcess);
            model.addAttribute("allCountersignProcesses", allCountersignProcesses);
            model.addAttribute("attachmentPaths", attachmentPaths);
            model.addAttribute("listTitle", "合同会签");

            return "contract-manager/countersign-form";
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/contract-manager/pending-countersign";
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "访问被拒绝: " + e.getMessage());
            return "redirect:/contract-manager/pending-countersign";
        } catch (Exception e) {
            logger.error("Error showing countersign form for contract ID {}: {}", contractId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "加载会签表单时发生未知错误。");
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

        Map<String, Object> additionalParamsMap = new HashMap<>();
        if (contractNameSearch != null && !contractNameSearch.isEmpty()) {
            additionalParamsMap.put("contractNameSearch", contractNameSearch);
        }
        model.addAttribute("additionalParamsMap", additionalParamsMap);
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

            // 解析合同内容中的占位符
            String parsedContent = templateParsingService.parseContractContent(contract.getContent(), contract);
            contract.setContent(parsedContent);

            model.addAttribute("contract", contract);

            List<ContractProcess> countersignOpinions = contractService.getContractCountersignOpinions(contractId);
            model.addAttribute("countersignOpinions", countersignOpinions);

            // ⭐ NEW: 获取所有定稿意见 ⭐
            List<ContractProcess> allFinalizeProcesses = contractService.getContractFinalizeOpinions(contractId);
            model.addAttribute("allFinalizeProcesses", allFinalizeProcesses);


            ContractDraftRequest draftRequest = new ContractDraftRequest();
            List<String> currentAttachmentFileNames = new ArrayList<>();

            if (contract != null) {
                draftRequest.setUpdatedContent(contract.getContent());

                String attachmentPathJson = contract.getAttachmentPath();
                if (attachmentPathJson != null && !attachmentPathJson.trim().isEmpty() &&
                        !attachmentPathJson.equals("[]") && !attachmentPathJson.equalsIgnoreCase("null")) {
                    try {
                        currentAttachmentFileNames = objectMapper.readValue(attachmentPathJson, new TypeReference<List<String>>() {});
                        logger.debug("加载定稿表单时解析附件JSON成功。合同ID: {}, 附件: {}", contractId, currentAttachmentFileNames);
                    } catch (JsonProcessingException e) {
                        logger.warn("加载定稿表单时解析附件JSON失败 (Contract ID: {}): {}", contractId, e.getMessage());
                        currentAttachmentFileNames = new ArrayList<>();
                        model.addAttribute("attachmentError", "加载现有附件时遇到问题，请检查附件格式。");
                    }
                }
            }
            draftRequest.setAttachmentServerFileNames(new ArrayList<>(currentAttachmentFileNames));
            model.addAttribute("contractDraftRequest", draftRequest);
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
        }
        catch (Exception e) {
            logger.error("加载定稿合同表单时发生未知错误 (Contract ID: {})", contractId, e);
            redirectAttributes.addFlashAttribute("errorMessage", "加载定稿页面时发生内部错误。");
            return "redirect:/contract-manager/pending-finalization";
        }
    }

    @PostMapping(value = "/finalize/{contractId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @PreAuthorize("hasAuthority('CON_FINAL_SUBMIT')")
    public ResponseEntity<Map<String, String>> processFinalizeContract(
            @PathVariable Long contractId,
            @RequestBody @Valid Map<String, Object> payload,
            Principal principal) {

        String finalizationComments = (String) payload.get("finalizationComments");
        String updatedContent = (String) payload.get("updatedContent");
        @SuppressWarnings("unchecked")
        List<String> attachmentServerFileNames = (List<String>) payload.get("attachmentServerFileNames");

        if (updatedContent == null || updatedContent.isEmpty()) {
            Map<String, String> errors = new HashMap<>();
            errors.put("message", "合同内容不能为空。");
            errors.put("type", "validation_error");
            return ResponseEntity.badRequest().body(errors);
        }

        try {
            String username = principal.getName();

            Contract finalizedContract = contractService.finalizeContract(
                    contractId,
                    finalizationComments,
                    attachmentServerFileNames,
                    updatedContent,
                    username
            );

            Map<String, String> response = new HashMap<>();
            if (finalizedContract.getStatus() == ContractStatus.PENDING_APPROVAL) {
                response.put("statusKey", "COMPLETED_ALL_FINALIZERS");
                response.put("message", "合同 " + finalizedContract.getContractName() + " (ID: " + contractId + ") 已成功定稿，并进入下一审批流程。");
            } else if (finalizedContract.getStatus() == ContractStatus.PENDING_FINALIZATION) {
                response.put("statusKey", "PARTIALLY_FINALIZED");
                response.put("message", "您的定稿已提交。合同 " + finalizedContract.getContractName() + " (ID: " + contractId + ") 仍在等待其他定稿人员完成定稿。");
            } else if (finalizedContract.getStatus() == ContractStatus.REJECTED) {
                response.put("statusKey", "FINALIZATION_REJECTED");
                response.put("message", "定稿操作被拒绝，合同 " + finalizedContract.getContractName() + " (ID: " + contractId + ") 状态已变为拒绝。");
            } else {
                response.put("statusKey", "UNKNOWN_STATUS");
                response.put("message", "定稿操作完成，合同状态: " + finalizedContract.getStatus().getDescription() + "。");
            }
            response.put("contractId", String.valueOf(contractId));
            return ResponseEntity.ok(response);
        } catch (BusinessLogicException e) {
            logger.warn("定稿失败 (合同ID: {}): {}", contractId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", "定稿失败: " + e.getMessage(), "type", "business_error"));
        } catch (AccessDeniedException e) {
            logger.error("定稿失败，权限不足 (合同ID: {}): {}", contractId, e.getMessage());
            return ResponseEntity.status(403).body(Map.of("message", "定稿失败: 权限不足。", "type", "access_denied"));
        } catch (ResourceNotFoundException e) {
            logger.warn("定稿失败，合同未找到 (合同ID: {}): {}", contractId, e.getMessage());
            return ResponseEntity.status(404).body(Map.of("message", "定稿失败: " + e.getMessage(), "type", "not_found"));
        } catch (IOException e) {
            logger.error("合同定稿附件处理失败 (合同ID: {}): {}", contractId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("message", "附件处理失败: " + e.getMessage(), "type", "io_error"));
        } catch (Exception e) {
            logger.error("合同定稿过程中发生未知系统错误 (合同ID: {})", contractId, e);
            return ResponseEntity.internalServerError().body(Map.of("message", "合同定稿过程中发生未知系统错误。", "type", "unknown_error"));
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
        Page<ContractProcess> pendingProcessesPage = contractService.getPendingProcessesForUser(
                username, ContractProcessType.APPROVAL, ContractProcessState.PENDING, contractNameSearch, pageable);

        List<PendingApprovalItemDto> itemsWithAttachments = new ArrayList<>();
        for (ContractProcess process : pendingProcessesPage.getContent()) {
            List<String> attachmentPaths = new ArrayList<>();
            String attachmentJson = process.getContract() != null ? process.getContract().getAttachmentPath() : null;
            if (StringUtils.hasText(attachmentJson) && !attachmentJson.equals("[]") && !attachmentJson.equalsIgnoreCase("null")) {
                try {
                    attachmentPaths = objectMapper.readValue(attachmentJson, new TypeReference<List<String>>() {});
                } catch (JsonProcessingException e) {
                    logger.warn("解析待审批合同附件路径失败，合同ID {}: {}", process.getContract().getId(), e.getMessage());
                }
            }

            PendingApprovalItemDto dto = new PendingApprovalItemDto(process, attachmentPaths);

            try {
                dto.setAttachmentFileNamesAsJson(objectMapper.writeValueAsString(attachmentPaths));
            } catch (JsonProcessingException e) {
                logger.error("序列化附件列表到JSON时出错，合同ID {}: {}", process.getContract().getId(), e.getMessage());
                dto.setAttachmentFileNamesAsJson("[]");
            }

            itemsWithAttachments.add(dto);
        }

        Page<PendingApprovalItemDto> pendingApprovalsDtoPage = new PageImpl<>(
                itemsWithAttachments, pageable, pendingProcessesPage.getTotalElements());

        model.addAttribute("pendingApprovals", pendingApprovalsDtoPage);
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
                model.addAttribute("infoMessage", "提示：此合同当前状态为 " + contract.getStatus().getDescription() + "，可能并非处于标准的待审批环节。");
            }

            // 解析合同内容中的占位符
            String parsedContent = templateParsingService.parseContractContent(contract.getContent(), contract);
            contract.setContent(parsedContent);

            model.addAttribute("contract", contract);

            // ⭐ NEW: 获取所有定稿意见和所有审批意见 ⭐
            List<ContractProcess> allFinalizeProcesses = contractService.getContractFinalizeOpinions(contractId);
            model.addAttribute("allFinalizeProcesses", allFinalizeProcesses);
            List<ContractProcess> allApprovalProcesses = contractService.getContractApprovalOpinions(contractId);
            model.addAttribute("allApprovalProcesses", allApprovalProcesses);

            return "contract-manager/approval-details";
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "无法加载审批详情：" + e.getMessage());
            return "redirect:/contract-manager/pending-approval";
        }
        catch (AccessDeniedException e) {
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

    @GetMapping("/sign-contract/{processId}")
    @PreAuthorize("hasAuthority('CON_SIGN_VIEW') or hasAuthority('CONTRACT_SIGN_SUBMIT')")
    public String showSignContractForm(@PathVariable Long processId, Model model, Principal principal, RedirectAttributes redirectAttributes) {
        try {
            String username = principal.getName();
            ContractProcess contractProcess = contractService.getContractProcessByIdAndOperator(
                    processId, username, ContractProcessType.SIGNING, ContractProcessState.PENDING);

            // 解析合同内容中的占位符
            String parsedContent = templateParsingService.parseContractContent(contractProcess.getContract().getContent(), contractProcess.getContract());
            contractProcess.getContract().setContent(parsedContent);

            model.addAttribute("contractProcess", contractProcess);
            // 获取合同ID用于查询其他流程意见
            Long contractId = contractProcess.getContract().getId();

            // ⭐ NEW: 获取所有审批意见和所有签订意见 ⭐
            List<ContractProcess> allApprovalProcesses = contractService.getContractApprovalOpinions(contractId);
            model.addAttribute("allApprovalProcesses", allApprovalProcesses);
            List<ContractProcess> allSigningProcesses = contractService.getContractSigningOpinions(contractId);
            model.addAttribute("allSigningProcesses", allSigningProcesses); // 这里的列表包含当前用户已提交的签订意见

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
            @RequestParam(required = false) Boolean expiringSoon, // 新增参数
            Model model) {

        String currentUsername = authentication.getName();
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(ga -> ga.getAuthority().equals("ROLE_ADMIN"));

        Page<Contract> contractsPage = contractService.searchContracts(currentUsername, isAdmin, contractName, contractNumber, status, expiringSoon, pageable);

        model.addAttribute("contractsPage", contractsPage);
        model.addAttribute("contractName", contractName != null ? contractName : "");
        model.addAttribute("contractNumber", contractNumber != null ? contractNumber : "");
        model.addAttribute("status", status != null ? status : "");
        model.addAttribute("expiringSoon", expiringSoon != null ? expiringSoon : false); // 传回模型
        model.addAttribute("listTitle", "合同列表查询");
        return "reports/contract-search";
    }

    @GetMapping("/{contractId}/detail")
    @PreAuthorize("hasAuthority('CON_VIEW_MY') or hasRole('ROLE_ADMIN')")
    public String viewContractDetail(
            @PathVariable Long contractId,
            Model model,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        try {
            Contract contract = contractService.getContractById(contractId);
            List<ContractProcess> contractProcesses = contractService.getContractProcessHistory(contractId);

            // 解析合同内容中的占位符
            String parsedContent = templateParsingService.parseContractContent(contract.getContent(), contract);
            contract.setContent(parsedContent);

            model.addAttribute("contract", contract);
            model.addAttribute("contractProcesses", contractProcesses);

            List<String> attachmentPaths = new ArrayList<>();
            logger.info("合同详情页面 - 合同ID: {}, 原始附件路径: {}", contractId, contract.getAttachmentPath());
            if (contract.getAttachmentPath() != null && !contract.getAttachmentPath().trim().isEmpty()) {
                try {
                    attachmentPaths = objectMapper.readValue(contract.getAttachmentPath(), new TypeReference<List<String>>() {});
                    logger.info("合同详情页面 - 解析后的附件列表: {}", attachmentPaths);
                } catch (JsonProcessingException e) {
                    logger.error("解析附件路径失败，合同ID {}: {}", contractId, e.getMessage());
                    model.addAttribute("errorMessage", "附件信息解析失败。");
                }
            } else {
                logger.info("合同详情页面 - 附件路径为空或null");
            }
            model.addAttribute("attachmentPaths", attachmentPaths);


            return "contracts/detail";
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "无法找到指定的合同：" + e.getMessage());
            return "redirect:/reports/contract-search";
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "您没有权限查看此合同");
            return "redirect:/reports/contract-search";
        }
    }

    @PostMapping("/{contractId}/extend/admin")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @ResponseBody
    public ResponseEntity<String> extendContractAdmin(
            @PathVariable Long contractId,
            @Valid @RequestBody ContractExtendRequest request,
            Authentication authentication) {
        try {
            contractService.extendContract(contractId, request.getNewEndDate(), request.getComments(), authentication.getName());
            return ResponseEntity.ok("合同延期成功！");
        } catch (BusinessLogicException e) {
            logger.warn("管理员延期合同失败 (合同ID: {}): {}", contractId, e.getMessage());
            return ResponseEntity.badRequest().body("延期失败: " + e.getMessage());
        }
    }

    @PostMapping("/{contractId}/extend/request")
    @PreAuthorize("hasRole('ROLE_CONTRACT_OPERATOR') or hasRole('ROLE_ADMIN')")
    @ResponseBody
    public ResponseEntity<String> requestExtendContractOperator(
            @PathVariable Long contractId,
            @Valid @RequestBody ContractExtendRequestOperator request,
            Authentication authentication) {
        try {
            contractService.requestExtendContract(
                    contractId,
                    request.getRequestedNewEndDate(),
                    request.getReason(),
                    request.getComments(),
                    authentication.getName()
            );
            return ResponseEntity.ok("延期请求已提交，请等待管理员审批。");
        } catch (BusinessLogicException e) {
            logger.warn("操作员请求延期合同失败 (合同ID: {}): {}", contractId, e.getMessage());
            return ResponseEntity.badRequest().body("提交请求失败: " + e.getMessage());
        } catch (ResourceNotFoundException e) {
            logger.warn("操作员请求延期合同失败，合同未找到 (合同ID: {}): {}", contractId, e.getMessage());
            return ResponseEntity.status(404).body("提交请求失败: " + e.getMessage());
        } catch (Exception e) {
            logger.error("操作员请求延期合同发生未知错误 (合同ID: {}): {}", contractId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body("提交请求失败: 服务器内部错误。");
        }
    }
}