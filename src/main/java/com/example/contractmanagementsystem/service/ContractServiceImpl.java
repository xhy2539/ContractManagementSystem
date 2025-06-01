package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.dto.ContractDraftRequest;
import com.example.contractmanagementsystem.entity.*; // 确保 ContractStatus, User, ContractProcessType 等被导入
import com.example.contractmanagementsystem.exception.BusinessLogicException;
import com.example.contractmanagementsystem.exception.ResourceNotFoundException;
import jakarta.persistence.criteria.Fetch;
import org.springframework.security.access.AccessDeniedException;
import com.example.contractmanagementsystem.repository.ContractProcessRepository;
import com.example.contractmanagementsystem.repository.ContractRepository;
import com.example.contractmanagementsystem.repository.CustomerRepository;
import com.example.contractmanagementsystem.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType; // 确保导入 JoinType
import jakarta.persistence.criteria.Predicate;
import org.hibernate.Hibernate; // 导入 Hibernate 工具类
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ContractServiceImpl implements ContractService {

    @Value("${file.upload-dir}")
    private String uploadDirPropertyValue;

    private final ContractRepository contractRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final ContractProcessRepository contractProcessRepository;
    private final AuditLogService auditLogService; // 注入 AuditLogService

    private Path rootUploadPath;

    private static final List<String> ALLOWED_FILE_TYPES = Arrays.asList(
            "application/pdf",
            "image/jpeg", "image/png", "image/gif", "image/bmp",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    @Autowired
    public ContractServiceImpl(ContractRepository contractRepository,
                               CustomerRepository customerRepository,
                               UserRepository userRepository,
                               ContractProcessRepository contractProcessRepository,
                               AuditLogService auditLogService) { // 在构造函数中注入 AuditLogService
        this.contractRepository = contractRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.contractProcessRepository = contractProcessRepository;
        this.auditLogService = auditLogService; // 初始化 auditLogService
    }

    @PostConstruct
    public void init() {
        if (uploadDirPropertyValue == null || uploadDirPropertyValue.trim().isEmpty()) {
            System.err.println("错误：文件上传目录 'file.upload-dir' 未在 application.properties 中配置或配置为空！");
            throw new IllegalStateException("文件上传目录 'file.upload-dir' 未在 application.properties 中配置或配置为空！");
        }
        try {
            this.rootUploadPath = Paths.get(uploadDirPropertyValue).toAbsolutePath().normalize();
            if (!Files.exists(this.rootUploadPath)) {
                Files.createDirectories(this.rootUploadPath);
                System.out.println("信息：附件上传目录已创建: " + this.rootUploadPath.toString());
            } else {
                System.out.println("信息：附件上传目录已存在: " + this.rootUploadPath.toString());
            }
            if (!Files.isWritable(this.rootUploadPath)) {
                System.err.println("警告：附件上传目录不可写: " + this.rootUploadPath.toString());
                throw new IllegalStateException("附件上传目录不可写: " + this.rootUploadPath.toString() + "，请检查权限！");
            }
        } catch (IOException e) {
            System.err.println("错误：无法创建或访问附件上传目录 '" + uploadDirPropertyValue + "': " + e.getMessage());
            throw new RuntimeException("无法创建或访问附件上传目录: " + uploadDirPropertyValue, e);
        } catch (Exception e) {
            System.err.println("错误：解析附件上传目录路径 '" + uploadDirPropertyValue + "' 失败: " + e.getMessage());
            throw new RuntimeException("解析附件上传目录路径 '" + uploadDirPropertyValue + "' 失败", e);
        }
    }

    @Override
    @Transactional
    public Contract draftContract(ContractDraftRequest request, MultipartFile attachment, String username) throws IOException {
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new BusinessLogicException("合同开始日期不能晚于结束日期！");
        }

        Customer resolvedCustomer;
        String customerNumberFromRequest = request.getCustomerNumber().trim();
        String customerNameFromRequest = request.getCustomerName().trim();

        Optional<Customer> customerByNumberOpt = customerRepository.findByCustomerNumberIgnoreCase(customerNumberFromRequest);

        if (customerByNumberOpt.isPresent()) {
            resolvedCustomer = customerByNumberOpt.get();
            if (!resolvedCustomer.getCustomerName().equalsIgnoreCase(customerNameFromRequest)) {
                throw new BusinessLogicException(
                        "客户编号 '" + customerNumberFromRequest + "' 已存在，但其登记名称 ('" +
                                resolvedCustomer.getCustomerName() + "') 与您输入的名称 ('" +
                                customerNameFromRequest + "') 不匹配。请核实信息。"
                );
            }
        } else {
            if (customerRepository.existsByCustomerNameIgnoreCaseAndCustomerNumberNotIgnoreCase(customerNameFromRequest, customerNumberFromRequest)) {
                throw new BusinessLogicException(
                        "客户名称 '" + customerNameFromRequest + "' 已被系统中具有其他编号的客户使用。" +
                                "如果您要关联现有客户，请使用其正确的客户编号。如果要创建全新客户，请使用一个未被占用的客户名称。"
                );
            }
            Customer newCustomer = new Customer();
            newCustomer.setCustomerNumber(customerNumberFromRequest.toUpperCase());
            newCustomer.setCustomerName(customerNameFromRequest);
            resolvedCustomer = customerRepository.save(newCustomer);
        }

        User drafter = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("起草人用户 '" + username + "' 不存在。"));

        String attachmentFilename = null;
        if (attachment != null && !attachment.isEmpty()) {
            String contentType = attachment.getContentType();
            if (contentType == null || !ALLOWED_FILE_TYPES.contains(contentType.toLowerCase())) {
                throw new BusinessLogicException("附件格式不正确。当前格式: " + contentType + "。只允许上传PDF、图片、Word文件。");
            }
            String originalFilename = StringUtils.cleanPath(attachment.getOriginalFilename() != null ? attachment.getOriginalFilename() : "attachment");
            String fileExtension = "";
            if (originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String newFilename = UUID.randomUUID().toString() + fileExtension;
            Path filePath = this.rootUploadPath.resolve(newFilename);
            if (!Files.exists(this.rootUploadPath)) {
                Files.createDirectories(this.rootUploadPath);
            }
            Files.copy(attachment.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            attachmentFilename = newFilename;
        }

        Contract contract = new Contract();
        contract.setContractName(request.getContractName());
        String contractNumberGen = "CON-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        contract.setContractNumber(contractNumberGen);
        contract.setCustomer(resolvedCustomer);
        contract.setStartDate(request.getStartDate());
        contract.setEndDate(request.getEndDate());
        contract.setContent(request.getContractContent());
        contract.setStatus(ContractStatus.DRAFT);
        contract.setDrafter(drafter);
        contract.setAttachmentPath(attachmentFilename);
        // createdAt 和 updatedAt 会由 @CreationTimestamp 和 @UpdateTimestamp 自动处理
        // contract.setCreatedAt(LocalDateTime.now()); // 通常不需要手动设置
        // contract.setUpdatedAt(LocalDateTime.now()); // 通常不需要手动设置

        Contract savedContract = contractRepository.save(contract);
        auditLogService.logAction(username, "CONTRACT_DRAFTED", "用户 " + username + " 起草了新合同: " + savedContract.getContractName() + " (ID: " + savedContract.getId() + ")");
        return savedContract;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getContractStatusStatistics() {
        // 使用 ContractRepository 中定义的聚合查询方法
        List<Object[]> results = contractRepository.findContractCountByStatus(); // 假设您已在Repository中定义此方法
        Map<String, Long> statistics = new HashMap<>();
        // 初始化所有可能的状态，确保即使没有该状态的合同，也会显示计数为0
        for (ContractStatus status : ContractStatus.values()) {
            statistics.put(status.name(), 0L);
        }
        // 从查询结果填充实际数量
        for (Object[] result : results) {
            if (result[0] instanceof ContractStatus && result[1] instanceof Long) {
                ContractStatus status = (ContractStatus) result[0];
                Long count = (Long) result[1];
                statistics.put(status.name(), count);
            }
        }
        return statistics;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Contract> searchContracts(String contractName, String contractNumber, String status, Pageable pageable) {
        Specification<Contract> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(contractName)) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("contractName")), "%" + contractName.toLowerCase().trim() + "%"));
            }
            if (StringUtils.hasText(contractNumber)) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("contractNumber")), "%" + contractNumber.toLowerCase().trim() + "%"));
            }
            if (StringUtils.hasText(status)) {
                try {
                    ContractStatus contractStatus = ContractStatus.valueOf(status.toUpperCase());
                    predicates.add(criteriaBuilder.equal(root.get("status"), contractStatus));
                } catch (IllegalArgumentException e) {
                    System.err.println("搜索合同中提供了无效的状态值: " + status);
                }
            }
            // 列表页预加载关联数据
            if (query.getResultType().equals(Contract.class)) {
                root.fetch("customer", JoinType.LEFT);
                root.fetch("drafter", JoinType.LEFT);
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        Page<Contract> contractsPage = contractRepository.findAll(spec, pageable);
        contractsPage.getContent().forEach(contract -> {
            Hibernate.initialize(contract.getCustomer());
            Hibernate.initialize(contract.getDrafter());
        });
        return contractsPage;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ContractProcess> getPendingProcessesForUser(
            String username,
            ContractProcessType type,
            ContractProcessState state,
            String contractNameSearch,
            Pageable pageable) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessLogicException("用户不存在: " + username));
        Specification<ContractProcess> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("type"), type));
            predicates.add(cb.equal(root.get("state"), state));
            predicates.add(cb.equal(root.get("operator"), currentUser));

            if (StringUtils.hasText(contractNameSearch)) {
                // 正确的做法：使用 join 来获取 Join 对象，用于条件过滤
                Join<ContractProcess, Contract> contractJoin = root.join("contract", JoinType.LEFT); // 或者 JoinType.INNER 如果确定总是有contract
                predicates.add(cb.like(cb.lower(contractJoin.get("contractName")), "%" + contractNameSearch.toLowerCase().trim() + "%"));
            }
            if (query.getResultType().equals(ContractProcess.class)) {
                root.fetch("operator", JoinType.LEFT); // 预加载 operator
                Fetch<ContractProcess, Contract> contractFetchPath = root.fetch("contract", JoinType.LEFT);
                contractFetchPath.fetch("customer", JoinType.LEFT); // 在 Fetch 对象上继续 fetch
                contractFetchPath.fetch("drafter", JoinType.LEFT);
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<ContractProcess> resultPage = contractProcessRepository.findAll(spec, pageable);

        resultPage.getContent().forEach(process -> {
            Hibernate.initialize(process.getOperator());
            if (process.getOperator() != null) {
                Hibernate.initialize(process.getOperator().getRoles());
            }
            Hibernate.initialize(process.getContract());
            if (process.getContract() != null) {
                Hibernate.initialize(process.getContract().getCustomer());
                Hibernate.initialize(process.getContract().getDrafter());
                if (process.getContract().getDrafter() != null) {
                    Hibernate.initialize(process.getContract().getDrafter().getRoles());
                }
            }
        });
        return resultPage;
    }

    @Override
    public Path getAttachmentPath(String filename) {
        if (!StringUtils.hasText(filename)) {
            throw new BusinessLogicException("请求的附件文件名不能为空。");
        }
        String cleanedFilename = StringUtils.cleanPath(filename);
        if (cleanedFilename.contains("..") || cleanedFilename.startsWith("/") || cleanedFilename.startsWith("\\")) {
            throw new BusinessLogicException("附件文件名包含无效字符或路径。");
        }
        Path filePath = this.rootUploadPath.resolve(cleanedFilename).normalize();
        if (!filePath.startsWith(this.rootUploadPath)) { // 再次确认，防止符号链接等攻击
            throw new BusinessLogicException("试图访问上传目录之外的文件。");
        }
        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            throw new ResourceNotFoundException("附件文件不存在或不可读: " + cleanedFilename);
        }
        return filePath;
    }

    @Override
    @Transactional(readOnly = true)
    public Contract getContractById(Long id) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("合同不存在，ID: " + id));
        Hibernate.initialize(contract.getCustomer());
        Hibernate.initialize(contract.getDrafter());
        if (contract.getDrafter() != null) {
            Hibernate.initialize(contract.getDrafter().getRoles());
        }
        // 如果合同详情页需要显示流程历史，可以考虑初始化 ContractProcess 列表
        // Hibernate.initialize(contract.getContractProcesses()); // 假设 Contract 实体有这个字段
        return contract;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canUserApproveContract(String username, Long contractId) {
        return contractProcessRepository.findByContractIdAndOperatorUsernameAndTypeAndState(
                        contractId, username, ContractProcessType.APPROVAL, ContractProcessState.PENDING)
                .isPresent();
    }

    @Override
    @Transactional
    public void processApproval(Long contractId, String username, boolean approved, String comments) {
        Contract contract = getContractById(contractId); // 此方法已包含初始化
        ContractProcess process = contractProcessRepository
                .findByContractIdAndOperatorUsernameAndTypeAndState(
                        contractId, username, ContractProcessType.APPROVAL, ContractProcessState.PENDING)
                .orElseThrow(() -> new BusinessLogicException("未找到待处理的审批任务或您无权操作。"));

        process.setState(approved ? ContractProcessState.APPROVED : ContractProcessState.REJECTED);
        process.setComments(comments);
        process.setProcessedAt(LocalDateTime.now());
        process.setCompletedAt(LocalDateTime.now());
        contractProcessRepository.save(process);

        contract.setUpdatedAt(LocalDateTime.now());

        String logActionType = approved ? "CONTRACT_APPROVED" : "CONTRACT_REJECTED";
        String logDetails = "用户 " + username + (approved ? " 批准了" : " 拒绝了") + "合同ID " + contractId + " (“" + contract.getContractName() + "”)。审批意见: " + (StringUtils.hasText(comments) ? comments : "无");

        if (approved) {
            boolean allRelevantApprovalsCompletedAndApproved = contractProcessRepository.findByContractAndType(contract, ContractProcessType.APPROVAL)
                    .stream()
                    .allMatch(p -> ContractProcessState.APPROVED.equals(p.getState()));

            if (allRelevantApprovalsCompletedAndApproved) {
                contract.setStatus(ContractStatus.PENDING_SIGNING);
                logDetails += " 所有审批通过，合同进入待签订状态。";
            } else {
                logDetails += " 尚有其他审批流程未完成。";
            }
        } else {
            contract.setStatus(ContractStatus.REJECTED);
        }
        contractRepository.save(contract);
        auditLogService.logAction(username, logActionType, logDetails);
    }

    @Override
    @Transactional(readOnly = true)
    public ContractProcess getContractProcessByIdAndOperator(Long contractProcessId, String username, ContractProcessType expectedType, ContractProcessState expectedState) {
        ContractProcess process = contractProcessRepository.findById(contractProcessId)
                .orElseThrow(() -> new ResourceNotFoundException("合同流程记录未找到，ID: " + contractProcessId));

        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessLogicException("当前用户 '" + username + "' 不存在。"));

        if (!process.getOperator().equals(currentUser)) {
            throw new AccessDeniedException("您 (" + username + ") 不是此合同流程 (ID: " + contractProcessId + ", 操作员: " + process.getOperator().getUsername() + ") 的指定操作员，无权操作。");
        }
        if (process.getType() != expectedType) {
            throw new BusinessLogicException("合同流程类型不匹配。预期类型: " + expectedType.getDescription() + ", 实际类型: " + process.getType().getDescription());
        }
        if (process.getState() != expectedState) {
            throw new BusinessLogicException("合同流程状态不正确。预期状态: " + expectedState.getDescription() + ", 实际状态: " + process.getState().getDescription());
        }

        // 初始化关联实体，确保在Controller或模板中访问时数据可用
        Hibernate.initialize(process.getContract());
        if (process.getContract() != null) {
            Hibernate.initialize(process.getContract().getCustomer());
            Hibernate.initialize(process.getContract().getDrafter());
            if (process.getContract().getDrafter() != null) {
                Hibernate.initialize(process.getContract().getDrafter().getRoles());
            }
        }
        Hibernate.initialize(process.getOperator()); // 虽然已获取currentUser，但这是 process 上的 operator
        if (process.getOperator() != null) {
            Hibernate.initialize(process.getOperator().getRoles());
        }
        return process;
    }

    @Override
    @Transactional
    public void signContract(Long contractProcessId, String signingOpinion, String username) {
        ContractProcess process = getContractProcessByIdAndOperator(
                contractProcessId, username, ContractProcessType.SIGNING, ContractProcessState.PENDING);

        process.setState(ContractProcessState.COMPLETED);
        process.setComments(signingOpinion);
        process.setProcessedAt(LocalDateTime.now());
        process.setCompletedAt(LocalDateTime.now());
        contractProcessRepository.save(process);

        Contract contract = process.getContract();
        contract.setUpdatedAt(LocalDateTime.now());

        String logDetails = "用户 " + username + " 完成了对合同ID " + contract.getId() + " (“" + contract.getContractName() + "”) 的签订。签订意见: " + (StringUtils.hasText(signingOpinion) ? signingOpinion : "无");

        boolean allRelevantSigningsCompleted = contractProcessRepository.findByContractAndType(contract, ContractProcessType.SIGNING)
                .stream()
                .allMatch(p -> ContractProcessState.COMPLETED.equals(p.getState()));

        if (allRelevantSigningsCompleted) {
            contract.setStatus(ContractStatus.ACTIVE);
            logDetails += " 所有签订流程完成，合同状态更新为有效。";
            auditLogService.logAction(username, "CONTRACT_ALL_SIGNED", logDetails);
        } else {
            logDetails += " 尚有其他签订流程未完成。";
            auditLogService.logAction(username, "CONTRACT_PARTIALLY_SIGNED", logDetails);
        }
        contractRepository.save(contract);
    }

    // --- 新增“定稿合同”相关方法的具体实现 ---

    @Override
    @Transactional(readOnly = true)
    public Page<Contract> getContractsPendingFinalizationForUser(String username, String contractNameSearch, Pageable pageable) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户 '" + username + "' 不存在。"));

        Specification<Contract> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), ContractStatus.PENDING_FINALIZATION));

            // 业务规则：谁有权查看待定稿列表？
            // 选项1: 只有合同的起草人能看到他起草的待定稿合同
            // predicates.add(cb.equal(root.get("drafter"), currentUser));
            // 选项2: 任何拥有 CON_FINAL_VIEW 权限的用户都可以看到所有待定稿合同
            //         这种情况下，用户过滤不是在这里做，而是在Controller通过权限控制。
            // 当前选择选项2的思路，服务层返回所有符合状态的合同。
            // 如果您希望只有起草人能看到，请取消上面那行对 drafter 的过滤注释。

            if (StringUtils.hasText(contractNameSearch)) {
                predicates.add(cb.like(cb.lower(root.get("contractName")), "%" + contractNameSearch.toLowerCase().trim() + "%"));
            }

            // 为列表页预加载关联数据
            if (query.getResultType().equals(Contract.class)) { // 仅在主查询时 fetch，避免影响 count 查询
                root.fetch("customer", JoinType.LEFT);
                root.fetch("drafter", JoinType.LEFT);
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Contract> contractsPage = contractRepository.findAll(spec, pageable);
        // 对查询结果进行显式初始化，确保模板渲染时关联数据可用
        contractsPage.getContent().forEach(contract -> {
            Hibernate.initialize(contract.getCustomer());
            Hibernate.initialize(contract.getDrafter());
            if (contract.getDrafter() != null) {
                Hibernate.initialize(contract.getDrafter().getRoles());
            }
        });
        return contractsPage;
    }

    @Override
    @Transactional(readOnly = true)
    public Contract getContractForFinalization(Long contractId, String username) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("合同未找到，ID: " + contractId));

        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("当前用户 '" + username + "' 不存在。"));

        // 状态检查：合同必须处于“待定稿”状态
        if (contract.getStatus() != ContractStatus.PENDING_FINALIZATION) {
            throw new BusinessLogicException("合同当前状态为“" + contract.getStatus().getDescription() + "”，无法进行定稿。请确保合同处于“待定稿”状态。");
        }

        // 权限检查：根据您的业务规则决定谁有权定稿。
        // 例如，如果只有起草人可以定稿，或者具有特定权限的用户可以定稿。
        // 这里假设权限检查主要由Controller层的@PreAuthorize("hasAuthority('CON_FINAL_SUBMIT')")完成。
        // 如果需要更细致的业务逻辑检查（比如，是否为起草人），可以在这里添加：
        // if (!contract.getDrafter().equals(currentUser)) {
        //     throw new AccessDeniedException("您不是此合同的起草人，无权进行定稿操作。");
        // }

        // 为定稿详情页预加载所需数据
        Hibernate.initialize(contract.getCustomer());
        Hibernate.initialize(contract.getDrafter());
        if (contract.getDrafter() != null) {
            Hibernate.initialize(contract.getDrafter().getRoles());
        }
        // 如果需要在定稿页面显示历史流程，也应初始化
        // 例如: List<ContractProcess> processes = contractProcessRepository.findByContractOrderByCreatedAtDesc(contract);
        // Hibernate.initialize(processes); // 然后在 Contract 实体中有一个瞬时字段来存放它们，或直接传递

        return contract;
    }

    @Override
    @Transactional
    public Contract finalizeContract(Long contractId, String finalizationComments, MultipartFile newAttachment, String username) throws IOException {
        // 1. 获取合同并进行状态和权限检查 (getContractForFinalization已包含部分检查)
        Contract contract = getContractForFinalization(contractId, username); // 此方法会检查合同状态和用户存在性
        User finalizer = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("执行定稿操作的用户 '" + username + "' 不存在。")); // 再次确认操作用户

        // 2. (可选) 更新合同内容或特定字段（如果业务允许在定稿时修改）
        // contract.setSomeField(updatedValue);

        // 3. 处理新附件（如果提供）
        if (newAttachment != null && !newAttachment.isEmpty()) {
            String contentType = newAttachment.getContentType();
            if (contentType == null || !ALLOWED_FILE_TYPES.contains(contentType.toLowerCase())) {
                throw new BusinessLogicException("新附件的格式不正确。支持的格式有：PDF、图片 (JPEG, PNG, GIF, BMP)、Word文档 (doc, docx)。");
            }

            // 安全地删除旧附件（如果存在）
            if (StringUtils.hasText(contract.getAttachmentPath())) {
                try {
                    Path oldFilePath = this.rootUploadPath.resolve(StringUtils.cleanPath(contract.getAttachmentPath())).normalize();
                    if (oldFilePath.startsWith(this.rootUploadPath) && Files.exists(oldFilePath)) {
                        Files.delete(oldFilePath);
                        // 审计日志: 记录旧附件被删除
                        auditLogService.logAction(username, "ATTACHMENT_DELETED_ON_FINALIZE", "合同ID " + contractId + " 定稿时删除了旧附件: " + contract.getAttachmentPath());
                    }
                } catch (IOException e) {
                    System.err.println("警告：删除旧附件失败 (" + contract.getAttachmentPath() + "): " + e.getMessage());
                    // 根据业务需求决定是否因为删除旧附件失败而中断整个定稿操作
                }
            }

            // 保存新附件
            String originalFilename = StringUtils.cleanPath(newAttachment.getOriginalFilename() != null ? newAttachment.getOriginalFilename() : "attachment");
            String fileExtension = "";
            if (originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String newFilenameBase = UUID.randomUUID().toString();
            String newFilename = newFilenameBase + fileExtension;
            Path targetLocation = this.rootUploadPath.resolve(newFilename).normalize();

            if (!targetLocation.startsWith(this.rootUploadPath)) {
                throw new BusinessLogicException("目标附件路径无效，可能包含非法字符。");
            }
            if (!Files.exists(this.rootUploadPath)) {
                Files.createDirectories(this.rootUploadPath);
            }
            Files.copy(newAttachment.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            contract.setAttachmentPath(newFilename); // 更新合同中的附件路径
            // 审计日志: 记录新附件被添加/替换
            auditLogService.logAction(username, "ATTACHMENT_UPDATED_ON_FINALIZE", "合同ID " + contractId + " 定稿时更新了附件为: " + newFilename);
        }

        // 4. 更新合同状态至下一阶段，例如 PENDING_APPROVAL
        contract.setStatus(ContractStatus.PENDING_APPROVAL);
        contract.setUpdatedAt(LocalDateTime.now());

        // 5. 创建 ContractProcess 记录来跟踪定稿操作和保存意见
        ContractProcess finalizationProcessRecord = new ContractProcess();
        finalizationProcessRecord.setContract(contract);
        finalizationProcessRecord.setContractNumber(contract.getContractNumber());
        finalizationProcessRecord.setType(ContractProcessType.FINALIZE);
        finalizationProcessRecord.setState(ContractProcessState.COMPLETED); // 定稿操作一旦提交即视为完成
        finalizationProcessRecord.setOperator(finalizer);
        finalizationProcessRecord.setOperatorUsername(finalizer.getUsername());
        finalizationProcessRecord.setComments(finalizationComments); // 保存定稿意见
        finalizationProcessRecord.setProcessedAt(LocalDateTime.now()); // 处理时间
        finalizationProcessRecord.setCompletedAt(LocalDateTime.now()); // 完成时间
        contractProcessRepository.save(finalizationProcessRecord);

        Contract savedContract = contractRepository.save(contract);

        // 6. 记录核心审计日志
        String details = "合同ID " + contractId + " (“" + contract.getContractName() + "”) 已被用户 " + username + " 定稿，状态变更为“待审批”。";
        if (StringUtils.hasText(finalizationComments)) {
            details += " 定稿意见: “" + finalizationComments + "”。";
        }
        if (newAttachment != null && !newAttachment.isEmpty() && StringUtils.hasText(savedContract.getAttachmentPath())) {
            details += " 附件已更新为: " + savedContract.getAttachmentPath() + "。";
        }
        auditLogService.logAction(username, "CONTRACT_FINALIZED", details);

        return savedContract;
    }
}