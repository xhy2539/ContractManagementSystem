package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.dto.ContractDraftRequest;
import com.example.contractmanagementsystem.entity.*;
import com.example.contractmanagementsystem.exception.BusinessLogicException;
import com.example.contractmanagementsystem.exception.ResourceNotFoundException;
import com.example.contractmanagementsystem.repository.ContractProcessRepository;
import com.example.contractmanagementsystem.repository.ContractRepository;
import com.example.contractmanagementsystem.repository.CustomerRepository;
import com.example.contractmanagementsystem.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
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
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ContractServiceImpl implements ContractService {

    @Value("${file.upload-dir}")
    private String uploadDirPropertyValue;

    private final ContractRepository contractRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final ContractProcessRepository contractProcessRepository;
    private final AuditLogService auditLogService;

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
                               AuditLogService auditLogService) {
        this.contractRepository = contractRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.contractProcessRepository = contractProcessRepository;
        this.auditLogService = auditLogService;
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

        Customer selectedCustomer = customerRepository.findById(request.getSelectedCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("选择的客户不存在，ID: " + request.getSelectedCustomerId()));

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
        contract.setCustomer(selectedCustomer);
        contract.setStartDate(request.getStartDate());
        contract.setEndDate(request.getEndDate());
        contract.setContent(request.getContractContent());
        contract.setDrafter(drafter);
        contract.setAttachmentPath(attachmentFilename);

        contract.setStatus(ContractStatus.PENDING_ASSIGNMENT); // 起草后等待分配

        Contract savedContract = contractRepository.save(contract);
        auditLogService.logAction(username, "CONTRACT_DRAFTED_FOR_ASSIGNMENT", "用户 " + username + " 起草了合同: " + savedContract.getContractName() + " (ID: " + savedContract.getId() + ")，状态变更为待分配。");
        return savedContract;
    }

    @Transactional
    public void processCountersign(Long contractProcessId, String comments, String username, boolean isApproved) {
        ContractProcess process = getContractProcessByIdAndOperator(contractProcessId, username, ContractProcessType.COUNTERSIGN, ContractProcessState.PENDING);

        process.setState(isApproved ? ContractProcessState.COMPLETED : ContractProcessState.REJECTED);
        process.setComments(comments);
        process.setProcessedAt(LocalDateTime.now());
        process.setCompletedAt(LocalDateTime.now());
        contractProcessRepository.save(process);

        Contract contract = process.getContract();
        contract.setUpdatedAt(LocalDateTime.now());

        String logDetails = "用户 " + username + (isApproved ? " 完成了" : " 拒绝了") + "对合同ID " + contract.getId() + " (“" + contract.getContractName() + "”) 的会签。意见: " + (StringUtils.hasText(comments) ? comments : "无");

        if (!isApproved) {
            contract.setStatus(ContractStatus.REJECTED);
            contractRepository.save(contract);
            auditLogService.logAction(username, "CONTRACT_COUNTERSIGN_REJECTED", logDetails);
            return;
        }

        boolean allRelevantCountersignsCompleted = contractProcessRepository.findByContractAndType(contract, ContractProcessType.COUNTERSIGN)
                .stream()
                .allMatch(p -> p.getState() == ContractProcessState.COMPLETED || p.getState() == ContractProcessState.APPROVED);

        if (allRelevantCountersignsCompleted) {
            contract.setStatus(ContractStatus.PENDING_FINALIZATION);
            logDetails += " 所有会签完成，合同进入待定稿状态。";
            auditLogService.logAction(username, "CONTRACT_ALL_COUNTERSIGNED", logDetails);
        } else {
            logDetails += " 尚有其他会签流程未完成。";
            auditLogService.logAction(username, "CONTRACT_PARTIALLY_COUNTERSIGNED", logDetails);
        }
        contractRepository.save(contract);
    }


    @Override
    @Transactional(readOnly = true)
    public Page<Contract> getContractsPendingFinalizationForUser(String username, String contractNameSearch, Pageable pageable) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户 '" + username + "' 不存在。"));

        Specification<Contract> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), ContractStatus.PENDING_FINALIZATION));

            if (StringUtils.hasText(contractNameSearch)) {
                predicates.add(cb.like(cb.lower(root.get("contractName")), "%" + contractNameSearch.toLowerCase().trim() + "%"));
            }

            if (query.getResultType().equals(Contract.class)) {
                root.fetch("customer", JoinType.LEFT);
                root.fetch("drafter", JoinType.LEFT);
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Contract> contractsPage = contractRepository.findAll(spec, pageable);
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

        if (contract.getStatus() != ContractStatus.PENDING_FINALIZATION) {
            throw new BusinessLogicException("合同当前状态为“" + contract.getStatus().getDescription() + "”，无法进行定稿操作。必须处于“待定稿”状态。");
        }

        Hibernate.initialize(contract.getCustomer());
        Hibernate.initialize(contract.getDrafter());
        if (contract.getDrafter() != null) {
            Hibernate.initialize(contract.getDrafter().getRoles());
        }
        return contract;
    }

    @Override
    @Transactional
    public Contract finalizeContract(Long contractId, String finalizationComments, MultipartFile newAttachment, String username) throws IOException {
        Contract contract = getContractForFinalization(contractId, username);
        User finalizer = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("执行定稿操作的用户 '" + username + "' 不存在。"));

        if (newAttachment != null && !newAttachment.isEmpty()) {
            String contentType = newAttachment.getContentType();
            if (contentType == null || !ALLOWED_FILE_TYPES.contains(contentType.toLowerCase())) {
                throw new BusinessLogicException("新附件的格式不正确。");
            }
            if (StringUtils.hasText(contract.getAttachmentPath())) {
                try {
                    Path oldFilePath = this.rootUploadPath.resolve(StringUtils.cleanPath(contract.getAttachmentPath())).normalize();
                    if (oldFilePath.startsWith(this.rootUploadPath) && Files.exists(oldFilePath)) {
                        Files.delete(oldFilePath);
                        auditLogService.logAction(username, "ATTACHMENT_DELETED_ON_FINALIZE", "合同ID " + contractId + " 定稿时删除了旧附件: " + contract.getAttachmentPath());
                    }
                } catch (IOException e) {
                    System.err.println("警告：删除旧附件失败 (" + contract.getAttachmentPath() + "): " + e.getMessage());
                }
            }
            String originalFilename = StringUtils.cleanPath(newAttachment.getOriginalFilename() != null ? newAttachment.getOriginalFilename() : "attachment");
            String fileExtension = "";
            if (originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String newFilenameBase = UUID.randomUUID().toString();
            String newFilename = newFilenameBase + fileExtension;
            Path targetLocation = this.rootUploadPath.resolve(newFilename).normalize();

            if (!targetLocation.startsWith(this.rootUploadPath)) {
                throw new BusinessLogicException("目标附件路径无效。");
            }
            if (!Files.exists(this.rootUploadPath)) {
                Files.createDirectories(this.rootUploadPath);
            }
            Files.copy(newAttachment.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            contract.setAttachmentPath(newFilename);
            auditLogService.logAction(username, "ATTACHMENT_UPDATED_ON_FINALIZE", "合同ID " + contractId + " 定稿时更新了附件为: " + newFilename);
        }

        contract.setStatus(ContractStatus.PENDING_APPROVAL);
        contract.setUpdatedAt(LocalDateTime.now());

        ContractProcess finalizationProcessRecord = new ContractProcess();
        finalizationProcessRecord.setContract(contract);
        finalizationProcessRecord.setContractNumber(contract.getContractNumber());
        finalizationProcessRecord.setType(ContractProcessType.FINALIZE);
        finalizationProcessRecord.setState(ContractProcessState.COMPLETED);
        finalizationProcessRecord.setOperator(finalizer);
        finalizationProcessRecord.setOperatorUsername(finalizer.getUsername());
        finalizationProcessRecord.setComments(finalizationComments);
        finalizationProcessRecord.setProcessedAt(LocalDateTime.now());
        finalizationProcessRecord.setCompletedAt(LocalDateTime.now());
        contractProcessRepository.save(finalizationProcessRecord);

        Contract savedContract = contractRepository.save(contract);

        String details = "合同ID " + contractId + " (“" + contract.getContractName() + "”) 已被用户 " + username + " 定稿，状态变更为“待审批”。";
        if (StringUtils.hasText(finalizationComments)) {
            details += " 定稿意见: “" + finalizationComments + "”。";
        }
        auditLogService.logAction(username, "CONTRACT_FINALIZED", details);

        return savedContract;
    }


    @Override
    @Transactional
    public void processApproval(Long contractId, String username, boolean approved, String comments) {
        Contract contract = getContractById(contractId);
        if (contract.getStatus() != ContractStatus.PENDING_APPROVAL) {
            throw new BusinessLogicException("合同当前状态为 " + contract.getStatus().getDescription() + "，不能进行审批。必须处于“待审批”状态。");
        }

        ContractProcess process = contractProcessRepository
                .findByContractIdAndOperatorUsernameAndTypeAndState(
                        contractId, username, ContractProcessType.APPROVAL, ContractProcessState.PENDING)
                .orElseThrow(() -> new AccessDeniedException("未找到待处理的审批任务，或您无权操作此合同的审批。"));

        process.setState(approved ? ContractProcessState.APPROVED : ContractProcessState.REJECTED);
        process.setComments(comments);
        process.setProcessedAt(LocalDateTime.now());
        process.setCompletedAt(LocalDateTime.now());
        contractProcessRepository.save(process);

        contract.setUpdatedAt(LocalDateTime.now());
        String logActionType = approved ? "CONTRACT_APPROVED" : "CONTRACT_REJECTED_APPROVAL";
        String logDetails = "用户 " + username + (approved ? " 批准了" : " 拒绝了") + "合同ID " + contractId + " (“" + contract.getContractName() + "”)的审批。审批意见: " + (StringUtils.hasText(comments) ? comments : "无");

        if (approved) {
            boolean allRelevantApprovalsCompletedAndApproved = contractProcessRepository.findByContractAndType(contract, ContractProcessType.APPROVAL)
                    .stream()
                    .allMatch(p -> ContractProcessState.APPROVED.equals(p.getState()));

            if (allRelevantApprovalsCompletedAndApproved) {
                contract.setStatus(ContractStatus.PENDING_SIGNING);
                logDetails += " 所有审批通过，合同进入待签订状态。";
            } else {
                logDetails += " 尚有其他审批流程未完成或未全部通过。";
            }
        } else {
            contract.setStatus(ContractStatus.REJECTED);
            logDetails += " 合同被拒绝，状态更新为已拒绝。";
        }
        contractRepository.save(contract);
        auditLogService.logAction(username, logActionType, logDetails);
    }


    @Override
    @Transactional
    public void signContract(Long contractProcessId, String signingOpinion, String username) {
        ContractProcess process = getContractProcessByIdAndOperator(
                contractProcessId, username, ContractProcessType.SIGNING, ContractProcessState.PENDING);

        Contract contract = process.getContract();
        if (contract.getStatus() != ContractStatus.PENDING_SIGNING) {
            throw new BusinessLogicException("合同当前状态为 " + contract.getStatus().getDescription() + "，不能进行签订。必须处于“待签订”状态。");
        }

        process.setState(ContractProcessState.COMPLETED);
        process.setComments(signingOpinion);
        process.setProcessedAt(LocalDateTime.now());
        process.setCompletedAt(LocalDateTime.now());
        contractProcessRepository.save(process);

        contract.setUpdatedAt(LocalDateTime.now());
        String logDetails = "用户 " + username + " 完成了对合同ID " + contract.getId() + " (“" + contract.getContractName() + "”) 的签订。签订意见: " + (StringUtils.hasText(signingOpinion) ? signingOpinion : "无");

        boolean allRelevantSigningsCompleted = contractProcessRepository.findByContractAndType(contract, ContractProcessType.SIGNING)
                .stream()
                .allMatch(p -> ContractProcessState.COMPLETED.equals(p.getState()));

        if (allRelevantSigningsCompleted) {
            contract.setStatus(ContractStatus.ACTIVE);
            logDetails += " 所有签订流程完成，合同状态更新为有效。";
            auditLogService.logAction(username, "CONTRACT_ALL_SIGNED_ACTIVE", logDetails);
        } else {
            logDetails += " 尚有其他签订流程未完成。";
            auditLogService.logAction(username, "CONTRACT_PARTIALLY_SIGNED", logDetails);
        }
        contractRepository.save(contract);
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

            Join<ContractProcess, Contract> contractJoin = root.join("contract", JoinType.INNER);

            switch (type) {
                case COUNTERSIGN:
                    predicates.add(cb.equal(contractJoin.get("status"), ContractStatus.PENDING_COUNTERSIGN));
                    break;
                case FINALIZE:
                    predicates.add(cb.equal(contractJoin.get("status"), ContractStatus.PENDING_FINALIZATION));
                    break;
                case APPROVAL:
                    predicates.add(cb.equal(contractJoin.get("status"), ContractStatus.PENDING_APPROVAL));
                    break;
                case SIGNING:
                    predicates.add(cb.equal(contractJoin.get("status"), ContractStatus.PENDING_SIGNING));
                    break;
                default:
                    break;
            }

            if (StringUtils.hasText(contractNameSearch)) {
                predicates.add(cb.like(cb.lower(contractJoin.get("contractName")), "%" + contractNameSearch.toLowerCase().trim() + "%"));
            }

            if (query.getResultType().equals(ContractProcess.class)) {
                root.fetch("operator", JoinType.LEFT);
                Fetch<ContractProcess, Contract> contractFetch = root.fetch("contract", JoinType.LEFT);
                contractFetch.fetch("customer", JoinType.LEFT);
                Fetch<Contract, User> drafterFetch = contractFetch.fetch("drafter", JoinType.LEFT);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<ContractProcess> resultPage = contractProcessRepository.findAll(spec, pageable);

        resultPage.getContent().forEach(process -> {
            Hibernate.initialize(process.getOperator());
            Hibernate.initialize(process.getContract());
            if (process.getContract() != null) {
                Hibernate.initialize(process.getContract().getCustomer());
                Hibernate.initialize(process.getContract().getDrafter());
            }
        });
        return resultPage;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractProcess> getAllPendingTasksForUser(String username) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessLogicException("用户未找到: " + username));

        Specification<ContractProcess> spec = (root, query, cb) -> {
            List<Predicate> mainPredicates = new ArrayList<>();
            mainPredicates.add(cb.equal(root.get("operator"), currentUser));
            mainPredicates.add(cb.equal(root.get("state"), ContractProcessState.PENDING));

            Join<ContractProcess, Contract> contractJoin = root.join("contract", JoinType.INNER);

            Predicate countersignTasks = cb.and(
                    cb.equal(root.get("type"), ContractProcessType.COUNTERSIGN),
                    cb.equal(contractJoin.get("status"), ContractStatus.PENDING_COUNTERSIGN)
            );
            Predicate finalizeTasks = cb.and(
                    cb.equal(root.get("type"), ContractProcessType.FINALIZE),
                    cb.equal(contractJoin.get("status"), ContractStatus.PENDING_FINALIZATION)
            );
            Predicate approvalTasks = cb.and(
                    cb.equal(root.get("type"), ContractProcessType.APPROVAL),
                    cb.equal(contractJoin.get("status"), ContractStatus.PENDING_APPROVAL)
            );
            Predicate signingTasks = cb.and(
                    cb.equal(root.get("type"), ContractProcessType.SIGNING),
                    cb.equal(contractJoin.get("status"), ContractStatus.PENDING_SIGNING)
            );

            mainPredicates.add(cb.or(countersignTasks, finalizeTasks, approvalTasks, signingTasks));


            if (query.getResultType().equals(ContractProcess.class)) {
                Fetch<ContractProcess, Contract> contractFetch = root.fetch("contract", JoinType.LEFT);
                contractFetch.fetch("customer", JoinType.LEFT);
                contractFetch.fetch("drafter", JoinType.LEFT);
            }
            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(mainPredicates.toArray(new Predicate[0]));
        };

        List<ContractProcess> tasks = contractProcessRepository.findAll(spec);
        tasks.forEach(task -> {
            Hibernate.initialize(task.getContract());
            if (task.getContract() != null) {
                Hibernate.initialize(task.getContract().getCustomer());
                Hibernate.initialize(task.getContract().getDrafter());
            }
        });
        return tasks;
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
        if (!filePath.startsWith(this.rootUploadPath)) {
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

        Hibernate.initialize(process.getContract());
        if (process.getContract() != null) {
            Hibernate.initialize(process.getContract().getCustomer());
            Hibernate.initialize(process.getContract().getDrafter());
            if (process.getContract().getDrafter() != null) {
                Hibernate.initialize(process.getContract().getDrafter().getRoles());
            }
        }
        Hibernate.initialize(process.getOperator());
        if (process.getOperator() != null) {
            Hibernate.initialize(process.getOperator().getRoles());
        }
        return process;
    }


    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getContractStatusStatistics() {
        List<Object[]> results = contractRepository.findContractCountByStatus();
        Map<String, Long> statistics = new HashMap<>();
        for (ContractStatus status : ContractStatus.values()) {
            statistics.put(status.name(), 0L);
        }
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
                    ContractStatus contractStatusEnum = ContractStatus.valueOf(status.toUpperCase());
                    predicates.add(criteriaBuilder.equal(root.get("status"), contractStatusEnum));
                } catch (IllegalArgumentException e) {
                    System.err.println("搜索合同中提供了无效的状态值: " + status + "。将忽略此状态条件。");
                }
            }
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

    // --- 新增：为仪表盘统计信息添加方法实现 ---
    @Override
    @Transactional(readOnly = true)
    public long countActiveContracts() {
        return contractRepository.countByStatus(ContractStatus.ACTIVE);
    }

    @Override
    @Transactional(readOnly = true)
    public long countContractsExpiringSoon(int days) {
        LocalDate today = LocalDate.now();
        LocalDate futureDate = today.plusDays(days);

        Specification<Contract> spec = (root, query, criteriaBuilder) -> {
            Predicate statusPredicate = criteriaBuilder.equal(root.get("status"), ContractStatus.ACTIVE);
            // 只统计结束日期在今天之后（不包括今天），但在 futureDate 或之前（包括 futureDate）的合同
            Predicate endDateRangePredicate = criteriaBuilder.between(root.get("endDate"), today.plusDays(1), futureDate);
            // 或者，如果您想包括今天到期的合同，可以使用：
            // Predicate endDateRangePredicate = criteriaBuilder.between(root.get("endDate"), today, futureDate);

            // 确保合同尚未过期（虽然 ACTIVE 状态通常意味着未过期，但双重检查无害）
            Predicate notYetExpiredPredicate = criteriaBuilder.greaterThanOrEqualTo(root.get("endDate"), today);

            return criteriaBuilder.and(statusPredicate, endDateRangePredicate, notYetExpiredPredicate);
        };
        // 确保 ContractRepository 继承了 JpaSpecificationExecutor<Contract>
        return contractRepository.count(spec);
    }
    // --- 结束新增 ---
}