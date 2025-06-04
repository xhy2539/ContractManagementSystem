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
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Subquery;


import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors; // 确保导入 Collectors

@Service
public class ContractServiceImpl implements ContractService {

    private static final Logger logger = LoggerFactory.getLogger(ContractServiceImpl.class);

    private final ContractRepository contractRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final ContractProcessRepository contractProcessRepository;
    private final AuditLogService auditLogService;
    private final AttachmentService attachmentService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ContractServiceImpl(ContractRepository contractRepository,
                               CustomerRepository customerRepository,
                               UserRepository userRepository,
                               ContractProcessRepository contractProcessRepository,
                               AuditLogService auditLogService,
                               AttachmentService attachmentService,
                               ObjectMapper objectMapper) {
        this.contractRepository = contractRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.contractProcessRepository = contractProcessRepository;
        this.auditLogService = auditLogService;
        this.attachmentService = attachmentService;
        this.objectMapper = objectMapper;
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
        contract.setContent(request.getContractContent()); // 设置初始内容
        contract.setDrafter(drafter);

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
            contract.setAttachmentPath(null);
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
    public Contract finalizeContract(Long contractId, String finalizationComments, List<String> attachmentServerFileNames, String updatedContent, String username) throws IOException {
        Contract contract = getContractForFinalization(contractId, username); // This method performs permission and status checks
        User finalizer = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("执行定稿操作的用户 '" + username + "' 不存在。"));

        String oldAttachmentPath = contract.getAttachmentPath();
        String newAttachmentsJson = null;

        // Handle attachment updates
        if (attachmentServerFileNames != null) { // An empty list is different from null; empty list means "remove all attachments"
            if (!attachmentServerFileNames.isEmpty()) {
                try {
                    newAttachmentsJson = objectMapper.writeValueAsString(attachmentServerFileNames);
                } catch (JsonProcessingException e) {
                    logger.error("序列化附件文件名列表为JSON时出错 (定稿合同ID: {}): {}", contractId, e.getMessage());
                    throw new BusinessLogicException("处理附件信息时出错: " + e.getMessage());
                }
            } else { // Empty list explicitly passed
                newAttachmentsJson = "[]"; // Represent no attachments
            }
            contract.setAttachmentPath(newAttachmentsJson);

            // Log attachment changes
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
        } // If attachmentServerFileNames is null, attachments are not changed.

        // Handle updated contract content
        if (updatedContent != null && !updatedContent.equals(contract.getContent())) {
            contract.setContent(updatedContent);
            auditLogService.logAction(username, "CONTRACT_CONTENT_UPDATED_ON_FINALIZE", "合同ID " + contractId + " 内容在定稿时被更新。");
            logger.info("合同 {} 定稿时，内容被更新。", contractId);
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
        process.setCompletedAt(LocalDateTime.now());
        contractProcessRepository.save(process);

        Contract contract = process.getContract();
        contract.setUpdatedAt(LocalDateTime.now());

        String logActionType = isApproved ? "CONTRACT_COUNTERSIGN_COMPLETED" : "CONTRACT_COUNTERSIGN_REJECTED";
        String logDetails = "用户 " + username + (isApproved ? " 完成了" : " 拒绝了") + "对合同ID " +
                contract.getId() + " (“" + contract.getContractName() + "”) 的会签。意见: " +
                (StringUtils.hasText(comments) ? comments : "无");

        if (!isApproved) {
            contract.setStatus(ContractStatus.REJECTED); // 如果任何一方会签拒绝，合同直接标记为拒绝
            logDetails += " 合同因会签被拒绝，状态变更为已拒绝。";
            auditLogService.logAction(username, logActionType, logDetails);
            contractRepository.save(contract);
            return; // 流程终止
        }

        // 如果当前会签通过，检查是否所有其他会签也都已通过
        List<ContractProcess> allCountersignProcesses = contractProcessRepository.findByContractAndType(contract, ContractProcessType.COUNTERSIGN);
        boolean allRelevantCountersignsApproved = allCountersignProcesses.stream()
                .allMatch(p -> p.getState() == ContractProcessState.COMPLETED || p.getState() == ContractProcessState.APPROVED); // COMPLETED 也是一种通过

        if (allRelevantCountersignsApproved) {
            contract.setStatus(ContractStatus.PENDING_FINALIZATION); // 所有会签都通过后才进入待定稿
            logDetails += " 所有会签均完成且通过，合同进入待定稿状态。";
            auditLogService.logAction(username, "CONTRACT_ALL_COUNTERSIGNED", logDetails);
        } else {
            // 如果还有其他人的会签是 PENDING 或 REJECTED，则合同不能进入 PENDING_FINALIZATION
            // 如果有REJECTED，则在上面 !isApproved 分支已经处理。
            // 所以这里只可能是还有 PENDING 的。
            logDetails += " 当前会签已通过，但尚有其他会签流程未完成。合同状态保持待会签。";
            auditLogService.logAction(username, logActionType, logDetails);
        }
        contractRepository.save(contract);
    }


    @Override
    public Path getAttachmentPath(String filename) {
        return attachmentService.getAttachment(filename);
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
    public Page<Contract> searchContracts(String currentUsername, boolean isAdmin, String contractName, String contractNumber, String status, Pageable pageable) {
        Specification<Contract> spec = (Root<Contract> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) -> {
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

            if (!isAdmin && StringUtils.hasText(currentUsername)) {
                User currentUser = userRepository.findByUsername(currentUsername)
                        .orElse(null);

                if (currentUser != null) {
                    Predicate isDrafter = criteriaBuilder.equal(root.get("drafter"), currentUser);

                    Subquery<Long> subquery = query.subquery(Long.class);
                    Root<ContractProcess> contractProcessRoot = subquery.from(ContractProcess.class);
                    subquery.select(contractProcessRoot.get("contract").get("id"));

                    Predicate subqueryPredicate = criteriaBuilder.and(
                            criteriaBuilder.equal(contractProcessRoot.get("contract").get("id"), root.get("id")),
                            criteriaBuilder.equal(contractProcessRoot.get("operator"), currentUser)
                    );
                    subquery.where(subqueryPredicate);

                    Predicate isInvolvedInProcess = criteriaBuilder.exists(subquery);

                    predicates.add(criteriaBuilder.or(isDrafter, isInvolvedInProcess));
                } else {
                    logger.warn("为用户特定搜索提供了用户名 '{}'，但在数据库中未找到该用户。查询将不返回用户特定数据。", currentUsername);
                    predicates.add(criteriaBuilder.disjunction()); // effectively (1=0)
                }
            }

            if (query.getResultType().equals(Contract.class)) {
                root.fetch("customer", JoinType.LEFT);
                root.fetch("drafter", JoinType.LEFT);
            }

            query.distinct(true);
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

            Join<ContractProcess, Contract> contractJoin = root.join("contract", JoinType.INNER); // contract is an attribute in ContractProcess

            // Ensure contract's status matches the process type being queried
            switch (type) {
                case COUNTERSIGN:
                    predicates.add(cb.equal(contractJoin.get("status"), ContractStatus.PENDING_COUNTERSIGN));
                    break;
                case FINALIZE: // This case might be different as FINALIZE is done by drafter
                    // But if a ContractProcess of type FINALIZE is created, this logic is fine.
                    // If FINALIZE is not a process but a direct action on Contract by drafter,
                    // then getContractsPendingFinalizationForUser is the right method.
                    // Given a FINALIZE type in ContractProcessType, this implies it's a process.
                    predicates.add(cb.equal(contractJoin.get("status"), ContractStatus.PENDING_FINALIZATION));
                    break;
                case APPROVAL:
                    predicates.add(cb.equal(contractJoin.get("status"), ContractStatus.PENDING_APPROVAL));
                    break;
                case SIGNING:
                    predicates.add(cb.equal(contractJoin.get("status"), ContractStatus.PENDING_SIGNING));
                    break;
                default:
                    // Should not happen if type is always valid
                    break;
            }


            if (StringUtils.hasText(contractNameSearch)) {
                predicates.add(cb.like(cb.lower(contractJoin.get("contractName")), "%" + contractNameSearch.toLowerCase().trim() + "%"));
            }

            // Eager fetch related entities to avoid N+1 queries if not already handled by default EAGER or EntityGraph
            if (query.getResultType().equals(ContractProcess.class)) { // Check to avoid issues with count queries
                root.fetch("operator", JoinType.LEFT); // User
                Fetch<ContractProcess, Contract> contractFetch = root.fetch("contract", JoinType.LEFT); // Contract
                contractFetch.fetch("customer", JoinType.LEFT); // Customer via Contract
                contractFetch.fetch("drafter", JoinType.LEFT); // User (Drafter) via Contract
            }


            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<ContractProcess> resultPage = contractProcessRepository.findAll(spec, pageable);

        // Explicitly initialize if needed (though fetch should handle it for the main query)
        resultPage.getContent().forEach(process -> {
            Hibernate.initialize(process.getOperator()); // Operator
            User operator = process.getOperator();
            if (operator != null) {
                Hibernate.initialize(operator.getRoles());
                operator.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
            }

            Hibernate.initialize(process.getContract()); // Contract
            if (process.getContract() != null) {
                logger.debug("为任务ID {} 加载合同: {}, 名称: {}", process.getId(), process.getContract().getId(), process.getContract().getContractName());
                Hibernate.initialize(process.getContract().getCustomer()); // Customer
                User drafter = process.getContract().getDrafter(); // Drafter
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

            // Define predicates for each valid pending task scenario
            Predicate countersignTasks = cb.and(
                    cb.equal(root.get("type"), ContractProcessType.COUNTERSIGN),
                    cb.equal(contractJoin.get("status"), ContractStatus.PENDING_COUNTERSIGN)
            );
            // FINALIZE is typically an action by the drafter on the Contract itself,
            // not usually a ContractProcess assigned to the drafter in the same way as others.
            // However, if your system *does* create a PENDING FINALIZE process for the drafter:
            Predicate finalizeTasks = cb.and(
                    cb.equal(root.get("type"), ContractProcessType.FINALIZE), // Assuming FINALIZE is a process type
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

            // Combine these specific task conditions with OR
            // Ensure ContractProcessType.FINALIZE is handled correctly based on your design
            // If FINALIZE is not a 'task' assigned via ContractProcess in this way, remove 'finalizeTasks' from OR
            mainPredicates.add(cb.or(countersignTasks, approvalTasks, signingTasks, finalizeTasks)); // Added finalizeTasks


            // Eager fetching for the main query
            if (query.getResultType().equals(ContractProcess.class)) {
                Fetch<ContractProcess, Contract> contractFetch = root.fetch("contract", JoinType.LEFT);
                contractFetch.fetch("customer", JoinType.LEFT);
                contractFetch.fetch("drafter", JoinType.LEFT);
                root.fetch("operator", JoinType.LEFT); // Operator (current user)
            }
            query.orderBy(cb.desc(root.get("createdAt"))); // Order by creation time of the process

            return cb.and(mainPredicates.toArray(new Predicate[0]));
        };

        List<ContractProcess> tasks = contractProcessRepository.findAll(spec);
        // Explicitly initialize if needed (though fetch should handle it)
        tasks.forEach(task -> {
            // Log for debugging
            if (task.getContract() != null) {
                logger.info("仪表盘任务 - 合同ID: {}, 合同名称: {}, 合同状态: {}, 任务类型: {}",
                        task.getContract().getId(),
                        task.getContract().getContractName(),
                        task.getContract().getStatus(),
                        task.getType());
            }
            User operator = task.getOperator(); // Operator of the process
            if (operator != null) {
                Hibernate.initialize(operator.getRoles()); // Initialize roles of the operator
                operator.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
            }
            // Initialize drafter of the contract, if exists
            if (task.getContract() != null && task.getContract().getDrafter() != null) {
                User drafter = task.getContract().getDrafter();
                Hibernate.initialize(drafter.getRoles());
                drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
            }

        });
        return tasks;
    }


    @Override
    @Transactional(readOnly = true)
    public Contract getContractById(Long id) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("合同不存在，ID: " + id));
        // Eagerly fetch related entities
        Hibernate.initialize(contract.getCustomer());
        User drafter = contract.getDrafter();
        if (drafter != null) {
            Hibernate.initialize(drafter); // Initialize the drafter proxy
            Hibernate.initialize(drafter.getRoles()); // Initialize the roles collection
            drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities())); // Initialize functionalities for each role
        }
        return contract;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canUserApproveContract(String username, Long contractId) {
        // Check if there's a PENDING APPROVAL process for this user and contract
        return contractProcessRepository.findByContractIdAndOperatorUsernameAndTypeAndState(
                        contractId, username, ContractProcessType.APPROVAL, ContractProcessState.PENDING)
                .isPresent();
    }


    @Override
    @Transactional
    public void processApproval(Long contractId, String username, boolean approved, String comments) {
        Contract contract = getContractById(contractId); // Gets contract with initialized relations

        if (contract.getStatus() != ContractStatus.PENDING_APPROVAL) {
            throw new BusinessLogicException("合同当前状态为 " + contract.getStatus().getDescription() + "，不能进行审批。必须处于“待审批”状态。");
        }

        // Find the specific pending approval process for this user and contract
        ContractProcess process = contractProcessRepository
                .findByContractIdAndOperatorUsernameAndTypeAndState(
                        contractId, username, ContractProcessType.APPROVAL, ContractProcessState.PENDING)
                .orElseThrow(() -> new AccessDeniedException("未找到您的待处理审批任务，或您无权操作此合同的审批。"));

        process.setState(approved ? ContractProcessState.APPROVED : ContractProcessState.REJECTED);
        process.setComments(comments);
        process.setProcessedAt(LocalDateTime.now());
        process.setCompletedAt(LocalDateTime.now()); // Mark as completed regardless of approval/rejection
        contractProcessRepository.save(process);

        contract.setUpdatedAt(LocalDateTime.now());

        String logActionType = approved ? "CONTRACT_APPROVED" : "CONTRACT_REJECTED_APPROVAL";
        String logDetails = "用户 " + username + (approved ? " 批准了" : " 拒绝了") + "合同ID " + contractId +
                " (“" + contract.getContractName() + "”)的审批。审批意见: " +
                (StringUtils.hasText(comments) ? comments : "无");

        if (approved) {
            // Check if all approval processes for this contract are completed and approved
            List<ContractProcess> allApprovalProcesses = contractProcessRepository.findByContractAndType(contract, ContractProcessType.APPROVAL);
            boolean allRelevantApprovalsCompletedAndApproved = allApprovalProcesses.stream()
                    .allMatch(p -> p.getState() == ContractProcessState.APPROVED || p.getState() == ContractProcessState.COMPLETED); // Assuming COMPLETED implies prior approval

            if (allRelevantApprovalsCompletedAndApproved) {
                contract.setStatus(ContractStatus.PENDING_SIGNING); // Move to next state if all approvals are done
                logDetails += " 所有审批通过，合同进入待签订状态。";
            } else {
                // Check if any other approval was rejected, which would halt the process
                boolean anyRejected = allApprovalProcesses.stream()
                        .anyMatch(p -> p.getState() == ContractProcessState.REJECTED);
                if(anyRejected) {
                    contract.setStatus(ContractStatus.REJECTED); // If any approval is rejected, contract is rejected
                    logDetails += " 合同因流程中存在拒绝审批而被标记为已拒绝。";
                } else {
                    // Some approvals are still PENDING
                    logDetails += " 尚有其他审批流程未完成或未全部通过。合同状态保持待审批。";
                }
            }
        } else { // If this specific approval was a rejection
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

        // Eagerly fetch related entities
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
        User operator = process.getOperator(); // Already fetched, but ensure roles/functionalities
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

        process.setState(ContractProcessState.COMPLETED); // Signing process is completed by this user
        process.setComments(signingOpinion);
        process.setProcessedAt(LocalDateTime.now());
        process.setCompletedAt(LocalDateTime.now());
        contractProcessRepository.save(process);

        contract.setUpdatedAt(LocalDateTime.now());
        String logDetails = "用户 " + username + " 完成了对合同ID " + contract.getId() +
                " (“" + contract.getContractName() + "”) 的签订。签订意见: " +
                (StringUtils.hasText(signingOpinion) ? signingOpinion : "无");

        // Check if all assigned signing processes for this contract are completed
        List<ContractProcess> allSigningProcesses = contractProcessRepository.findByContractAndType(contract, ContractProcessType.SIGNING);
        boolean allRelevantSigningsCompleted = allSigningProcesses.stream()
                .allMatch(p -> p.getState() == ContractProcessState.COMPLETED); // All assigned must complete

        if (allRelevantSigningsCompleted) {
            contract.setStatus(ContractStatus.ACTIVE); // Contract becomes active if all have signed
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
            // Only the drafter can finalize
            predicates.add(cb.equal(root.get("drafter"), currentUser));


            if (StringUtils.hasText(contractNameSearch)) {
                predicates.add(cb.like(cb.lower(root.get("contractName")), "%" + contractNameSearch.toLowerCase().trim() + "%"));
            }

            // Eager fetch for main query
            if (query.getResultType().equals(Contract.class)) {
                root.fetch("customer", JoinType.LEFT);
                root.fetch("drafter", JoinType.LEFT); // Drafter (current user)
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Contract> contractsPage = contractRepository.findAll(spec, pageable);
        // Initialize related entities for DTO conversion or lazy loading issues
        contractsPage.getContent().forEach(contract -> {
            Hibernate.initialize(contract.getCustomer());
            User drafter = contract.getDrafter(); // Should be the current user
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
        if (contract.getDrafter() == null || !contract.getDrafter().equals(currentUser)) {
            logger.warn("用户 '{}' 尝试定稿合同 ID {}，但该合同由 '{}' 起草，或起草人为空。拒绝访问。",
                    username, contractId, (contract.getDrafter() != null ? contract.getDrafter().getUsername() : "未知"));
            throw new AccessDeniedException("您 ("+username+") 不是此合同的起草人，无权定稿。");
        }

        // Eagerly fetch related entities
        Hibernate.initialize(contract.getCustomer());
        User drafter = contract.getDrafter(); // Current user
        if (drafter != null) {
            Hibernate.initialize(drafter.getRoles());
            drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
        }
        return contract;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractProcess> getContractCountersignOpinions(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("获取会签意见失败：合同未找到，ID: " + contractId));

        List<ContractProcess> countersignProcesses = contractProcessRepository.findByContractAndType(contract, ContractProcessType.COUNTERSIGN);

        // 急切加载操作员信息，并按处理时间排序（如果尚未处理，则最后显示）
        countersignProcesses.forEach(process -> {
            Hibernate.initialize(process.getOperator()); // 确保操作员信息被加载
            // 如果操作员还有需要急切加载的关联对象（如角色），也可以在这里处理
            // Hibernate.initialize(process.getOperator().getRoles());
        });

        // 按处理时间排序，nullsLast表示未处理的（processedAt为null）排在后面
        return countersignProcesses.stream()
                .sorted(Comparator.comparing(ContractProcess::getProcessedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
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
                    criteriaBuilder.greaterThan(root.get("endDate"), today), // Must be after today to be "expiring soon" not "expired"
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