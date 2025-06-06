package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.dto.ContractDraftRequest;
import com.example.contractmanagementsystem.dto.ContractExtendRequest; // 导入 ContractExtendRequest
import com.example.contractmanagementsystem.dto.ContractExtendRequestOperator; // 导入 ContractExtendRequestOperator
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
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody; // 导入 RequestBody
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.example.contractmanagementsystem.dto.PendingApprovalItemDto;


@Controller
@RequestMapping({"/contract-manager", "/contracts"}) // 类级别映射，例如 /contract-manager
public class ContractController {

    private static final Logger logger = LoggerFactory.getLogger(ContractController.class);
    private final ContractService contractService;
    private final ObjectMapper objectMapper;

    // TODO: 替换为你的附件存储的实际根路径
    // 例如：private final Path attachmentStorageLocation = Paths.get("/path/to/your/attachments").toAbsolutePath().normalize();
    // 假设附件存储在项目根目录下的 uploads/attachments 文件夹
    private final Path attachmentStorageLocation = Paths.get("uploads/attachments").toAbsolutePath().normalize();

    @Autowired
    public ContractController(ContractService contractService, ObjectMapper objectMapper) {
        this.contractService = contractService;
        this.objectMapper = objectMapper;
    }

    // 新增：附件下载接口
    // 使用 @ResponseBody 表示直接将返回值写入HTTP响应体，而不是解析为视图
    @GetMapping("/api/attachments/download/{filename:.+}") // {filename:.+} 允许文件名包含点，匹配完整文件名
    @ResponseBody
    public ResponseEntity<Resource> downloadAttachment(@PathVariable String filename) {
        logger.info("收到附件下载请求，文件名: {}", filename);
        try {
            // 确保文件名是安全的，防止路径遍历攻击
            Path filePath = attachmentStorageLocation.resolve(filename).normalize();

            // 打印解析后的文件绝对路径，用于调试
            logger.debug("解析附件路径: {}", filePath.toString());

            if (!filePath.startsWith(attachmentStorageLocation)) {
                // 如果尝试访问 attachmentStorageLocation 之外的路径，则拒绝
                logger.warn("尝试非法访问附件路径: {}", filename);
                return ResponseEntity.badRequest().build();
            }

            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                logger.info("附件文件找到并可读: {}", filePath.toString());
                // 尝试根据文件扩展名设置MIME类型
                String contentType = null;
                try {
                    // 实际项目中，更推荐使用 Files.probeContentType(filePath)
                    // 或 ServletContext.getMimeType(resource.getFilename())
                    if (filename.toLowerCase().endsWith(".pdf")) {
                        contentType = "application/pdf";
                    } else if (filename.toLowerCase().endsWith(".png")) {
                        contentType = "image/png";
                    } else if (filename.toLowerCase().endsWith(".jpg") || filename.toLowerCase().endsWith(".jpeg")) {
                        contentType = "image/jpeg";
                    } else if (filename.toLowerCase().endsWith(".doc")) {
                        contentType = "application/msword";
                    } else if (filename.toLowerCase().endsWith(".docx")) {
                        contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                    } else {
                        contentType = "application/octet-stream"; // 默认二进制流
                    }
                    logger.debug("推断出的MIME类型: {}", contentType);
                } catch (Exception e) {
                    logger.warn("无法确定文件 {} 的 MIME 类型，使用默认类型。错误: {}", filename, e.getMessage());
                    contentType = "application/octet-stream";
                }

                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                logger.warn("请求的附件文件不存在或不可读: {}", filePath.toString());
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException ex) {
            logger.error("附件文件名 {} 格式不正确: {}", filename, ex.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception ex) {
            logger.error("下载附件 {} 时发生服务器内部错误: {}", filename, ex.getMessage(), ex);
            return ResponseEntity.internalServerError().build();
        }
    }


    @GetMapping("/draft-contract")
    @PreAuthorize("hasAuthority('CON_DRAFT_NEW')")
    public String showDraftContractPage(Model model) {
        // 如果模型中不包含 contractDraftRequest，则添加一个新的空对象
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
            Model model, // 用于在当前请求中添加错误信息（如果发生校验错误）
            Principal principal
    ) {
        // 如果存在JSR-303/380验证错误
        if (bindingResult.hasErrors()) {
            // 将包含错误的请求对象重新添加到模型中，以便前端显示错误信息
            model.addAttribute("contractDraftRequest", contractDraftRequest);
            // 返回到起草合同页面，Thymeleaf会根据 bindingResult 显示错误
            return "contract-manager/draft-contract";
        }
        try {
            String username = principal.getName();
            Contract draftedContract = contractService.draftContract(contractDraftRequest, username);
            // 使用 Flash Attributes 在重定向后传递成功消息
            redirectAttributes.addFlashAttribute("successMessage", "合同 " + draftedContract.getContractName() + " (编号: " + draftedContract.getContractNumber() + ") 已成功起草，等待分配！");
            // 重定向回起草页面或某个成功页面
            return "redirect:/contract-manager/draft-contract";
        } catch (BusinessLogicException e) {
            // 业务逻辑异常，在当前请求中显示错误信息
            model.addAttribute("errorMessage", e.getMessage());
            // 如果错误信息与附件相关，可以添加特定属性以便前端针对性显示
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("附件")) {
                model.addAttribute("attachmentError", e.getMessage());
            }
            // 重新添加请求对象以保留用户输入
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
            // 捕获其他未知异常，并记录到日志
            model.addAttribute("errorMessage", "起草合同失败，发生未知系统错误。");
            model.addAttribute("contractDraftRequest", contractDraftRequest);
            logger.error("起草合同未知错误", e);
            return "contract-manager/draft-contract";
        }
    }

    @GetMapping("/pending-countersign")
    @PreAuthorize("hasAuthority('CON_CSIGN_VIEW')") // 仅允许拥有 "CON_CSIGN_VIEW" 权限的用户访问
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

    // 注意：这里的 @PostMapping 路径已修改，移除了重复的 /contract-manager
    @PostMapping("/countersign-form")
    @PreAuthorize("hasAuthority('CON_CSIGN_SUBMIT')") // 仅允许拥有 "CON_CSIGN_SUBMIT" 权限的用户提交
    public String processCountersignAction(
            @RequestParam Long contractProcessId,
            @RequestParam String decision, // "APPROVED" 或 "REJECTED"
            @RequestParam(required = false) String comments,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        // --- 添加的日志 ---
        logger.info("进入 processCountersignAction 方法.");
        logger.info("接收到参数：contractProcessId={}, decision={}, comments={}", contractProcessId, decision, comments);
        // --- 日志结束 ---
        try {
            boolean isApproved = "APPROVED".equalsIgnoreCase(decision);
            contractService.processCountersign(contractProcessId, comments, principal.getName(), isApproved);
            redirectAttributes.addFlashAttribute("successMessage", "会签意见已成功提交。");
            return "redirect:/contract-manager/pending-countersign";
        } catch (BusinessLogicException | ResourceNotFoundException | AccessDeniedException e) {
            logger.error("会签操作失败，错误信息: {}", e.getMessage()); // 记录错误信息
            redirectAttributes.addFlashAttribute("errorMessage", "会签操作失败: " + e.getMessage());
            return "redirect:/contract-manager/pending-countersign";
        } catch (Exception e) {
            logger.error("会签过程中发生未知系统错误。", e); // 记录未知异常及堆栈
            redirectAttributes.addFlashAttribute("errorMessage", "会签过程中发生未知系统错误。");
            return "redirect:/contract-manager/pending-countersign";
        }
    }

    // 注意：这里的 @GetMapping 路径已修改，移除了重复的 /contract-manager
    @GetMapping("/countersign-form/{contractId}") // 路径变量使用 contractId
    @PreAuthorize("hasAuthority('CON_CSIGN_VIEW')or hasAuthority('CON_CSIGN_SUBMIT')")
    public String showCountersignForm(
            @PathVariable Long contractId, // 获取合同ID
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {

        String currentUsername = authentication.getName();
        try {
            // 1. 获取合同详情
            Contract contract = contractService.getContractById(contractId);

            // 2. 获取当前用户对应的待会签流程记录
            Optional<ContractProcess> currentProcessOpt = contractService.getContractProcessDetails(
                    contractId, currentUsername, ContractProcessType.COUNTERSIGN, ContractProcessState.PENDING
            );

            if (currentProcessOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "未找到您需要会签的该合同流程，或您已完成会签。");
                return "redirect:/contract-manager/pending-countersign";
            }
            ContractProcess currentProcess = currentProcessOpt.get();


            // 3. 验证当前用户是否可以会签此合同
            if (!contractService.canUserCountersignContract(contract.getId(), currentUsername)) {
                redirectAttributes.addFlashAttribute("errorMessage", "您无权会签此合同或该合同不处于待会签状态。");
                return "redirect:/contract-manager/pending-countersign";
            }

            // 4. 获取所有会签意见（已完成和待完成的）
            List<ContractProcess> allCountersignProcesses = contractService.getAllContractProcessesByContractAndType(contract, ContractProcessType.COUNTERSIGN);

            // 5. 解析附件路径 (如果存在)
            List<String> attachmentPaths = Collections.emptyList();
            if (contract.getAttachmentPath() != null && !contract.getAttachmentPath().isEmpty()) {
                try {
                    attachmentPaths = objectMapper.readValue(contract.getAttachmentPath(), new TypeReference<List<String>>() {});
                } catch (JsonProcessingException e) {
                    logger.error("Error parsing attachment paths for contract ID {}: {}", contractId, e.getMessage());
                    model.addAttribute("errorMessage", "解析附件信息失败。");
                }
            }


            model.addAttribute("contract", contract);
            model.addAttribute("currentProcess", currentProcess); // 传递当前待处理的流程实例
            model.addAttribute("allCountersignProcesses", allCountersignProcesses); // 传递所有会签意见
            model.addAttribute("attachmentPaths", attachmentPaths);
            model.addAttribute("listTitle", "合同会签"); // 页面标题

            return "contract-manager/countersign-form"; // 返回新的会签表单页面
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
    @PreAuthorize("hasAuthority('CON_FINAL_VIEW')") // 仅允许拥有 "CON_FINAL_VIEW" 权限的用户访问
    public String showPendingFinalizationContracts(
            @PageableDefault(size = 10, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String contractNameSearch,
            Model model,
            Principal principal) {
        String username = principal.getName();
        // 修改此行，将 Page<ContractProcess> 更改为 Page<Contract>
        Page<Contract> pendingFinalizationContracts = contractService.getContractsPendingFinalizationForUser(username, contractNameSearch, pageable);
        model.addAttribute("pendingFinalizationContracts", pendingFinalizationContracts);

        // 为了在分页链接中保留搜索条件，将搜索参数放入 Map
        java.util.Map<String, Object> additionalParamsMap = new java.util.HashMap<>();
        if (contractNameSearch != null && !contractNameSearch.isEmpty()) {
            additionalParamsMap.put("contractNameSearch", contractNameSearch);
        }
        model.addAttribute("additionalParamsMap", additionalParamsMap); // 将Map传递给模板
        // 为了向后兼容或如果其他地方仍然直接使用 contractNameSearch, 可以保留它
        model.addAttribute("contractNameSearch", contractNameSearch != null ? contractNameSearch : "");


        model.addAttribute("listTitle", "待定稿合同");
        model.addAttribute("finalizeBaseUrl", "/contract-manager/finalize"); // 定稿操作的基路径
        return "contract-manager/pending-finalization";
    }

    @GetMapping("/finalize/{contractId}")
    @PreAuthorize("hasAuthority('CON_FINAL_VIEW') or hasAuthority('CON_FINAL_SUBMIT')") // 允许查看或提交定稿的用户访问
    public String showFinalizeContractForm(@PathVariable Long contractId,
                                           Model model,
                                           Principal principal,
                                           RedirectAttributes redirectAttributes) {
        try {
            String username = principal.getName();
            // 获取合同详情，该方法会进行权限和状态检查
            Contract contract = contractService.getContractForFinalization(contractId, username);
            model.addAttribute("contract", contract);

            // 获取所有会签意见，用于在定稿页面显示
            List<ContractProcess> countersignOpinions = contractService.getContractCountersignOpinions(contractId);
            model.addAttribute("countersignOpinions", countersignOpinions);

            // 创建一个 ContractDraftRequest DTO 来预填充表单
            ContractDraftRequest draftRequest = new ContractDraftRequest();
            List<String> currentAttachmentFileNames = new ArrayList<>();

            // 预填充合同内容
            if (contract != null) {
                draftRequest.setUpdatedContent(contract.getContent());

                // 解析附件路径 JSON 字符串，预填充到 attachmentServerFileNames
                String attachmentPathJson = contract.getAttachmentPath();
                if (attachmentPathJson != null && !attachmentPathJson.trim().isEmpty() &&
                        !attachmentPathJson.equals("[]") && !attachmentPathJson.equalsIgnoreCase("null")) {
                    try {
                        // 使用 ObjectMapper 将 JSON 字符串反序列化为 List<String>
                        currentAttachmentFileNames = objectMapper.readValue(attachmentPathJson, new TypeReference<List<String>>() {});
                        logger.debug("加载定稿表单时解析附件JSON成功。合同ID: {}, 附件: {}", contractId, currentAttachmentFileNames);
                    } catch (JsonProcessingException e) {
                        logger.warn("加载定稿表单时解析附件JSON失败 (Contract ID: {}): {}", contractId, e.getMessage());
                        // 解析失败则清空附件列表，避免前端显示错误
                        currentAttachmentFileNames = new ArrayList<>();
                        // 可以选择向用户显示一个警告
                        model.addAttribute("attachmentError", "加载现有附件时遇到问题，请检查附件格式。");
                    }
                }
            }
            // 将解析出的附件文件名列表设置到 DTO 中
            draftRequest.setAttachmentServerFileNames(new ArrayList<>(currentAttachmentFileNames));
            model.addAttribute("contractDraftRequest", draftRequest);
            // 额外传递一个用于前端JS初始化的JSON字符串
            try {
                model.addAttribute("initialAttachmentsJson", objectMapper.writeValueAsString(currentAttachmentFileNames));
            } catch (JsonProcessingException e) {
                logger.warn("序列化初始附件列表到JSON时出错 (Contract ID: {}): {}", contractId, e.getMessage());
                model.addAttribute("initialAttachmentsJson", "[]"); // 序列化失败则传空数组
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

    @PostMapping("/finalize/{contractId}")
    @PreAuthorize("hasAuthority('CON_FINAL_SUBMIT')") // 仅允许拥有 "CON_FINAL_SUBMIT" 权限的用户提交定稿
    public String processFinalizeContract(@PathVariable Long contractId,
                                          @RequestParam(required = false) String finalizationComments,
                                          @ModelAttribute("contractDraftRequest") @Valid ContractDraftRequest contractDraftRequest,
                                          BindingResult bindingResult,
                                          RedirectAttributes redirectAttributes,
                                          Principal principal,
                                          Model model) { // Model 用于在校验失败时保留数据和显示错误

        // 如果存在JSR-303/380验证错误
        if (bindingResult.hasErrors()) {
            logger.warn("Contract finalization form validation failed for contractId: {}", contractId); // 添加警告日志
            bindingResult.getAllErrors().forEach(error -> { // 遍历所有验证错误
                if (error instanceof org.springframework.validation.FieldError fieldError) {
                    logger.warn("Validation error in field '{}': {}", fieldError.getField(), fieldError.getDefaultMessage()); // 打印字段错误
                } else {
                    logger.warn("Validation error: {}", error.getDefaultMessage()); // 打印全局错误
                }
            });
            // 重新加载合同数据，以便在返回表单时显示完整信息和会签意见
            try {
                Contract contractToDisplay = contractService.getContractForFinalization(contractId, principal.getName());
                model.addAttribute("contract", contractToDisplay);
                List<ContractProcess> countersignOpinions = contractService.getContractCountersignOpinions(contractId);
                model.addAttribute("countersignOpinions", countersignOpinions);

                // 重新解析并传递附件列表，以便前端JS可以重新渲染文件列表
                List<String> currentAttachmentFileNames = new ArrayList<>();
                if (contractToDisplay != null && contractToDisplay.getAttachmentPath() != null &&
                        !contractToDisplay.getAttachmentPath().trim().isEmpty() &&
                        !contractToDisplay.getAttachmentPath().equals("[]") &&
                        !contractToDisplay.getAttachmentPath().equalsIgnoreCase("null")) {
                    try {
                        currentAttachmentFileNames = objectMapper.readValue(contractToDisplay.getAttachmentPath(), new TypeReference<List<String>>(){});
                    } catch (JsonProcessingException jsonEx) {
                        logger.warn("校验失败后重新加载附件JSON时出错 (Contract ID: {}): {}", contractId, jsonEx.getMessage());
                    }
                }
                model.addAttribute("currentAttachmentFileNames", currentAttachmentFileNames); // 用于前端显示
                // 再次将初始附件列表序列化为 JSON 字符串，传递给前端JS
                try {
                    model.addAttribute("initialAttachmentsJson", objectMapper.writeValueAsString(currentAttachmentFileNames));
                } catch (JsonProcessingException jsonEx) {
                    model.addAttribute("initialAttachmentsJson", "[]");
                }

            } catch (Exception loadEx) {
                logger.error("表单校验失败后重新加载合同信息失败 (Contract ID: {})", contractId, loadEx);
                redirectAttributes.addFlashAttribute("errorMessage", "表单校验失败，且重新加载合同信息以显示错误时也发生错误：" + loadEx.getMessage());
                return "redirect:/contract-manager/pending-finalization"; // 重定向到列表页
            }
            model.addAttribute("errorMessage", "表单提交无效，请检查输入。"); // 添加通用错误消息
            model.addAttribute("contractDraftRequest", contractDraftRequest); // 保持用户输入
            return "contract-manager/finalize-contract"; // 返回到定稿页面
        }

        try {
            String username = principal.getName();
            String updatedContent = contractDraftRequest.getUpdatedContent(); // 获取更新后的合同内容

            // 调用 ContractService 执行定稿操作，传递所有必要参数
            Contract finalizedContract = contractService.finalizeContract(
                    contractId,
                    finalizationComments,
                    contractDraftRequest.getAttachmentServerFileNames(), // 传递附件文件名列表
                    updatedContent, // 传递更新后的内容
                    username
            );

            redirectAttributes.addFlashAttribute("successMessage", "合同 " + finalizedContract.getContractName() + " (ID: " + contractId + ") 已成功定稿，并进入下一审批流程。");
            return "redirect:/contract-manager/pending-finalization";
        } catch (BusinessLogicException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "定稿失败: " + e.getMessage());
            return "redirect:/contract-manager/finalize/" + contractId; // 返回到定稿页面
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "定稿失败: 权限不足。");
            return "redirect:/contract-manager/finalize/" + contractId; // 返回到定稿页面
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "定稿失败: " + e.getMessage());
            return "redirect:/contract-manager/pending-finalization"; // 返回到列表页
        } catch (IOException e) {
            // 如果附件处理（例如删除临时文件）发生IO错误，应在当前页面显示错误，并重新加载数据
            logger.error("合同定稿附件处理失败 (Contract ID: {}): {}", contractId, e.getMessage(), e);
            model.addAttribute("errorMessage", "附件处理失败: " + e.getMessage());
            // 重新加载合同数据以便在当前页面显示错误
            try {
                Contract contractToDisplay = contractService.getContractForFinalization(contractId, principal.getName());
                model.addAttribute("contract", contractToDisplay);
                List<ContractProcess> countersignOpinions = contractService.getContractCountersignOpinions(contractId);
                model.addAttribute("countersignOpinions", countersignOpinions);

                // 重新解析并传递附件列表，以便前端JS可以重新渲染文件列表
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
                logger.error("附件IO异常后重新加载合同信息失败 (Contract ID: {})", contractId, loadEx);
                redirectAttributes.addFlashAttribute("errorMessage", "附件处理失败，且重新加载合同信息以显示错误时也发生错误：" + loadEx.getMessage());
                return "redirect:/contract-manager/pending-finalization"; // 重定向到列表页
            }
            return "contract-manager/finalize-contract"; // 返回到定稿页面
        } catch (Exception e) {
            // 捕获其他未知异常
            logger.error("合同定稿过程中发生未知系统错误 (Contract ID: {})", contractId, e);
            redirectAttributes.addFlashAttribute("errorMessage", "合同定稿过程中发生未知系统错误。");
            return "redirect:/contract-manager/finalize/" + contractId; // 返回到定稿页面
        }
    }

    @GetMapping("/pending-approval")
    @PreAuthorize("hasAuthority('CON_APPROVE_VIEW')") // 仅允许拥有 "CON_APPROVE_VIEW" 权限的用户访问
    public String pendingApprovalContracts(
            @PageableDefault(size = 10, sort = "contract.updatedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String contractNameSearch,
            Model model,
            Principal principal
    ) {
        String username = principal.getName();
        Page<ContractProcess> pendingProcessesPage = contractService.getPendingProcessesForUser(
                username, ContractProcessType.APPROVAL, ContractProcessState.PENDING, contractNameSearch, pageable);

        // 创建一个用于前端的新列表，其中包含解析后的附件文件名
        List<PendingApprovalItemDto> itemsWithAttachments = new ArrayList<>();
        for (ContractProcess process : pendingProcessesPage.getContent()) {
            List<String> attachmentPaths = new ArrayList<>();
            // 尝试解析合同的附件路径JSON字符串
            if (process.getContract() != null && process.getContract().getAttachmentPath() != null &&
                    !process.getContract().getAttachmentPath().trim().isEmpty() &&
                    !process.getContract().getAttachmentPath().equals("[]") &&
                    !process.getContract().getAttachmentPath().equalsIgnoreCase("null")) {
                try {
                    attachmentPaths = objectMapper.readValue(process.getContract().getAttachmentPath(), new TypeReference<List<String>>() {});
                } catch (JsonProcessingException e) {
                    logger.warn("解析待审批合同附件路径失败，合同ID {}: {}", process.getContract().getId(), e.getMessage());
                    // 即使解析失败，也继续处理，附件列表为空
                }
            }
            itemsWithAttachments.add(new PendingApprovalItemDto(process, attachmentPaths));
        }

        // 将包含DTO的Page对象传递给模型，保持分页结构
        Page<PendingApprovalItemDto> pendingApprovalsDtoPage = new PageImpl<>(
                itemsWithAttachments, pageable, pendingProcessesPage.getTotalElements());

        model.addAttribute("pendingApprovals", pendingApprovalsDtoPage); // 现在前端将接收到这个包含DTO的Page
        model.addAttribute("contractNameSearch", contractNameSearch != null ? contractNameSearch : "");
        model.addAttribute("listTitle", "待审批合同");
        return "contract-manager/pending-approval";
    }

    @GetMapping("/approval-details/{contractId}")
    @PreAuthorize("hasAuthority('CON_APPROVE_VIEW') or hasAuthority('CON_APPROVE_SUBMIT')") // 允许查看或提交审批的用户访问
    public String showApprovalDetails(@PathVariable Long contractId, Model model, Principal principal, RedirectAttributes redirectAttributes) {
        try {
            Contract contract = contractService.getContractById(contractId); // 获取合同详情
            boolean canActuallyApprove = contractService.canUserApproveContract(principal.getName(), contractId);
            model.addAttribute("canActuallyApprove", canActuallyApprove); // 用于前端按钮的启用/禁用

            // 提示信息，如果合同状态不是标准的待审批状态
            if (contract.getStatus() != ContractStatus.PENDING_APPROVAL && contract.getStatus() != ContractStatus.REJECTED ) {
                model.addAttribute("infoMessage", "提示：此合同当前状态为 " + contract.getStatus().getDescription() + "，可能并非处于标准的待审批环节。");
            }
            model.addAttribute("contract", contract);
            return "contract-manager/approval-details";
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "无法加载审批详情：" + e.getMessage());
            return "redirect:/contract-manager/pending-approval";
        }
        catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("errorMessage","权限不足，无法查看审批详情。");
            return "redirect:/dashboard"; // 或者返回到待审批列表
        }
    }


    @PostMapping("/approve/{contractId}")
    @PreAuthorize("hasAuthority('CON_APPROVE_SUBMIT')") // 仅允许拥有 "CON_APPROVE_SUBMIT" 权限的用户提交审批
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
            return "redirect:/contract-manager/pending-approval"; // 重定向回待审批列表
        } catch (BusinessLogicException | AccessDeniedException | ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "审批操作失败: " + e.getMessage());
            return "redirect:/contract-manager/approval-details/" + contractId; // 返回到审批详情页
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "审批过程中发生未知系统错误。");
            logger.error("处理审批合同未知错误 (Contract ID: {})", contractId, e);
            return "redirect:/contract-manager/approval-details/" + contractId; // 返回到审批详情页
        }
    }

    @GetMapping("/pending-signing")
    @PreAuthorize("hasAuthority('CON_SIGN_VIEW')") // 仅允许拥有 "CON_SIGN_VIEW" 权限的用户访问
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
    @PreAuthorize("hasAuthority('CON_SIGN_VIEW') or hasAuthority('CONTRACT_SIGN_SUBMIT')") // 允许查看或提交签订的用户访问
    public String showSignContractForm(@PathVariable Long contractProcessId, Model model, Principal principal, RedirectAttributes redirectAttributes) {
        try {
            String username = principal.getName();
            ContractProcess contractProcess = contractService.getContractProcessByIdAndOperator(
                    contractProcessId, username, ContractProcessType.SIGNING, ContractProcessState.PENDING); // 获取流程记录

            model.addAttribute("contractProcess", contractProcess);
            return "contract-manager/sign-contract";
        } catch (ResourceNotFoundException | BusinessLogicException | AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/contract-manager/pending-signing";
        }
    }

    @PostMapping("/sign")
    @PreAuthorize("hasAuthority('CONTRACT_SIGN_SUBMIT')") // 仅允许拥有 "CONTRACT_SIGN_SUBMIT" 权限的用户提交签订
    public String signContract(@RequestParam Long contractProcessId,
                               @RequestParam(required = false) String signingOpinion,
                               RedirectAttributes redirectAttributes,
                               Principal principal) {
        try {
            String username = principal.getName();
            contractService.signContract(contractProcessId, signingOpinion, username);
            redirectAttributes.addFlashAttribute("successMessage", "合同已成功标记为签订！");
            return "redirect:/contract-manager/pending-signing"; // 重定向回待签订列表
        } catch (ResourceNotFoundException | BusinessLogicException | AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "签订失败: " + e.getMessage());
            return "redirect:/contract-manager/sign/" + contractProcessId; // 返回到签订页面
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "签订过程中发生未知错误。");
            logger.error("处理签订合同未知错误 (Process ID: {})", contractProcessId, e);
            return "redirect:/contract-manager/pending-signing"; // 重定向回待签订列表
        }
    }

    @GetMapping("/view-all")
    @PreAuthorize("hasAuthority('CON_VIEW_MY') or hasRole('ROLE_ADMIN')") // 允许查看本人起草合同的用户或管理员访问
    public String viewAllContracts(
            Authentication authentication, // 获取认证信息，用于判断用户角色
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String contractName,
            @RequestParam(required = false) String contractNumber,
            @RequestParam(required = false) String status,
            Model model) {

        String currentUsername = authentication.getName();
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(ga -> ga.getAuthority().equals("ROLE_ADMIN")); // <-- 修正：移除了多余的单引号

        Page<Contract> contractsPage = contractService.searchContracts(currentUsername, isAdmin, contractName, contractNumber, status, pageable);

        model.addAttribute("contractsPage", contractsPage);
        model.addAttribute("contractName", contractName != null ? contractName : "");
        model.addAttribute("contractNumber", contractNumber != null ? contractNumber : "");
        model.addAttribute("status", status != null ? status : "");
        model.addAttribute("listTitle", "合同列表查询");
        // 确保指向正确的模板，根据您的项目结构，这应该是 "reports/contract-search" 或 "contract-manager/view-all-contracts" 等
        return "reports/contract-search"; // 重用报告模块的查询页面
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

            // 由于 getContractProcessHistory 已经在服务层完成了排序，这里不再需要手动排序
            // contractProcesses.sort((a, b) -> b.getCreatedAt().compareTo(b.getCreatedAt()));

            model.addAttribute("contract", contract);
            model.addAttribute("contractProcesses", contractProcesses);

            List<String> attachmentPaths = new ArrayList<>();
            if (contract.getAttachmentPath() != null && !contract.getAttachmentPath().trim().isEmpty()) {
                try {
                    attachmentPaths = objectMapper.readValue(contract.getAttachmentPath(), new TypeReference<List<String>>() {});
                } catch (JsonProcessingException e) {
                    logger.error("解析附件路径失败，合同ID {}: {}", contractId, e.getMessage());
                    model.addAttribute("errorMessage", "附件信息解析失败。");
                }
            }
            model.addAttribute("attachmentPaths", attachmentPaths);


            return "contracts/detail"; // 确保返回的是这个模板
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "无法找到指定的合同：" + e.getMessage());
            return "redirect:/reports/contract-search";
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "您没有权限查看此合同");
            return "redirect:/reports/contract-search";
        }
    }

    /**
     * 管理员直接延期合同。
     * 权限：仅限 ROLE_ADMIN。
     * @param contractId 合同ID。
     * @param request 包含新到期日期和备注的请求体。
     * @param authentication 认证信息。
     * @return 成功响应或错误响应。
     */
    @PostMapping("/{contractId}/extend/admin")
    @PreAuthorize("hasRole('ROLE_ADMIN')") // 仅允许管理员访问此端点
    @ResponseBody // 返回JSON响应，而不是视图
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
        } catch (ResourceNotFoundException e) {
            logger.warn("管理员延期合同失败，合同未找到 (合同ID: {}): {}", contractId, e.getMessage());
            return ResponseEntity.status(404).body("延期失败: " + e.getMessage());
        } catch (AccessDeniedException e) {
            logger.error("管理员延期合同失败，权限不足 (用户: {}): {}", authentication.getName(), e.getMessage());
            return ResponseEntity.status(403).body("延期失败: 权限不足。");
        } catch (Exception e) {
            logger.error("管理员延期合同发生未知错误 (合同ID: {}): {}", contractId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body("延期失败: 服务器内部错误。");
        }
    }

    /**
     * 操作员请求延期合同。
     * 权限：仅限 ROLE_CONTRACT_OPERATOR 或 ROLE_ADMIN (因为管理员是操作员的超集)。
     * @param contractId 合同ID。
     * @param request 包含期望新到期日期、原因和备注的请求体。
     * @param authentication 认证信息。
     * @return 成功响应或错误响应。
     */
    @PostMapping("/{contractId}/extend/request")
    @PreAuthorize("hasRole('ROLE_CONTRACT_OPERATOR') or hasRole('ROLE_ADMIN')") // 允许操作员或管理员访问此端点
    @ResponseBody // 返回JSON响应，而不是视图
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
        } catch (AccessDeniedException e) {
            logger.error("操作员请求延期合同失败，权限不足 (用户: {}): {}", authentication.getName(), e.getMessage());
            return ResponseEntity.status(403).body("提交请求失败: 权限不足。");
        } catch (Exception e) {
            logger.error("操作员请求延期合同发生未知错误 (合同ID: {}): {}", contractId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body("提交请求失败: 服务器内部错误。");
        }
    }
}