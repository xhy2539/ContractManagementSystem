package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.dto.ContractDraftRequest;
import com.example.contractmanagementsystem.entity.*;
import com.example.contractmanagementsystem.exception.BusinessLogicException;
import com.example.contractmanagementsystem.exception.ResourceNotFoundException;
import com.example.contractmanagementsystem.repository.ContractProcessRepository;
import com.example.contractmanagementsystem.repository.ContractRepository;
import com.example.contractmanagementsystem.repository.CustomerRepository;
import com.example.contractmanagementsystem.repository.UserRepository;

// Jackson imports for JSON processing
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.beans.factory.annotation.Value; // Not used for upload paths here anymore
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.util.CollectionUtils; // For checking empty collections

import java.io.IOException;
// import java.nio.file.Files; // Not used directly here anymore
import java.nio.file.Path;
// import java.nio.file.Paths; // Not used directly here anymore
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ContractServiceImpl implements ContractService {

    private static final Logger logger = LoggerFactory.getLogger(ContractServiceImpl.class);

    private final ContractRepository contractRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final ContractProcessRepository contractProcessRepository;
    private final AuditLogService auditLogService;
    private final AttachmentService attachmentService;
    private final ObjectMapper objectMapper; // Added ObjectMapper

    @Autowired
    public ContractServiceImpl(ContractRepository contractRepository,
                               CustomerRepository customerRepository,
                               UserRepository userRepository,
                               ContractProcessRepository contractProcessRepository,
                               AuditLogService auditLogService,
                               AttachmentService attachmentService,
                               ObjectMapper objectMapper) { // Injected ObjectMapper
        this.contractRepository = contractRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.contractProcessRepository = contractProcessRepository;
        this.auditLogService = auditLogService;
        this.attachmentService = attachmentService;
        this.objectMapper = objectMapper; // Initialize ObjectMapper
    }

    @PostConstruct
    public void init() {
        logger.info("ContractServiceImpl initialized.");
    }

    @Override
    @Transactional
    public Contract draftContract(ContractDraftRequest request, String username) throws IOException {
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new BusinessLogicException("合同开始日期不能晚于结束日期！");
        }

        Customer selectedCustomer = customerRepository.findById(request.getSelectedCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("选择的客户不存在，ID: " + request.getSelectedCustomerId()));

        User drafter = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("起草人用户 '" + username + "' 不存在。"));

        Contract contract = new Contract();
        contract.setContractName(request.getContractName());
        String contractNumberGen = "CON-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        contract.setContractNumber(contractNumberGen);
        contract.setCustomer(selectedCustomer);
        contract.setStartDate(request.getStartDate());
        contract.setEndDate(request.getEndDate());
        contract.setContent(request.getContractContent());
        contract.setDrafter(drafter);

        // Handle multiple attachments
        if (!CollectionUtils.isEmpty(request.getAttachmentServerFileNames())) {
            try {
                String attachmentsJson = objectMapper.writeValueAsString(request.getAttachmentServerFileNames());
                contract.setAttachmentPath(attachmentsJson);
                logger.info("合同 '{}' 起草时设置附件路径: {}", contract.getContractName(), attachmentsJson);
            } catch (JsonProcessingException e) {
                logger.error("序列化附件文件名列表为JSON时出错 (合同: {}): {}", request.getContractName(), e.getMessage());
                throw new BusinessLogicException("处理附件信息时出错: " + e.getMessage());
            }
        } else {
            contract.setAttachmentPath(null); // No attachments or an empty list means no attachments
        }

        contract.setStatus(ContractStatus.PENDING_ASSIGNMENT);

        Contract savedContract = contractRepository.save(contract);
        String logDetails = "用户 " + username + " 起草了合同: " + savedContract.getContractName() +
                " (ID: " + savedContract.getId() + ")，状态变更为待分配。";
        if (StringUtils.hasText(savedContract.getAttachmentPath()) && !savedContract.getAttachmentPath().equals("[]") && !savedContract.getAttachmentPath().equals("null")) {
            logDetails += " 附件: " + savedContract.getAttachmentPath();
        }
        auditLogService.logAction(username, "CONTRACT_DRAFTED_FOR_ASSIGNMENT", logDetails);
        return savedContract;
    }

    @Override
    @Transactional
    public Contract finalizeContract(Long contractId, String finalizationComments, List<String> attachmentServerFileNames, String username) throws IOException {
        Contract contract = getContractForFinalization(contractId, username);
        User finalizer = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("执行定稿操作的用户 '" + username + "' 不存在。"));

        String oldAttachmentPath = contract.getAttachmentPath();
        String newAttachmentsJson = null;

        if (attachmentServerFileNames != null) { // Check if list is provided (could be empty)
            if (!attachmentServerFileNames.isEmpty()) {
                try {
                    newAttachmentsJson = objectMapper.writeValueAsString(attachmentServerFileNames);
                } catch (JsonProcessingException e) {
                    logger.error("序列化附件文件名列表为JSON时出错 (定稿合同ID: {}): {}", contractId, e.getMessage());
                    throw new BusinessLogicException("处理附件信息时出错: " + e.getMessage());
                }
            } else { // Empty list means remove all attachments
                newAttachmentsJson = "[]"; // or null, depending on how you want to represent no attachments
            }
            contract.setAttachmentPath(newAttachmentsJson); // Set new or empty list

            // Logging changes
            if (oldAttachmentPath == null && newAttachmentsJson != null && !newAttachmentsJson.equals("[]")) {
                logger.info("合同 {} 定稿时，新增附件: {}", contractId, newAttachmentsJson);
                auditLogService.logAction(username, "ATTACHMENT_ADDED_ON_FINALIZE", "合同ID " + contractId + " 定稿时新增附件: " + newAttachmentsJson);
            } else if (oldAttachmentPath != null && (newAttachmentsJson == null || newAttachmentsJson.equals("[]"))) {
                logger.info("合同 {} 定稿时，移除了所有附件 (原附件: {})", contractId, oldAttachmentPath);
                auditLogService.logAction(username, "ATTACHMENTS_REMOVED_ON_FINALIZE", "合同ID " + contractId + " 定稿时移除所有附件。原附件: " + oldAttachmentPath);
            } else if (oldAttachmentPath != null && newAttachmentsJson != null && !oldAttachmentPath.equals(newAttachmentsJson)) {
                logger.info("合同 {} 定稿时，附件由 '{}' 更新为 '{}'", contractId, oldAttachmentPath, newAttachmentsJson);
                auditLogService.logAction(username, "ATTACHMENTS_UPDATED_ON_FINALIZE", "合同ID " + contractId + " 定稿时附件由 " + oldAttachmentPath + " 更新为 " + newAttachmentsJson);
            }
        }
        // If attachmentServerFileNames is null, it means no changes to attachments were intended from the DTO.

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
        if (StringUtils.hasText(savedContract.getAttachmentPath()) && !savedContract.getAttachmentPath().equals("[]") && !savedContract.getAttachmentPath().equals("null")) {
            details += " 当前附件: " + savedContract.getAttachmentPath() + "。";
        }
        auditLogService.logAction(username, "CONTRACT_FINALIZED", details);

        return savedContract;
    }

    @Override
    @Transactional
    public void processCountersign(Long contractProcessId, String comments, String username, boolean isApproved) {
        ContractProcess process = getContractProcessByIdAndOperator(contractProcessId, username, ContractProcessType.COUNTERSIGN, ContractProcessState.PENDING);

        process.setState(isApproved ? ContractProcessState.COMPLETED : ContractProcessState.REJECTED);
        process.setComments(comments);
        process.setProcessedAt(LocalDateTime.now());
        process.setCompletedAt(LocalDateTime.now()); // Mark completion time
        contractProcessRepository.save(process);

        Contract contract = process.getContract();
        contract.setUpdatedAt(LocalDateTime.now()); // Update contract timestamp

        String logActionType = isApproved ? "CONTRACT_COUNTERSIGN_COMPLETED" : "CONTRACT_COUNTERSIGN_REJECTED";
        String logDetails = "用户 " + username + (isApproved ? " 完成了" : " 拒绝了") + "对合同ID " +
                contract.getId() + " (“" + contract.getContractName() + "”) 的会签。意见: " +
                (StringUtils.hasText(comments) ? comments : "无");

        if (!isApproved) {
            contract.setStatus(ContractStatus.REJECTED); // If any countersigner rejects, contract is rejected
            contractRepository.save(contract);
            auditLogService.logAction(username, logActionType, logDetails + " 合同状态变更为已拒绝。");
            return; // No further checks needed if rejected
        }

        // Check if all countersigns for this contract are completed (and approved)
        List<ContractProcess> allCountersignProcesses = contractProcessRepository.findByContractAndType(contract, ContractProcessType.COUNTERSIGN);
        boolean allRelevantCountersignsApproved = allCountersignProcesses.stream()
                .allMatch(p -> p.getState() == ContractProcessState.COMPLETED || p.getState() == ContractProcessState.APPROVED); // Or just COMPLETED if that's the only success state

        if (allRelevantCountersignsApproved) {
            contract.setStatus(ContractStatus.PENDING_FINALIZATION);
            logDetails += " 所有会签完成，合同进入待定稿状态。";
            auditLogService.logAction(username, "CONTRACT_ALL_COUNTERSIGNED", logDetails);
        } else {
            logDetails += " 尚有其他会签流程未完成或未全部通过。合同状态保持待会签。";
            auditLogService.logAction(username, logActionType, logDetails); // Still log the individual action
        }
        contractRepository.save(contract);
    }


    @Override
    public Path getAttachmentPath(String filename) {
        // This now directly calls the AttachmentService, which is specialized for this.
        return attachmentService.getAttachment(filename);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getContractStatusStatistics() {
        List<Object[]> results = contractRepository.findContractCountByStatus();
        Map<String, Long> statistics = new HashMap<>();
        for (ContractStatus status : ContractStatus.values()) {
            statistics.put(status.name(), 0L); // Initialize all statuses with 0 count
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
                    logger.warn("搜索合同中提供了无效的状态值: {}。将忽略此状态条件。", status);
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
            User drafter = contract.getDrafter();
            if (drafter != null) {
                Hibernate.initialize(drafter.getRoles());
                drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
            }
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
                contractFetch.fetch("drafter", JoinType.LEFT);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<ContractProcess> resultPage = contractProcessRepository.findAll(spec, pageable);

        resultPage.getContent().forEach(process -> {
            Hibernate.initialize(process.getOperator());
            User operator = process.getOperator();
            if (operator != null) {
                Hibernate.initialize(operator.getRoles());
                operator.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
            }

            Hibernate.initialize(process.getContract());
            if (process.getContract() != null) {
                // 确保合同名称被加载
                logger.debug("为任务ID {} 加载合同: {}, 名称: {}", process.getId(), process.getContract().getId(), process.getContract().getContractName());
                Hibernate.initialize(process.getContract().getCustomer());
                User drafter = process.getContract().getDrafter();
                if (drafter != null) {
                    Hibernate.initialize(drafter.getRoles());
                    drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
                }
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
                // 确保合同名称被加载 (用于日志和可能的调试)
                logger.info("仪表盘任务 - 合同ID: {}, 合同名称: {}", task.getContract().getId(), task.getContract().getContractName());
                Hibernate.initialize(task.getContract().getCustomer());
                User drafter = task.getContract().getDrafter();
                if (drafter != null) {
                    Hibernate.initialize(drafter.getRoles());
                    drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
                }
            }
            User operator = task.getOperator();
            if (operator != null) {
                Hibernate.initialize(operator.getRoles());
                operator.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
            }
        });
        return tasks;
    }
    @Override
    @Transactional(readOnly = true)
    public Contract getContractById(Long id) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("合同不存在，ID: " + id));
        Hibernate.initialize(contract.getCustomer());
        User drafter = contract.getDrafter();
        if (drafter != null) {
            Hibernate.initialize(drafter);
            Hibernate.initialize(drafter.getRoles());
            drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
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
    @Transactional
    public void processApproval(Long contractId, String username, boolean approved, String comments) {
        Contract contract = getContractById(contractId);

        if (contract.getStatus() != ContractStatus.PENDING_APPROVAL) {
            throw new BusinessLogicException("合同当前状态为 " + contract.getStatus().getDescription() + "，不能进行审批。必须处于“待审批”状态。");
        }

        ContractProcess process = contractProcessRepository
                .findByContractIdAndOperatorUsernameAndTypeAndState(
                        contractId, username, ContractProcessType.APPROVAL, ContractProcessState.PENDING)
                .orElseThrow(() -> new AccessDeniedException("未找到您的待处理审批任务，或您无权操作此合同的审批。"));

        process.setState(approved ? ContractProcessState.APPROVED : ContractProcessState.REJECTED);
        process.setComments(comments);
        process.setProcessedAt(LocalDateTime.now());
        process.setCompletedAt(LocalDateTime.now());
        contractProcessRepository.save(process);

        contract.setUpdatedAt(LocalDateTime.now());
        String logActionType = approved ? "CONTRACT_APPROVED" : "CONTRACT_REJECTED_APPROVAL";
        String logDetails = "用户 " + username + (approved ? " 批准了" : " 拒绝了") + "合同ID " + contractId +
                " (“" + contract.getContractName() + "”)的审批。审批意见: " +
                (StringUtils.hasText(comments) ? comments : "无");

        if (approved) {
            List<ContractProcess> allApprovalProcesses = contractProcessRepository.findByContractAndType(contract, ContractProcessType.APPROVAL);
            boolean allRelevantApprovalsCompletedAndApproved = allApprovalProcesses.stream()
                    .allMatch(p -> p.getState() == ContractProcessState.APPROVED || p.getState() == ContractProcessState.COMPLETED);

            if (allRelevantApprovalsCompletedAndApproved) {
                contract.setStatus(ContractStatus.PENDING_SIGNING);
                logDetails += " 所有审批通过，合同进入待签订状态。";
            } else {
                boolean anyRejected = allApprovalProcesses.stream()
                        .anyMatch(p -> p.getState() == ContractProcessState.REJECTED);
                if(anyRejected) {
                    contract.setStatus(ContractStatus.REJECTED);
                    logDetails += " 合同因流程中存在拒绝审批而被标记为已拒绝。";
                } else {
                    logDetails += " 尚有其他审批流程未完成或未全部通过。合同状态保持待审批。";
                }
            }
        } else {
            contract.setStatus(ContractStatus.REJECTED);
            logDetails += " 合同被拒绝，状态更新为已拒绝。";
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
            throw new AccessDeniedException("您 (" + username + ") 不是此合同流程 (ID: " + contractProcessId +
                    ", 操作员: " + process.getOperator().getUsername() + ") 的指定操作员，无权操作。");
        }
        if (process.getType() != expectedType) {
            throw new BusinessLogicException("合同流程类型不匹配。预期类型: " + expectedType.getDescription() +
                    ", 实际类型: " + process.getType().getDescription());
        }
        if (process.getState() != expectedState) {
            throw new BusinessLogicException("合同流程状态不正确。预期状态: " + expectedState.getDescription() +
                    ", 实际状态: " + process.getState().getDescription());
        }

        Hibernate.initialize(process.getContract());
        if (process.getContract() != null) {
            Hibernate.initialize(process.getContract().getCustomer());
            User drafter = process.getContract().getDrafter();
            if (drafter != null) {
                Hibernate.initialize(drafter);
                Hibernate.initialize(drafter.getRoles());
                drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
            }
        }
        User operator = process.getOperator();
        if (operator != null) {
            Hibernate.initialize(operator.getRoles());
            operator.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
        }
        return process;
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
        String logDetails = "用户 " + username + " 完成了对合同ID " + contract.getId() +
                " (“" + contract.getContractName() + "”) 的签订。签订意见: " +
                (StringUtils.hasText(signingOpinion) ? signingOpinion : "无");

        List<ContractProcess> allSigningProcesses = contractProcessRepository.findByContractAndType(contract, ContractProcessType.SIGNING);
        boolean allRelevantSigningsCompleted = allSigningProcesses.stream()
                .allMatch(p -> p.getState() == ContractProcessState.COMPLETED);

        if (allRelevantSigningsCompleted) {
            contract.setStatus(ContractStatus.ACTIVE);
            logDetails += " 所有签订流程完成，合同状态更新为有效。";
            auditLogService.logAction(username, "CONTRACT_ALL_SIGNED_ACTIVE", logDetails);
        } else {
            logDetails += " 尚有其他签订流程未完成。合同状态保持待签订。";
            auditLogService.logAction(username, "CONTRACT_PARTIALLY_SIGNED", logDetails);
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
            predicates.add(cb.equal(root.get("drafter"), currentUser));


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
            User drafter = contract.getDrafter();
            if (drafter != null) {
                Hibernate.initialize(drafter.getRoles());
                drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
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
            throw new BusinessLogicException("合同当前状态为“" + contract.getStatus().getDescription() +
                    "”，无法进行定稿操作。必须处于“待定稿”状态。");
        }

        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("当前用户 '" + username + "' 不存在。"));
        if (!contract.getDrafter().equals(currentUser)) {
            throw new AccessDeniedException("您 ("+username+") 不是此合同的起草人 ("+contract.getDrafter().getUsername()+")，无权定稿。");
        }

        Hibernate.initialize(contract.getCustomer());
        User drafter = contract.getDrafter();
        if (drafter != null) {
            Hibernate.initialize(drafter.getRoles());
            drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
        }
        return contract;
    }
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
            Predicate endDateRangePredicate = criteriaBuilder.and(
                    criteriaBuilder.greaterThan(root.get("endDate"), today),
                    criteriaBuilder.lessThanOrEqualTo(root.get("endDate"), futureDate)
            );
            return criteriaBuilder.and(statusPredicate, endDateRangePredicate);
        };
        return contractRepository.count(spec);
    }

    @Override
    @Transactional(readOnly = true)
    public long countContractsPendingAssignment() {
        return contractRepository.countByStatus(ContractStatus.PENDING_ASSIGNMENT);
    }
}