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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors; // Ensure Collectors is imported

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
        // Generate unique contract number, adjust generation rules as needed
        String contractNumberGen = "CON-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        contract.setContractNumber(contractNumberGen);
        contract.setCustomer(selectedCustomer);
        contract.setStartDate(request.getStartDate());
        contract.setEndDate(request.getEndDate());
        contract.setContent(request.getContractContent()); // Set initial content
        contract.setDrafter(drafter);

        // Process attachment path list
        if (!CollectionUtils.isEmpty(request.getAttachmentServerFileNames())) {
            try {
                // Serialize attachment filenames list to JSON string for storage
                String attachmentsJson = objectMapper.writeValueAsString(request.getAttachmentServerFileNames());
                contract.setAttachmentPath(attachmentsJson);
                logger.info("Contract '{}' drafted with attachment path: {}", contract.getContractName(), attachmentsJson);
            } catch (JsonProcessingException e) {
                logger.error("Error serializing attachment filenames list to JSON (Contract: {}): {}", request.getContractName(), e.getMessage());
                throw new BusinessLogicException("Error processing attachment information: " + e.getMessage());
            }
        } else {
            contract.setAttachmentPath(null); // If no attachments, set to null or empty JSON array "[]"
        }

        // After drafting, contract status changes to pending assignment
        contract.setStatus(ContractStatus.PENDING_ASSIGNMENT);

        Contract savedContract = contractRepository.save(contract);
        String logDetails = "User " + username + " drafted contract: " + savedContract.getContractName() +
                " (ID: " + savedContract.getId() + "), status changed to pending assignment.";
        // Include attachment info in audit log
        if (StringUtils.hasText(savedContract.getAttachmentPath()) && !savedContract.getAttachmentPath().equals("[]") && !savedContract.getAttachmentPath().equals("null")) {
            logDetails += " Attachments: " + savedContract.getAttachmentPath();
        }
        auditLogService.logAction(username, "CONTRACT_DRAFTED_FOR_ASSIGNMENT", logDetails);
        return savedContract;
    }

    @Override
    @Transactional
    public Contract finalizeContract(Long contractId, String finalizationComments, List<String> attachmentServerFileNames, String updatedContent, String username) throws IOException {
        // 1. Get contract and perform permission and status checks
        Contract contract = getContractForFinalization(contractId, username); //
        User finalizer = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Finalizing user '" + username + "' not found."));

        // 2. Handle attachment updates
        String oldAttachmentPath = contract.getAttachmentPath();
        String newAttachmentsJson = null;

        if (attachmentServerFileNames != null) { // If the passed list is not null, it means the frontend explicitly intends to update attachments
            if (!attachmentServerFileNames.isEmpty()) {
                try {
                    newAttachmentsJson = objectMapper.writeValueAsString(attachmentServerFileNames);
                }
                catch (JsonProcessingException e) {
                    logger.error("Error serializing attachment filenames list to JSON (Finalize Contract ID: {}): {}", contractId, e.getMessage());
                    throw new BusinessLogicException("Error processing attachment information: " + e.getMessage());
                }
            } else {
                newAttachmentsJson = "[]"; // Explicitly state no attachments
            }
            contract.setAttachmentPath(newAttachmentsJson);

            // Log attachment changes to audit log
            if (oldAttachmentPath == null && newAttachmentsJson != null && !newAttachmentsJson.equals("[]")) {
                logger.info("Contract {} finalized, new attachments added: {}", contractId, newAttachmentsJson);
                auditLogService.logAction(username, "ATTACHMENT_ADDED_ON_FINALIZE", "Contract ID " + contractId + " finalized, new attachments added: " + newAttachmentsJson);
            } else if (oldAttachmentPath != null && (newAttachmentsJson == null || newAttachmentsJson.equals("[]"))) {
                logger.info("Contract {} finalized, all attachments removed (Original attachments: {})", contractId, oldAttachmentPath);
                auditLogService.logAction(username, "ATTACHMENTS_REMOVED_ON_FINALIZE", "Contract ID " + contractId + " finalized, all attachments removed. Original attachments: " + oldAttachmentPath);
            } else if (oldAttachmentPath != null && newAttachmentsJson != null && !oldAttachmentPath.equals(newAttachmentsJson)) {
                logger.info("Contract {} finalized, attachments updated from '{}' to '{}'", contractId, oldAttachmentPath, newAttachmentsJson);
                auditLogService.logAction(username, "ATTACHMENTS_UPDATED_ON_FINALIZE", "Contract ID " + contractId + " finalized, attachments updated from " + oldAttachmentPath + " to " + newAttachmentsJson);
            }
        } // If attachmentServerFileNames is null, it means the frontend did not provide a new attachment list, so existing attachments are not modified.

        // 3. Handle contract content updates
        // Only update if the provided updatedContent is not empty and different from existing content
        if (updatedContent != null && !updatedContent.equals(contract.getContent())) {
            contract.setContent(updatedContent);
            auditLogService.logAction(username, "CONTRACT_CONTENT_UPDATED_ON_FINALIZE", "Contract ID " + contractId + " content updated during finalization.");
            logger.info("Contract {} finalized, content updated.", contractId);
        }

        // 4. Update contract status to pending approval
        contract.setStatus(ContractStatus.PENDING_APPROVAL); //
        contract.setUpdatedAt(LocalDateTime.now());

        // 5. Record finalization process
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

        // 6. Save contract
        Contract savedContract = contractRepository.save(contract);

        // 7. Record audit log
        String details = "Contract ID " + contractId + " (" + contract.getContractName() + ") finalized by user " + username + ", status changed to pending approval.";
        if (StringUtils.hasText(finalizationComments)) {
            details += " Finalization comments: " + finalizationComments + ".";
        }
        if (StringUtils.hasText(savedContract.getAttachmentPath()) && !savedContract.getAttachmentPath().equals("[]") && !savedContract.getAttachmentPath().equals("null")) {
            details += " Current attachments: " + savedContract.getAttachmentPath() + ".";
        }
        auditLogService.logAction(username, "CONTRACT_FINALIZED", details);

        return savedContract;
    }

    @Override
    @Transactional
    public void processCountersign(Long contractProcessId, String comments, String username, boolean isApproved) {
        // 1. Get countersign process record and perform permission and status checks
        ContractProcess process = getContractProcessByIdAndOperator(contractProcessId, username, ContractProcessType.COUNTERSIGN, ContractProcessState.PENDING); //

        // 2. Update current countersign process status
        process.setState(isApproved ? ContractProcessState.APPROVED : ContractProcessState.REJECTED); // Countersign can be APPROVED or REJECTED
        process.setComments(comments);
        process.setProcessedAt(LocalDateTime.now());
        process.setCompletedAt(LocalDateTime.now());
        contractProcessRepository.save(process);

        Contract contract = process.getContract();
        contract.setUpdatedAt(LocalDateTime.now());

        String logActionType = isApproved ? "CONTRACT_COUNTERSIGN_APPROVED" : "CONTRACT_COUNTERSIGN_REJECTED"; // Detailed audit type
        String logDetails = "User " + username + (isApproved ? " approved" : " rejected") + " countersign for Contract ID " +
                contract.getId() + " (" + contract.getContractName() + "). Comments: " +
                (StringUtils.hasText(comments) ? comments : "None");

        // 3. Update contract status based on countersign result and other countersign statuses
        if (!isApproved) {
            // If any party rejects countersign, the contract is directly marked as rejected
            contract.setStatus(ContractStatus.REJECTED); //
            logDetails += " Contract rejected due to countersign, status changed to Rejected.";
            auditLogService.logAction(username, logActionType, logDetails);
            contractRepository.save(contract);
            return; // Terminate process, no further countersign checks
        }

        // If current countersign is approved, check if all other countersigns are also approved (including COMPLETED if that implies approval)
        List<ContractProcess> allCountersignProcesses = contractProcessRepository.findByContractAndType(contract, ContractProcessType.COUNTERSIGN); //
        boolean allRelevantCountersignsApproved = allCountersignProcesses.stream()
                .allMatch(p -> p.getState() == ContractProcessState.APPROVED || p.getState() == ContractProcessState.COMPLETED); //

        if (allRelevantCountersignsApproved) {
            // Once all countersigns are approved, contract status enters pending finalization
            contract.setStatus(ContractStatus.PENDING_FINALIZATION); //
            logDetails += " All countersigns completed and approved, contract enters pending finalization status.";

            auditLogService.logAction(username, "CONTRACT_ALL_COUNTERSIGNED_TO_FINALIZE", logDetails); // New audit log type
            User drafter = contract.getDrafter(); // Get contract drafter
            if (drafter == null) {
                // If drafter is null, this is a data inconsistency issue, needs handling
                logger.error("Contract (ID: {}) countersign completed, but drafter is null, cannot create finalization task.", contract.getId());
                throw new BusinessLogicException("Contract drafter information missing, cannot proceed with finalization process.");
            }

            // Check if there is already an uncompleted finalization task to avoid creating duplicates
            Optional<ContractProcess> existingFinalizeTask = contractProcessRepository
                    .findByContractIdAndOperatorUsernameAndTypeAndState(
                            contract.getId(),
                            drafter.getUsername(),
                            ContractProcessType.FINALIZE, // Finalization type
                            ContractProcessState.PENDING // Pending status
                    );

            if (existingFinalizeTask.isEmpty()) {
                ContractProcess finalizeTask = new ContractProcess();
                finalizeTask.setContract(contract);
                finalizeTask.setContractNumber(contract.getContractNumber());
                finalizeTask.setType(ContractProcessType.FINALIZE); // Set to finalization type
                finalizeTask.setState(ContractProcessState.PENDING); // Set to pending status
                finalizeTask.setOperator(drafter); // Operator is the drafter
                finalizeTask.setOperatorUsername(drafter.getUsername());
                finalizeTask.setComments("Waiting for drafter to finalize contract content."); // Default comment
                // createdAt will be set automatically, processedAt and completedAt will be null at this point

                contractProcessRepository.save(finalizeTask);
                auditLogService.logAction(drafter.getUsername(), "FINALIZE_TASK_CREATED",
                        "Created pending finalization task for Contract ID " + contract.getId() + " (" + contract.getContractName() + ").");
                logger.info("Finalization task created for contract {} (ID: {}) for drafter {}.", contract.getContractName(), contract.getId(), drafter.getUsername());
            } else {
                logger.warn("Contract {} (ID: {}) already has a pending finalization task for drafter {}, skipping creation.", contract.getContractName(), contract.getId(), drafter.getUsername());
            }
        } else {
            // If there are other pending countersigns, contract status remains unchanged
            logDetails += " Current countersign approved, but other countersign processes are still pending. Contract status remains pending countersign.";
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
        // Initialize counts for all statuses to 0 to ensure complete chart display
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
                    logger.warn("Invalid status value provided in contract search: {}. Ignoring this status condition.", status);
                }
            }

            // Non-admin users can only see contracts they drafted or are involved in a process for
            if (!isAdmin && StringUtils.hasText(currentUsername)) {
                User currentUser = userRepository.findByUsername(currentUsername)
                        .orElse(null);

                if (currentUser != null) {
                    Predicate isDrafter = criteriaBuilder.equal(root.get("drafter"), currentUser); //

                    // Subquery: check if user is involved in any contract process (countersign, approval, signing, finalization)
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

            // Ensure Eager Fetch for main query to avoid N+1 issues
            // Only fetch if query result type is Contract.class, to avoid affecting count queries
            if (query.getResultType().equals(Contract.class)) { //
                root.fetch("customer", JoinType.LEFT); //
                root.fetch("drafter", JoinType.LEFT); //
            }

            query.distinct(true); // Avoid duplicate rows due to JOIN
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Page<Contract> contractsPage = contractRepository.findAll(spec, pageable); //

        // Explicitly initialize lazy-loaded collections to ensure data availability for DTO conversion or Thymeleaf rendering
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
                .orElseThrow(() -> new BusinessLogicException("User not found: " + username)); //

        Specification<ContractProcess> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("type"), type));
            predicates.add(cb.equal(root.get("state"), state));
            predicates.add(cb.equal(root.get("operator"), currentUser));

            Join<ContractProcess, Contract> contractJoin = root.join("contract", JoinType.INNER); //

            // Ensure contract's own status matches process type for data consistency
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
                case FINALIZE: // Finalization process, usually completed by drafter, contract status is PENDING_FINALIZATION
                    predicates.add(cb.equal(contractJoin.get("status"), ContractStatus.PENDING_FINALIZATION)); //
                    break;
                default:
                    // For other unhandled types, no additional status restrictions, or throw exception
                    break;
            }

            if (StringUtils.hasText(contractNameSearch)) {
                predicates.add(cb.like(cb.lower(contractJoin.get("contractName")), "%" + contractNameSearch.toLowerCase().trim() + "%"));
            }

            // Eager Fetch in main query to avoid N+1 query issues
            if (query.getResultType().equals(ContractProcess.class)) { //
                root.fetch("operator", JoinType.LEFT); // Fetch operator User
                Fetch<ContractProcess, Contract> contractFetch = root.fetch("contract", JoinType.LEFT); // Fetch associated Contract
                contractFetch.fetch("customer", JoinType.LEFT); // Fetch Contract's Customer
                contractFetch.fetch("drafter", JoinType.LEFT); // Fetch Contract's Drafter (User)
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<ContractProcess> resultPage = contractProcessRepository.findAll(spec, pageable); //

        // Explicitly initialize lazy-loaded collections (even if fetch should have handled most)
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
                .orElseThrow(() -> new BusinessLogicException("User not found: " + username)); //

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
            // For finalization tasks, might also be displayed as pending tasks to the drafter
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
            // Ensure operator (process executor) roles and functionalities are loaded
            User operator = task.getOperator(); //
            if (operator != null) {
                Hibernate.initialize(operator.getRoles()); //
                operator.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities())); //
            }
            // Ensure contract drafter and drafter's roles and functionalities are loaded
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
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found, ID: " + id));
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
        // 1. Get contract and perform status checks (using eagerly loaded method)
        Contract contract = getContractById(contractId); //

        if (contract.getStatus() != ContractStatus.PENDING_APPROVAL) { //
            throw new BusinessLogicException("Contract current status is " + contract.getStatus().getDescription() + ", cannot be approved. Must be in pending approval status.");
        }

        // 2. Find pending approval task for specific user on specified contract
        ContractProcess process = contractProcessRepository
                .findByContractIdAndOperatorUsernameAndTypeAndState(
                        contractId, username, ContractProcessType.APPROVAL, ContractProcessState.PENDING) //
                .orElseThrow(() -> new AccessDeniedException("Your pending approval task not found, or you do not have permission to approve this contract."));

        // 3. Update current approval process status
        process.setState(approved ? ContractProcessState.APPROVED : ContractProcessState.REJECTED); //
        process.setComments(comments);
        process.setProcessedAt(LocalDateTime.now());
        process.setCompletedAt(LocalDateTime.now());
        contractProcessRepository.save(process); //

        contract.setUpdatedAt(LocalDateTime.now());

        String logActionType = approved ? "CONTRACT_APPROVED" : "CONTRACT_REJECTED_APPROVAL";
        String logDetails = "User " + username + (approved ? " approved" : " rejected") + " approval for Contract ID " + contractId +
                " (" + contract.getContractName() + "). Approval comments: " +
                (StringUtils.hasText(comments) ? comments : "None");

        // 4. Update main contract status based on approval result
        if (approved) {
            // Check if all approval processes for this contract are completed and approved
            List<ContractProcess> allApprovalProcesses = contractProcessRepository.findByContractAndType(contract, ContractProcessType.APPROVAL); //
            boolean allRelevantApprovalsCompletedAndApproved = allApprovalProcesses.stream()
                    .allMatch(p -> p.getState() == ContractProcessState.APPROVED || p.getState() == ContractProcessState.COMPLETED); //

            if (allRelevantApprovalsCompletedAndApproved) {
                contract.setStatus(ContractStatus.PENDING_SIGNING); // // All approvals passed, enters pending signing status
                logDetails += " All approvals passed, contract enters pending signing status.";
            } else {
                // If any approval was rejected, contract already changed to REJECTED, no further action here.
                // If there are still pending approvals, contract status remains unchanged.
                logDetails += " Other approval processes are still pending or not all approved. Contract status remains pending approval.";
            }
        } else { // If current approval is rejected
            contract.setStatus(ContractStatus.REJECTED); // // Contract directly changes to rejected status
            logDetails += " Contract rejected, status updated to Rejected.";
        }
        contractRepository.save(contract); //
        auditLogService.logAction(username, logActionType, logDetails);
    }


    @Override
    @Transactional(readOnly = true)
    public ContractProcess getContractProcessByIdAndOperator(Long contractProcessId, String username, ContractProcessType expectedType, ContractProcessState expectedState) { //
        ContractProcess process = contractProcessRepository.findById(contractProcessId) //
                .orElseThrow(() -> new ResourceNotFoundException("Contract process record not found, ID: " + contractProcessId));

        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessLogicException("Current user '" + username + "' not found.")); //

        // Permission check: ensure current user is the designated operator for this process record
        if (!process.getOperator().equals(currentUser)) { //
            throw new AccessDeniedException("You (" + username + ") are not the designated operator for this contract process (ID: " + contractProcessId +
                    ", Operator: " + process.getOperator().getUsername() + "), no permission to operate."); //
        }
        // Business logic check: ensure process type and status match expectations
        if (process.getType() != expectedType) { //
            throw new BusinessLogicException("Contract process type mismatch. Expected type: " + expectedType.getDescription() +
                    ", Actual type: " + process.getType().getDescription()); //
        }
        if (process.getState() != expectedState) { //
            throw new BusinessLogicException("Contract process status incorrect. Expected status: " + expectedState.getDescription() +
                    ", Actual status: " + process.getState().getDescription()); //
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
            throw new BusinessLogicException("Contract current status is " + contract.getStatus().getDescription() + ", cannot be signed. Must be in pending signing status.");
        }

        // 2. Update current signing process status
        process.setState(ContractProcessState.COMPLETED); // // Signing process completed
        process.setComments(signingOpinion);
        process.setProcessedAt(LocalDateTime.now());
        process.setCompletedAt(LocalDateTime.now());
        contractProcessRepository.save(process); //

        contract.setUpdatedAt(LocalDateTime.now());
        String logDetails = "User " + username + " completed signing for Contract ID " + contract.getId() +
                " (" + contract.getContractName() + "). Signing comments: " +
                (StringUtils.hasText(signingOpinion) ? signingOpinion : "None");

        // 3. Check if all signing processes for this contract are completed
        List<ContractProcess> allSigningProcesses = contractProcessRepository.findByContractAndType(contract, ContractProcessType.SIGNING); //
        boolean allRelevantSigningsCompleted = allSigningProcesses.stream()
                .allMatch(p -> p.getState() == ContractProcessState.COMPLETED); //

        if (allRelevantSigningsCompleted) {
            contract.setStatus(ContractStatus.ACTIVE); // // All signing processes completed, contract becomes active
            logDetails += " All signing processes completed, contract status updated to Active.";
            auditLogService.logAction(username, "CONTRACT_ALL_SIGNED_ACTIVE", logDetails);
        } else {
            logDetails += " Other signing processes are still pending. Contract status remains pending signing.";
            auditLogService.logAction(username, "CONTRACT_PARTIALLY_SIGNED", logDetails);
        }
        contractRepository.save(contract); //
    }


    @Override
    @Transactional(readOnly = true)
    public Page<Contract> getContractsPendingFinalizationForUser(String username, String contractNameSearch, Pageable pageable) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User '" + username + "' not found.")); //

        Specification<Contract> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // Filter contracts with PENDING_FINALIZATION status
            predicates.add(cb.equal(root.get("status"), ContractStatus.PENDING_FINALIZATION)); //
            // Only drafter can finalize their own contracts
            predicates.add(cb.equal(root.get("drafter"), currentUser)); //


            if (StringUtils.hasText(contractNameSearch)) {
                predicates.add(cb.like(cb.lower(root.get("contractName")), "%" + contractNameSearch.toLowerCase().trim() + "%"));
            }

            // Eager Fetch in main query to avoid N+1 query issues
            if (query.getResultType().equals(Contract.class)) { //
                root.fetch("customer", JoinType.LEFT); //
                root.fetch("drafter", JoinType.LEFT); // Drafter is the current user, can also fetch here
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
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found, ID: " + contractId));

        // Business logic check: contract must be in pending finalization status
        if (contract.getStatus() != ContractStatus.PENDING_FINALIZATION) { //
            throw new BusinessLogicException("合同当前状态为" + contract.getStatus().getDescription() +
                    "，无法进行定稿操作。必须处于待定稿状态。");
        }

        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Current user '" + username + "' not found.")); //
        // Permission check: only contract drafter can finalize
        if (contract.getDrafter() == null || !contract.getDrafter().equals(currentUser)) { //
            logger.warn("User '{}' attempted to finalize contract ID {}, but this contract was drafted by '{}', or drafter is null. Access denied.",
                    username, contractId, (contract.getDrafter() != null ? contract.getDrafter().getUsername() : "unknown")); //
            throw new AccessDeniedException("您 ("+username+") 不是此合同的起草人，无权定稿。");
        }

        // Explicitly initialize drafter's roles and functionalities to ensure completeness
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
                .orElseThrow(() -> new ResourceNotFoundException("Failed to get countersign opinions: Contract not found, ID: " + contractId));

        List<ContractProcess> countersignProcesses = contractProcessRepository.findByContractAndType(contract, ContractProcessType.COUNTERSIGN); //

        // Eagerly load operator information, and sort by processedAt (if not processed, show last)
        countersignProcesses.forEach(process -> {
            Hibernate.initialize(process.getOperator()); // Ensure operator info is loaded
            // If operator has other associated objects needing eager loading (e.g., roles), handle here
            // Hibernate.initialize(process.getOperator().getRoles());
        });

        // Sort by processedAt, nullsLast means unprocessed (processedAt is null) come last
        return countersignProcesses.stream()
                .sorted(Comparator.comparing(ContractProcess::getProcessedAt, Comparator.nullsLast(Comparator.naturalOrder()))) //
                .collect(Collectors.toList());
    }

    /**
     * Builds a Predicate for contract access based on user permissions.
     * If the user is an admin, returns an unrestricted Predicate (all contracts).
     * If the user is not an admin, returns a Predicate that filters contracts drafted by or involving the user.
     *
     * @param username Current username
     * @param isAdmin Whether the user is an admin
     * @param root Root object for the Contract entity
     * @param query CriteriaQuery object
     * @param criteriaBuilder CriteriaBuilder object
     * @return Filtering Predicate
     */
    private Predicate buildUserContractAccessPredicate(String username, boolean isAdmin, Root<Contract> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
        if (isAdmin) {
            return criteriaBuilder.conjunction(); // Admin has no restrictions
        }

        User currentUser = userRepository.findByUsername(username)
                .orElse(null);

        if (currentUser == null) {
            logger.warn("Username '{}' provided for user-specific contract statistics, but user not found in database. Statistics will return zero.", username);
            return criteriaBuilder.disjunction(); // If user does not exist, cannot access any contracts
        }

        // Predicate 1: User is the drafter of the contract
        Predicate isDrafter = criteriaBuilder.equal(root.get("drafter"), currentUser);

        // Predicate 2: User is involved in any process for the contract (countersign, approval, signing, finalization)
        Subquery<Long> subquery = query.subquery(Long.class);
        Root<ContractProcess> contractProcessRoot = subquery.from(ContractProcess.class);
        subquery.select(contractProcessRoot.get("contract").get("id"));

        Predicate subqueryPredicate = criteriaBuilder.and(
                criteriaBuilder.equal(contractProcessRoot.get("contract").get("id"), root.get("id")),
                criteriaBuilder.equal(contractProcessRoot.get("operator"), currentUser)
        );
        subquery.where(subqueryPredicate);

        Predicate isInvolvedInProcess = criteriaBuilder.exists(subquery);

        return criteriaBuilder.or(isDrafter, isInvolvedInProcess);
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveContracts(String username, boolean isAdmin) {
        Specification<Contract> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("status"), ContractStatus.ACTIVE));

            // Apply user contract access permission filter
            predicates.add(buildUserContractAccessPredicate(username, isAdmin, root, query, criteriaBuilder));

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return contractRepository.count(spec);
    }

    @Override
    @Transactional(readOnly = true)
    public long countContractsExpiringSoon(int days, String username, boolean isAdmin) {
        LocalDate today = LocalDate.now();
        LocalDate futureDate = today.plusDays(days);

        Specification<Contract> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("status"), ContractStatus.ACTIVE)); // Must be active contract to be expiring soon
            predicates.add(criteriaBuilder.greaterThan(root.get("endDate"), today)); // Must be after today to be "expiring soon"
            predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("endDate"), futureDate)); // Must be within specified days in the future

            // Apply user contract access permission filter
            predicates.add(buildUserContractAccessPredicate(username, isAdmin, root, query, criteriaBuilder));

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return contractRepository.count(spec);
    }

    @Override
    @Transactional(readOnly = true)
    public long countContractsPendingAssignment() {
        return contractRepository.countByStatus(ContractStatus.PENDING_ASSIGNMENT); // This statistic does not need to be filtered by user, as it's an admin backend statistic
    }

    @Override
    @Transactional(readOnly = true)
    public long countExpiredContracts(String username, boolean isAdmin) {
        Specification<Contract> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("status"), ContractStatus.EXPIRED));

            // Apply user contract access permission filter
            predicates.add(buildUserContractAccessPredicate(username, isAdmin, root, query, criteriaBuilder));

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return contractRepository.count(spec);
    }

    @Override
    @Transactional
    public int updateExpiredContractStatuses() {
        LocalDate today = LocalDate.now();

        // Define statuses that should NOT be considered for automatic expiration update,
        // as they are already final or resolved states.
        Set<ContractStatus> excludedStatuses = EnumSet.of(
                ContractStatus.EXPIRED,
                ContractStatus.COMPLETED,
                ContractStatus.TERMINATED,
                ContractStatus.REJECTED
        );

        // Find all contracts whose end date is before or on today,
        // AND whose status is NOT in the excluded list.
        Specification<Contract> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("endDate"), today));
            predicates.add(criteriaBuilder.not(root.get("status").in(excludedStatuses)));
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        List<Contract> contractsToExpire = contractRepository.findAll(spec); // Using findAll with Specification

        int updatedCount = 0;
        for (Contract contract : contractsToExpire) {
            // Only update if the status is not already EXPIRED (double check)
            if (contract.getStatus() != ContractStatus.EXPIRED) {
                contract.setStatus(ContractStatus.EXPIRED);
                contract.setUpdatedAt(LocalDateTime.now()); // Update timestamp
                contractRepository.save(contract);
                updatedCount++;
                // Log this update for auditing purposes
                auditLogService.logAction("SYSTEM", "CONTRACT_AUTO_EXPIRED",
                        "Contract " + contract.getContractNumber() + " (ID: " + contract.getId() +
                                ") automatically updated to EXPIRED status from " + contract.getStatus().name() + " on " + today);
                logger.info("Contract {} (ID: {}) automatically updated to EXPIRED status.", contract.getContractName(), contract.getId());
            }
        }
        return updatedCount;
    }


    @Override
    @Transactional(readOnly = true)
    public Page<ContractProcess> getPendingCountersignContracts(String username, String contractNameSearch, Pageable pageable) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        Specification<ContractProcess> spec = (root, query, cb) -> {
            // Avoid duplicate Joins, if already fetched through Fetch Join, no need to Join again
            if (query.getResultType() != Long.class && query.getResultType() != String.class) { // Avoid fetching during count queries
                // Ensure eager loading of Contract and Contract.customer and Contract.drafter
                root.fetch("contract", JoinType.INNER)
                        .fetch("customer", JoinType.LEFT) // Customer information
                        .fetch("drafter", JoinType.LEFT); // Drafter information
            }


            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("operator"), currentUser));
            predicates.add(cb.equal(root.get("type"), ContractProcessType.COUNTERSIGN));
            predicates.add(cb.equal(root.get("state"), ContractProcessState.PENDING));

            if (StringUtils.hasText(contractNameSearch)) {
                // Fuzzy query by Contract's contractName
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
        // To avoid N+1 issues, may need to manually initialize some lazy-loaded associated entities (if they are accessed on the frontend page)
        // E.g.: Hibernate.initialize(process.getOperator());
        // Or use FETCH JOIN in Repository
        // Add something like @Query("SELECT cp FROM ContractProcess cp JOIN FETCH cp.contract c JOIN FETCH cp.operator o WHERE cp.contract = :contract AND cp.type = :type") in ContractProcessRepository
        // If you already have Fetch Join in ContractProcessRepository, then no additional handling is needed here
        processes.forEach(process -> Hibernate.initialize(process.getOperator())); // Ensure operator information is loaded
        return processes.stream()
                .sorted(Comparator.comparing(ContractProcess::getCreatedAt)) // Sort by creation time
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canUserCountersignContract(Long contractId, String username) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // Check if there is a countersign process for the current user as operator, and the contract is in pending countersign status
        Optional<ContractProcess> processOpt = contractProcessRepository.findByContractIdAndOperatorUsernameAndTypeAndState(
                contractId, username, ContractProcessType.COUNTERSIGN, ContractProcessState.PENDING
        );
        return processOpt.isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractProcess> getContractProcessHistory(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found, ID: " + contractId));

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
    @Transactional(readOnly = true)
    public long countInProcessContracts(String username, boolean isAdmin) {
        Specification<Contract> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 定义“处理中”的合同状态
            List<ContractStatus> inProcessStatuses = Arrays.asList(
                    ContractStatus.PENDING_COUNTERSIGN,
                    ContractStatus.PENDING_APPROVAL,
                    ContractStatus.PENDING_SIGNING,
                    ContractStatus.PENDING_FINALIZATION
            );
            predicates.add(root.get("status").in(inProcessStatuses)); // 过滤出处于“处理中”状态的合同

            // 如果不是管理员，需要根据用户权限进一步过滤
            if (!isAdmin) {
                User currentUser = userRepository.findByUsername(username)
                        .orElseThrow(() -> new UsernameNotFoundException("用户未找到: " + username));

                // 条件1: 用户是合同的起草人
                Predicate isDrafter = criteriaBuilder.equal(root.get("drafter"), currentUser);

                // 条件2: 用户是该合同任一“待处理”流程（会签、审批、签署、定稿）的操作人
                Subquery<Long> subquery = query.subquery(Long.class);
                Root<ContractProcess> contractProcessRoot = subquery.from(ContractProcess.class);
                subquery.select(contractProcessRoot.get("contract").get("id")); // 选择合同ID

                Predicate subqueryPredicate = criteriaBuilder.and(
                        // 确保流程记录关联到当前合同
                        criteriaBuilder.equal(contractProcessRoot.get("contract").get("id"), root.get("id")),
                        // 确保操作人是当前用户
                        criteriaBuilder.equal(contractProcessRoot.get("operator"), currentUser),
                        // 确保流程状态是“待处理”
                        criteriaBuilder.equal(contractProcessRoot.get("state"), ContractProcessState.PENDING),
                        // 确保流程类型是会签、审批、签署或定稿（与合同状态的“处理中”相对应）
                        contractProcessRoot.get("type").in(
                                ContractProcessType.COUNTERSIGN,
                                ContractProcessType.APPROVAL,
                                ContractProcessType.SIGNING,
                                ContractProcessType.FINALIZE
                        )
                );
                subquery.where(subqueryPredicate);

                Predicate isInvolvedInPendingProcess = criteriaBuilder.exists(subquery);

                // 最终条件：要么是起草人，要么参与了某个待处理的流程
                predicates.add(criteriaBuilder.or(isDrafter, isInvolvedInPendingProcess));
            }

            // 对于计数查询，使用distinct以避免因JOIN操作可能导致的重复计数
            query.distinct(true);

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        return contractRepository.count(spec);
    }
}

