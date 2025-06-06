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
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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
        this.objectMapper = objectMapper;
        this.attachmentService = attachmentService;
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
        // 生成唯一合同编号，可以根据实际需求调整生成规则
        String contractNumberGen = "CON-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        contract.setContractNumber(contractNumberGen);
        contract.setCustomer(selectedCustomer);
        contract.setStartDate(request.getStartDate());
        contract.setEndDate(request.getEndDate());
        contract.setContent(request.getContractContent()); // 设置初始内容
        contract.setDrafter(drafter);

        // 处理附件路径列表
        if (!CollectionUtils.isEmpty(request.getAttachmentServerFileNames())) {
            try {
                // 将附件文件名列表序列化为 JSON 字符串存储
                String attachmentsJson = objectMapper.writeValueAsString(request.getAttachmentServerFileNames());
                contract.setAttachmentPath(attachmentsJson);
                logger.info("合同 '{}' 起草时设置附件路径: {}", contract.getContractName(), attachmentsJson);
            } catch (JsonProcessingException e) {
                logger.error("序列化附件文件名列表为JSON时出错 (合同: {}): {}", request.getContractName(), e.getMessage());
                throw new BusinessLogicException("处理附件信息时出错: " + e.getMessage());
            }
        } else {
            contract.setAttachmentPath(null); // 如果没有附件，设置为 null 或空 JSON 数组 "[]"
        }

        // 合同起草后，状态变更为待分配
        contract.setStatus(ContractStatus.PENDING_ASSIGNMENT);

        Contract savedContract = contractRepository.save(contract);
        String logDetails = "用户 " + username + " 起草了合同: " + savedContract.getContractName() +
                " (ID: " + savedContract.getId() + ")，状态变更为待分配。";
        // 审计日志中也包含附件信息
        if (StringUtils.hasText(savedContract.getAttachmentPath()) && !savedContract.getAttachmentPath().equals("[]") && !savedContract.getAttachmentPath().equals("null")) {
            logDetails += " 附件: " + savedContract.getAttachmentPath();
        }
        auditLogService.logAction(username, "CONTRACT_DRAFTED_FOR_ASSIGNMENT", logDetails);
        return savedContract;
    }

    @Override
    @Transactional
    public Contract finalizeContract(Long contractId, String finalizationComments, List<String> attachmentServerFileNames, String updatedContent, String username) throws IOException {
        // 1. 获取合同并进行权限和状态检查
        Contract contract = getContractForFinalization(contractId, username); //
        User finalizer = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("执行定稿操作的用户 '" + username + "' 不存在。"));

        // 2. 处理附件更新
        String oldAttachmentPath = contract.getAttachmentPath();
        String newAttachmentsJson = null;

        if (attachmentServerFileNames != null) { // 如果传入的列表不是 null，说明前端有明确的附件更新意图
            if (!attachmentServerFileNames.isEmpty()) {
                try {
                    newAttachmentsJson = objectMapper.writeValueAsString(attachmentServerFileNames);
                } catch (JsonProcessingException e) {
                    logger.error("序列化附件文件名列表为JSON时出错 (定稿合同ID: {}): {}", contractId, e.getMessage());
                    throw new BusinessLogicException("处理附件信息时出错: " + e.getMessage());
                }
            } else {
                newAttachmentsJson = "[]"; // 明确表示没有附件
            }
            contract.setAttachmentPath(newAttachmentsJson);

            // 记录附件变化到审计日志
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
        } // 如果 attachmentServerFileNames 为 null，表示前端没有提供新的附件列表，则不修改现有附件。

        // 3. 处理合同内容更新
        // 只有当传入的 updatedContent 不为空且与现有内容不同时才更新
        if (updatedContent != null && !updatedContent.equals(contract.getContent())) {
            contract.setContent(updatedContent);
            auditLogService.logAction(username, "CONTRACT_CONTENT_UPDATED_ON_FINALIZE", "合同ID " + contractId + " 内容在定稿时被更新。");
            logger.info("合同 {} 定稿时，内容被更新。", contractId);
        }

        // 4. 更新合同状态为待审批
        contract.setStatus(ContractStatus.PENDING_APPROVAL); //
        contract.setUpdatedAt(LocalDateTime.now());

        // 5. 记录定稿流程
        ContractProcess finalizationProcessRecord = new ContractProcess();
        finalizationProcessRecord.setContract(contract);
        finalizationProcessRecord.setContractNumber(contract.getContractNumber());
        finalizationProcessRecord.setType(ContractProcessType.FINALIZE); //
        finalizationProcessRecord.setState(ContractProcessState.COMPLETED); //
        finalizationProcessRecord.setOperator(finalizer);
        finalizationProcessRecord.setOperatorUsername(finalizer.getUsername());
        finalizationProcessRecord.setComments(finalizationComments);
        finalizationProcessRecord.setProcessedAt(LocalDateTime.now());
        finalizationProcessRecord.setCompletedAt(LocalDateTime.now());
        contractProcessRepository.save(finalizationProcessRecord);

        // 6. 保存合同
        Contract savedContract = contractRepository.save(contract);

        // 7. 记录审计日志
        String details = "合同ID " + contractId + " (" + contract.getContractName() + ") 已被用户 " + username + " 定稿，状态变更为待审批。";
        if (StringUtils.hasText(finalizationComments)) {
            details += " 定稿意见: " + finalizationComments + "。";
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
        // 1. 获取会签流程记录并进行权限和状态检查
        ContractProcess process = getContractProcessByIdAndOperator(contractProcessId, username, ContractProcessType.COUNTERSIGN, ContractProcessState.PENDING); //

        // 2. 更新当前会签流程的状态
        process.setState(isApproved ? ContractProcessState.APPROVED : ContractProcessState.REJECTED); // 会签可以是 APPROVED 或 REJECTED
        process.setComments(comments);
        process.setProcessedAt(LocalDateTime.now());
        process.setCompletedAt(LocalDateTime.now());
        contractProcessRepository.save(process);

        Contract contract = process.getContract();
        contract.setUpdatedAt(LocalDateTime.now());

        String logActionType = isApproved ? "CONTRACT_COUNTERSIGN_APPROVED" : "CONTRACT_COUNTERSIGN_REJECTED"; // 细分审计类型
        String logDetails = "用户 " + username + (isApproved ? " 批准了" : " 拒绝了") + "合同ID " +
                contract.getId() + " (" + contract.getContractName() + ") 的会签。意见: " +
                (StringUtils.hasText(comments) ? comments : "无");

        // 3. 根据会签结果和合同其他会签情况更新合同状态
        if (!isApproved) {
            // 如果任何一方会签拒绝，合同直接标记为拒绝
            contract.setStatus(ContractStatus.REJECTED); //
            logDetails += " 合同因会签被拒绝，状态变更为已拒绝。";
            auditLogService.logAction(username, logActionType, logDetails);
            contractRepository.save(contract);
            return; // 流程终止，不再检查其他会签
        }

        // 如果当前会签通过，检查是否所有其他会签也都已通过 (包括已完成的会签，如果 COMPLETED 状态也表示通过)
        List<ContractProcess> allCountersignProcesses = contractProcessRepository.findByContractAndType(contract, ContractProcessType.COUNTERSIGN); //
        boolean allRelevantCountersignsApproved = allCountersignProcesses.stream()
                .allMatch(p -> p.getState() == ContractProcessState.APPROVED || p.getState() == ContractProcessState.COMPLETED); //

        if (allRelevantCountersignsApproved) {
            // 所有会签都通过后，合同状态进入待定稿
            contract.setStatus(ContractStatus.PENDING_FINALIZATION); //
            logDetails += " 所有会签均完成且通过，合同进入待定稿状态。";

            auditLogService.logAction(username, "CONTRACT_ALL_COUNTERSIGNED_TO_FINALIZE", logDetails); // 新增审计日志类型
            User drafter = contract.getDrafter(); // 获取合同的起草人
            if (drafter == null) {
                // 如果起草人为空，这是一个数据不一致问题，需要处理
                logger.error("合同 (ID: {}) 会签完成，但起草人为空，无法创建定稿任务。", contract.getId());
                throw new BusinessLogicException("合同起草人信息缺失，无法推进定稿流程。");
            }

            // 检查是否已经存在未完成的定稿任务，避免重复创建
            Optional<ContractProcess> existingFinalizeTask = contractProcessRepository
                    .findByContractIdAndOperatorUsernameAndTypeAndState(
                            contract.getId(),
                            drafter.getUsername(),
                            ContractProcessType.FINALIZE, // 定稿类型
                            ContractProcessState.PENDING // 待处理状态
                    );

            if (existingFinalizeTask.isEmpty()) {
                ContractProcess finalizeTask = new ContractProcess();
                finalizeTask.setContract(contract);
                finalizeTask.setContractNumber(contract.getContractNumber());
                finalizeTask.setType(ContractProcessType.FINALIZE); // 设置为定稿类型
                finalizeTask.setState(ContractProcessState.PENDING); // 设置为待处理状态
                finalizeTask.setOperator(drafter); // 操作人是起草人
                finalizeTask.setOperatorUsername(drafter.getUsername());
                finalizeTask.setComments("等待起草人对合同内容进行最终定稿。"); // 默认备注
                // createdAt 会自动设置，processedAt 和 completedAt 此时为 null

                contractProcessRepository.save(finalizeTask);
                auditLogService.logAction(drafter.getUsername(), "FINALIZE_TASK_CREATED",
                        "为合同ID " + contract.getId() + " (" + contract.getContractName() + ") 创建了待定稿任务。");
                logger.info("已为合同 {} (ID: {}) 创建定稿任务给起草人 {}。", contract.getContractName(), contract.getId(), drafter.getUsername());
            } else {
                logger.warn("合同 {} (ID: {}) 已存在待处理的定稿任务给起草人 {}，跳过创建。", contract.getContractName(), contract.getId(), drafter.getUsername());
            }
        } else {
            // 如果还有其他会签是 PENDING，则合同状态保持不变
            logDetails += " 当前会签已通过，但尚有其他会签流程未完成。合同状态保持待会签。";
            auditLogService.logAction(username, logActionType, logDetails);
        }
        contractRepository.save(contract);
    }


    @Override
    public Path getAttachmentPath(String filename) {
        return attachmentService.getAttachment(filename); //
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getContractStatusStatistics() {
        List<Object[]> results = contractRepository.findContractCountByStatus(); //
        Map<String, Long> statistics = new HashMap<>();
        // 初始化所有状态的计数为0，确保图表显示完整
        for (ContractStatus status : ContractStatus.values()) { //
            statistics.put(status.name(), 0L);
        }
        for (Object[] result : results) {
            if (result[0] instanceof ContractStatus && result[1] instanceof Long) { //
                ContractStatus status = (ContractStatus) result[0]; //
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
                    ContractStatus contractStatusEnum = ContractStatus.valueOf(status.toUpperCase()); //
                    predicates.add(criteriaBuilder.equal(root.get("status"), contractStatusEnum));
                } catch (IllegalArgumentException e) {
                    logger.warn("搜索合同中提供了无效的状态值: {}。将忽略此状态条件。", status);
                }
            }

            // 非管理员用户只能看到自己起草或参与的合同流程
            if (!isAdmin && StringUtils.hasText(currentUsername)) {
                User currentUser = userRepository.findByUsername(currentUsername)
                        .orElse(null);

                if (currentUser != null) {
                    Predicate isDrafter = criteriaBuilder.equal(root.get("drafter"), currentUser); //

                    // Subquery: user is involved in a contract's process (countersign, approval, signing, finalization)
                    Subquery<Long> subquery = query.subquery(Long.class);
                    Root<ContractProcess> contractProcessRoot = subquery.from(ContractProcess.class); //
                    subquery.select(contractProcessRoot.get("contract").get("id")); //

                    Predicate subqueryPredicate = criteriaBuilder.and(
                            criteriaBuilder.equal(contractProcessRoot.get("contract").get("id"), root.get("id")), //
                            criteriaBuilder.equal(contractProcessRoot.get("operator"), currentUser) //
                    );
                    subquery.where(subqueryPredicate);

                    Predicate isInvolvedInProcess = criteriaBuilder.exists(subquery);

                    predicates.add(criteriaBuilder.or(isDrafter, isInvolvedInProcess));
                } else {
                    logger.warn("Username '{}' provided for user-specific search, but user not found in database. Query will return no user-specific data.", currentUsername);
                    predicates.add(criteriaBuilder.disjunction()); // Equivalent to `false`, ensures no data is returned
                }
            }

            // Ensure lazy-loaded entities are fetched eagerly in the main query to avoid N+1 issues
            // Only fetch when the query result type is Contract.class to avoid affecting count queries
            if (query.getResultType().equals(Contract.class)) { //
                root.fetch("customer", JoinType.LEFT); //
                root.fetch("drafter", JoinType.LEFT); //
            }

            query.distinct(true); // Avoid duplicate rows due to JOIN
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Page<Contract> contractsPage = contractRepository.findAll(spec, pageable); //

        // Explicitly initialize lazy-loaded collections to ensure data is available during DTO conversion or Thymeleaf rendering
        contractsPage.getContent().forEach(contract -> {
            Hibernate.initialize(contract.getCustomer()); //
            User drafter = contract.getDrafter(); //
            if (drafter != null) {
                Hibernate.initialize(drafter.getRoles()); //
                drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities())); //
            }
        });
        return contractsPage;
    }


    @Override
    @Transactional(readOnly = true)
    public Page<ContractProcess> getPendingProcessesForUser(
            String username,
            ContractProcessType type, //
            ContractProcessState state, //
            String contractNameSearch,
            Pageable pageable) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessLogicException("用户不存在: " + username)); //

        Specification<ContractProcess> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("type"), type));
            predicates.add(cb.equal(root.get("state"), state));
            predicates.add(cb.equal(root.get("operator"), currentUser));

            Join<ContractProcess, Contract> contractJoin = root.join("contract", JoinType.INNER); //

            // Ensure the contract's own status is consistent with the process type, enhancing data consistency
            switch (type) {
                case COUNTERSIGN: //
                    predicates.add(cb.equal(contractJoin.get("status"), ContractStatus.PENDING_COUNTERSIGN)); //
                    break;
                case APPROVAL: //
                    predicates.add(cb.equal(contractJoin.get("status"), ContractStatus.PENDING_APPROVAL)); //
                    break;
                case SIGNING: //
                    predicates.add(cb.equal(contractJoin.get("status"), ContractStatus.PENDING_SIGNING)); //
                    break;
                case FINALIZE: // Finalization process, typically completed by the drafter, contract status is PENDING_FINALIZATION
                    predicates.add(cb.equal(contractJoin.get("status"), ContractStatus.PENDING_FINALIZATION)); //
                    break;
                default:
                    // For other types not explicitly handled, do not add additional status restrictions, or throw an exception
                    break;
            }

            if (StringUtils.hasText(contractNameSearch)) {
                predicates.add(cb.like(cb.lower(contractJoin.get("contractName")), "%" + contractNameSearch.toLowerCase().trim() + "%"));
            }

            // Perform Eager Fetch in the main query to avoid N+1 query issues
            if (query.getResultType().equals(ContractProcess.class)) { //
                root.fetch("operator", JoinType.LEFT); // Fetch operator User
                Fetch<ContractProcess, Contract> contractFetch = root.fetch("contract", JoinType.LEFT); // Fetch associated Contract
                contractFetch.fetch("customer", JoinType.LEFT); // Fetch Contract's Customer
                contractFetch.fetch("drafter", JoinType.LEFT); // Fetch Contract's Drafter (User)
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<ContractProcess> resultPage = contractProcessRepository.findAll(spec, pageable); //

        // Explicitly initialize lazy-loaded collections (even though fetch should have handled most)
        resultPage.getContent().forEach(process -> {
            Hibernate.initialize(process.getOperator()); //
            User operator = process.getOperator(); //
            if (operator != null) {
                Hibernate.initialize(operator.getRoles()); //
                operator.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities())); //
            }

            Hibernate.initialize(process.getContract()); //
            if (process.getContract() != null) {
                Hibernate.initialize(process.getContract().getCustomer()); //
                User drafter = process.getContract().getDrafter(); //
                if (drafter != null) {
                    Hibernate.initialize(drafter.getRoles()); //
                    drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities())); //
                }
            }
        });
        return resultPage;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractProcess> getAllPendingTasksForUser(String username) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessLogicException("用户未找到: " + username)); //

        Specification<ContractProcess> spec = (root, query, cb) -> {
            List<Predicate> mainPredicates = new ArrayList<>();
            mainPredicates.add(cb.equal(root.get("operator"), currentUser));
            mainPredicates.add(cb.equal(root.get("state"), ContractProcessState.PENDING)); //

            // Join to Contract entity to filter by contract status
            Join<ContractProcess, Contract> contractJoin = root.join("contract", JoinType.INNER); //

            // Define specific conditions for each pending task type
            Predicate countersignTasks = cb.and(
                    cb.equal(root.get("type"), ContractProcessType.COUNTERSIGN), //
                    cb.equal(contractJoin.get("status"), ContractStatus.PENDING_COUNTERSIGN) //
            );
            Predicate approvalTasks = cb.and(
                    cb.equal(root.get("type"), ContractProcessType.APPROVAL), //
                    cb.equal(contractJoin.get("status"), ContractStatus.PENDING_APPROVAL) //
            );
            Predicate signingTasks = cb.and(
                    cb.equal(root.get("type"), ContractProcessType.SIGNING), //
                    cb.equal(contractJoin.get("status"), ContractStatus.PENDING_SIGNING) //
            );
            // For finalization tasks, also potentially display as pending tasks for the drafter
            Predicate finalizeTasks = cb.and(
                    cb.equal(root.get("type"), ContractProcessType.FINALIZE), //
                    cb.equal(contractJoin.get("status"), ContractStatus.PENDING_FINALIZATION) //
            );

            // Combine these specific task conditions
            mainPredicates.add(cb.or(countersignTasks, approvalTasks, signingTasks, finalizeTasks));

            // Eager fetching for the main query
            if (query.getResultType().equals(ContractProcess.class)) { //
                Fetch<ContractProcess, Contract> contractFetch = root.fetch("contract", JoinType.LEFT); //
                contractFetch.fetch("customer", JoinType.LEFT); //
                contractFetch.fetch("drafter", JoinType.LEFT); //
                root.fetch("operator", JoinType.LEFT); // Operator (current user)
            }
            query.orderBy(cb.desc(root.get("createdAt"))); // Order by process creation time descending

            return cb.and(mainPredicates.toArray(new Predicate[0]));
        };

        List<ContractProcess> tasks = contractProcessRepository.findAll(spec); //
        // Explicitly initialize lazy-loaded associated entities
        tasks.forEach(task -> {
            // Ensure operator (process performer) roles and functionalities are loaded
            User operator = task.getOperator(); //
            if (operator != null) {
                Hibernate.initialize(operator.getRoles()); //
                operator.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities())); //
            }
            // Ensure contract's drafter and drafter's roles and functionalities are loaded
            if (task.getContract() != null && task.getContract().getDrafter() != null) {
                User drafter = task.getContract().getDrafter(); //
                Hibernate.initialize(drafter.getRoles()); //
                drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities())); //
            }
        });
        return tasks;
    }


    @Override
    @Transactional(readOnly = true)
    public Contract getContractById(Long id) {
        Contract contract = contractRepository.findById(id) //
                .orElseThrow(() -> new ResourceNotFoundException("合同不存在，ID: " + id));
        // Explicitly load associated entities to avoid lazy loading exceptions
        Hibernate.initialize(contract.getCustomer()); //
        User drafter = contract.getDrafter(); //
        if (drafter != null) {
            Hibernate.initialize(drafter);
            Hibernate.initialize(drafter.getRoles()); //
            drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities())); //
        }
        return contract;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canUserApproveContract(String username, Long contractId) {
        // Check if there is a pending approval task for the current user
        return contractProcessRepository.findByContractIdAndOperatorUsernameAndTypeAndState(
                        contractId, username, ContractProcessType.APPROVAL, ContractProcessState.PENDING) //
                .isPresent();
    }


    @Override
    @Transactional
    public void processApproval(Long contractId, String username, boolean approved, String comments) {
        // 1. Get contract and perform status check (using eagerly loaded method)
        Contract contract = getContractById(contractId); //

        if (contract.getStatus() != ContractStatus.PENDING_APPROVAL) { //
            throw new BusinessLogicException("合同当前状态为 " + contract.getStatus().getDescription() + "，不能进行审批。必须处于待审批状态。");
        }

        // 2. Find the pending approval task for the specific user on the given contract
        ContractProcess process = contractProcessRepository
                .findByContractIdAndOperatorUsernameAndTypeAndState(
                        contractId, username, ContractProcessType.APPROVAL, ContractProcessState.PENDING) //
                .orElseThrow(() -> new AccessDeniedException("未找到您的待处理审批任务，或您无权操作此合同的审批。"));

        // 3. Update the status of the current approval process
        process.setState(approved ? ContractProcessState.APPROVED : ContractProcessState.REJECTED); //
        process.setComments(comments);
        process.setProcessedAt(LocalDateTime.now());
        process.setCompletedAt(LocalDateTime.now());
        contractProcessRepository.save(process); //

        contract.setUpdatedAt(LocalDateTime.now());

        String logActionType = approved ? "CONTRACT_APPROVED" : "CONTRACT_REJECTED_APPROVAL";
        String logDetails = "用户 " + username + (approved ? " 批准了" : " 拒绝了") + "合同ID " + contractId +
                " (" + contract.getContractName() + ")的审批。审批意见: " +
                (StringUtils.hasText(comments) ? comments : "无");

        // 4. Update the main contract status based on the approval result
        if (approved) {
            // Check if all approval processes for this contract are completed and approved
            List<ContractProcess> allApprovalProcesses = contractProcessRepository.findByContractAndType(contract, ContractProcessType.APPROVAL); //
            boolean allRelevantApprovalsCompletedAndApproved = allApprovalProcesses.stream()
                    .allMatch(p -> p.getState() == ContractProcessState.APPROVED || p.getState() == ContractProcessState.COMPLETED); //

            if (allRelevantApprovalsCompletedAndApproved) {
                contract.setStatus(ContractStatus.PENDING_SIGNING); // // All approvals passed, move to pending signing status
                logDetails += " 所有审批通过，合同进入待签订状态。";
            } else {
                // If any approval was rejected, the contract would have already turned REJECTED, so no need to handle here.
                // If there are still PENDING approvals, the contract status remains unchanged.
                logDetails += " 尚有其他审批流程未完成或未全部通过。合同状态保持待审批。";
            }
        } else { // If the current approval is a rejection
            contract.setStatus(ContractStatus.REJECTED); // // Contract immediately moves to rejected status
            logDetails += " 合同被拒绝，状态更新为已拒绝。";
        }
        contractRepository.save(contract); //
        auditLogService.logAction(username, logActionType, logDetails);
    }


    @Override
    @Transactional(readOnly = true)
    public ContractProcess getContractProcessByIdAndOperator(Long contractProcessId, String username, ContractProcessType expectedType, ContractProcessState expectedState) { //
        ContractProcess process = contractProcessRepository.findById(contractProcessId) //
                .orElseThrow(() -> new ResourceNotFoundException("合同流程记录未找到，ID: " + contractProcessId));

        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessLogicException("当前用户 '" + username + "' 不存在。")); //

        // Permission check: ensure the current user is the designated operator for this process record
        if (!process.getOperator().equals(currentUser)) { //
            throw new AccessDeniedException("您 (" + username + ") 不是此合同流程 (ID: " + contractProcessId +
                    ", 操作员: " + process.getOperator().getUsername() + ") 的指定操作员，无权操作。"); //
        }
        // Business logic check: ensure process type and state match expectations
        if (process.getType() != expectedType) { //
            throw new BusinessLogicException("合同流程类型不匹配。预期类型: " + expectedType.getDescription() +
                    ", 实际类型: " + process.getType().getDescription()); //
        }
        if (process.getState() != expectedState) { //
            throw new BusinessLogicException("合同流程状态不正确。预期状态: " + expectedState.getDescription() +
                    ", 实际状态: " + process.getState().getDescription()); //
        }

        // Explicitly load associated entities to avoid lazy loading exceptions
        Hibernate.initialize(process.getContract()); //
        if (process.getContract() != null) {
            Hibernate.initialize(process.getContract().getCustomer()); //
            User drafter = process.getContract().getDrafter(); //
            if (drafter != null) {
                Hibernate.initialize(drafter);
                Hibernate.initialize(drafter.getRoles()); //
                drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities())); //
            }
        }
        User operator = process.getOperator(); //
        if (operator != null) {
            Hibernate.initialize(operator.getRoles()); //
            operator.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities())); //
        }
        return process;
    }


    @Override
    @Transactional
    public void signContract(Long contractProcessId, String signingOpinion, String username) {
        // 1. Get signing process record and perform permission and status checks
        ContractProcess process = getContractProcessByIdAndOperator(
                contractProcessId, username, ContractProcessType.SIGNING, ContractProcessState.PENDING); //

        Contract contract = process.getContract();
        if (contract.getStatus() != ContractStatus.PENDING_SIGNING) { //
            throw new BusinessLogicException("合同当前状态为 " + contract.getStatus().getDescription() + "，不能进行签订。必须处于待签订状态。");
        }

        // 2. Update the status of the current signing process
        process.setState(ContractProcessState.COMPLETED); // // Signing process completed
        process.setComments(signingOpinion);
        process.setProcessedAt(LocalDateTime.now());
        process.setCompletedAt(LocalDateTime.now());
        contractProcessRepository.save(process); //

        contract.setUpdatedAt(LocalDateTime.now());
        String logDetails = "用户 " + username + " 完成了对合同ID " + contract.getId() +
                " (" + contract.getContractName() + ") 的签订。签订意见: " +
                (StringUtils.hasText(signingOpinion) ? signingOpinion : "无");

        // 3. Check if all signing processes for this contract are completed
        List<ContractProcess> allSigningProcesses = contractProcessRepository.findByContractAndType(contract, ContractProcessType.SIGNING); //
        boolean allRelevantSigningsCompleted = allSigningProcesses.stream()
                .allMatch(p -> p.getState() == ContractProcessState.COMPLETED); //

        if (allRelevantSigningsCompleted) {
            contract.setStatus(ContractStatus.ACTIVE); // // All signing processes completed, contract becomes active
            logDetails += " 所有签订流程完成，合同状态更新为有效。";
            auditLogService.logAction(username, "CONTRACT_ALL_SIGNED_ACTIVE", logDetails);
        } else {
            logDetails += " 尚有其他签订流程未完成。合同状态保持待签订。";
            auditLogService.logAction(username, "CONTRACT_PARTIALLY_SIGNED", logDetails);
        }
        contractRepository.save(contract); //
    }


    @Override
    @Transactional(readOnly = true)
    public Page<Contract> getContractsPendingFinalizationForUser(String username, String contractNameSearch, Pageable pageable) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户 '" + username + "' 不存在。")); //

        Specification<Contract> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // Filter contracts with status PENDING_FINALIZATION
            predicates.add(cb.equal(root.get("status"), ContractStatus.PENDING_FINALIZATION)); //
            // Only the drafter can finalize their own contracts
            predicates.add(cb.equal(root.get("drafter"), currentUser)); //


            if (StringUtils.hasText(contractNameSearch)) {
                predicates.add(cb.like(cb.lower(root.get("contractName")), "%" + contractNameSearch.toLowerCase().trim() + "%"));
            }

            // Perform Eager Fetch in the main query to avoid N+1 query issues
            if (query.getResultType().equals(Contract.class)) { //
                root.fetch("customer", JoinType.LEFT); //
                root.fetch("drafter", JoinType.LEFT); // The drafter is the current user, can also Fetch here
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Contract> contractsPage = contractRepository.findAll(spec, pageable); //
        // Explicitly initialize lazy-loaded collections
        contractsPage.getContent().forEach(contract -> {
            Hibernate.initialize(contract.getCustomer()); //
            User drafter = contract.getDrafter(); // // Should be the current user
            if (drafter != null) {
                Hibernate.initialize(drafter.getRoles()); //
                drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities())); //
            }
        });
        return contractsPage;
    }

    @Override
    @Transactional(readOnly = true)
    public Contract getContractForFinalization(Long contractId, String username) {
        // Use findByIdWithCustomerAndDrafter to ensure customer and drafter are eagerly loaded
        Contract contract = contractRepository.findByIdWithCustomerAndDrafter(contractId) //
                .orElseThrow(() -> new ResourceNotFoundException("合同未找到，ID: " + contractId));

        // Business logic check: contract must be in pending finalization status
        if (contract.getStatus() != ContractStatus.PENDING_FINALIZATION) { //
            throw new BusinessLogicException("合同当前状态为" + contract.getStatus().getDescription() +
                    "，无法进行定稿操作。必须处于待定稿状态。");
        }

        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("当前用户 '" + username + "' 不存在。")); //
        // Permission check: only the contract's drafter can finalize
        if (contract.getDrafter() == null || !contract.getDrafter().equals(currentUser)) { //
            logger.warn("User '{}' attempted to finalize contract ID {}, but the contract was drafted by '{}', or drafter is null. Access denied.",
                    username, contractId, (contract.getDrafter() != null ? contract.getDrafter().getUsername() : "Unknown")); //
            throw new AccessDeniedException("您 ("+username+") 不是此合同的起草人，无权定稿。");
        }

        // Explicitly initialize drafter's roles and functionalities for completeness
        User drafter = contract.getDrafter(); //
        if (drafter != null) {
            Hibernate.initialize(drafter.getRoles()); //
            drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities())); //
        }
        return contract;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractProcess> getContractCountersignOpinions(Long contractId) {
        Contract contract = contractRepository.findById(contractId) //
                .orElseThrow(() -> new ResourceNotFoundException("获取会签意见失败：合同未找到，ID: " + contractId));

        List<ContractProcess> countersignProcesses = contractProcessRepository.findByContractAndType(contract, ContractProcessType.COUNTERSIGN); //

        // Eagerly load operator information, and sort by processedAt (if not processed, display last)
        countersignProcesses.forEach(process -> {
            Hibernate.initialize(process.getOperator()); // Ensure operator information is loaded
            // If operator has other associated objects needing eager loading (e.g., roles), handle here
            // Hibernate.initialize(process.getOperator().getRoles());
        });

        // Sort by processedAt, with nullsLast meaning unprocessed (processedAt is null) come last
        return countersignProcesses.stream()
                .sorted(Comparator.comparing(ContractProcess::getProcessedAt, Comparator.nullsLast(Comparator.naturalOrder()))) //
                .collect(Collectors.toList());
    }


    @Override
    @Transactional(readOnly = true)
    public long countActiveContracts(String username) {
        // You can add filtering by username if `username` needs to see only *their* active contracts.
        // For now, assuming it counts all active contracts globally, or `username` is just for context/auditing.
        // If you need to filter by user's *related* active contracts (e.g., as drafter, or involved in processes),
        // you'd need a more complex JPA Specification similar to `searchContracts`.
        logger.debug("Counting active contracts for user: {}", username);
        return contractRepository.countByStatus(ContractStatus.ACTIVE); //
    }

    @Override
    @Transactional(readOnly = true)
    public long countContractsExpiringSoon(String username, int days) {
        logger.debug("Counting expiring soon contracts for user: {}, within {} days", username, days);
        LocalDate today = LocalDate.now();
        LocalDate futureDate = today.plusDays(days);

        Specification<Contract> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("status"), ContractStatus.ACTIVE)); //
            predicates.add(criteriaBuilder.greaterThan(root.get("endDate"), today)); // Must be after today to be "expiring soon"
            predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("endDate"), futureDate)); //

            // If `username` needs to filter these by contracts they are involved in,
            // you'd add predicates here similar to `searchContracts` for `!isAdmin` case.
            // For now, assuming it counts all active contracts expiring soon globally, or `username` is just for context.

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return contractRepository.count(spec); //
    }

    @Override
    @Transactional(readOnly = true)
    public long countContractsPendingAssignment() {
        return contractRepository.countByStatus(ContractStatus.PENDING_ASSIGNMENT); //
    }
    @Override
    @Transactional(readOnly = true)
    public Page<ContractProcess> getPendingCountersignContracts(String username, String contractNameSearch, Pageable pageable) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户未找到: " + username));

        Specification<ContractProcess> spec = (root, query, cb) -> {
            // Prevent duplicate Join if already fetched, avoid affecting count query
            if (query.getResultType() != Long.class && query.getResultType() != String.class) { // Avoid fetch during count queries
                // Ensure eager loading of Contract and Contract.customer and Contract.drafter
                root.fetch("contract", JoinType.INNER)
                        .fetch("customer", JoinType.LEFT) // Customer info
                        .fetch("drafter", JoinType.LEFT); // Drafter info
            }


            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("operator"), currentUser));
            predicates.add(cb.equal(root.get("type"), ContractProcessType.COUNTERSIGN));
            predicates.add(cb.equal(root.get("state"), ContractProcessState.PENDING));

            if (StringUtils.hasText(contractNameSearch)) {
                // Fuzzy search by Contract's contractName
                predicates.add(cb.like(cb.lower(root.get("contract").get("contractName")),
                        "%" + contractNameSearch.toLowerCase() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        // findAll method supports Specification and Pageable
        return contractProcessRepository.findAll(spec, pageable);
    }


    @Override
    @Transactional(readOnly = true)
    public Optional<ContractProcess> getContractProcessDetails(Long contractId, String username, ContractProcessType type, ContractProcessState state) {
        return contractProcessRepository.findByContractIdAndOperatorUsernameAndTypeAndState(
                contractId, username, type, state
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractProcess> getAllContractProcessesByContractAndType(Contract contract, ContractProcessType type) {
        List<ContractProcess> processes = contractProcessRepository.findByContractAndType(contract, type);
        // To avoid N+1 problem, might need to manually initialize some lazy-loaded associated entities (if accessed on frontend)
        // E.g.: Hibernate.initialize(process.getOperator());
        // Or use FETCH JOIN in the Repository
        // If you already have Fetch Join in ContractProcessRepository, no additional handling needed here
        processes.forEach(process -> Hibernate.initialize(process.getOperator())); // Ensure operator info is loaded
        return processes.stream()
                .sorted(Comparator.comparing(ContractProcess::getCreatedAt)) // Sort by creation time
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canUserCountersignContract(Long contractId, String username) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户未找到: " + username));

        // Check if there is a countersign process for the current user as operator and contract is in pending countersign status
        Optional<ContractProcess> processOpt = contractProcessRepository.findByContractIdAndOperatorUsernameAndTypeAndState(
                contractId, username, ContractProcessType.COUNTERSIGN, ContractProcessState.PENDING
        );
        return processOpt.isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractProcess> getContractProcessHistory(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("合同未找到，ID: " + contractId));

        List<ContractProcess> processes = contractProcessRepository.findByContractOrderByCreatedAtDesc(contract);

        // Initialize lazy-loaded associated entities
        processes.forEach(process -> {
            Hibernate.initialize(process.getOperator());
            if (process.getOperator() != null) {
                Hibernate.initialize(process.getOperator().getRoles());
            }
        });

        return processes;
    }

    @Override
    @Transactional
    public Contract extendContract(Long contractId, LocalDate newEndDate, String reason, String username) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("合同未找到，ID: " + contractId));

        // Business logic validations
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw new BusinessLogicException("只有状态为 '有效' 的合同才能被延期。当前合同状态为: " + contract.getStatus().getDescription());
        }
        if (newEndDate == null) {
            throw new BusinessLogicException("新的到期日期不能为空。");
        }
        if (newEndDate.isBefore(contract.getEndDate())) {
            throw new BusinessLogicException("新的到期日期 (" + newEndDate + ") 必须晚于当前到期日期 (" + contract.getEndDate() + ")。");
        }
        if (!StringUtils.hasText(reason)) {
            throw new BusinessLogicException("延期原因不能为空。");
        }

        // Fetch the user performing the action
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("执行延期操作的用户 '" + username + "' 不存在。"));

        // Update contract end date
        LocalDate oldEndDate = contract.getEndDate();
        contract.setEndDate(newEndDate);
        contract.setUpdatedAt(LocalDateTime.now());
        // Contract status remains ACTIVE (or could change to PENDING_EXTEND if extension needs approval)
        // For this implementation, we assume direct extension without an approval process.

        // Create a ContractProcess record for the extension
        ContractProcess extensionProcess = new ContractProcess();
        extensionProcess.setContract(contract);
        extensionProcess.setContractNumber(contract.getContractNumber());
        extensionProcess.setType(ContractProcessType.EXTENSION); // Using the new EXTENSION type
        extensionProcess.setState(ContractProcessState.COMPLETED); // Assuming direct completion
        extensionProcess.setOperator(currentUser);
        extensionProcess.setOperatorUsername(currentUser.getUsername());
        extensionProcess.setComments("合同到期日期从 " + oldEndDate + " 延期至 " + newEndDate + "。原因: " + reason);
        extensionProcess.setProcessedAt(LocalDateTime.now());
        extensionProcess.setCompletedAt(LocalDateTime.now());
        contractProcessRepository.save(extensionProcess);

        // Save the updated contract
        Contract updatedContract = contractRepository.save(contract);

        // Log the action
        auditLogService.logAction(username, "CONTRACT_EXTENDED",
                "合同 ID: " + contractId + " (" + contract.getContractName() + ") 的到期日期从 " + oldEndDate + " 延期至 " + newEndDate + "。原因: " + reason);

        return updatedContract;
    }
}