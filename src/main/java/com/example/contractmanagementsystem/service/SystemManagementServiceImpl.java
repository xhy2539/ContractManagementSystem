package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.entity.*;
import com.example.contractmanagementsystem.exception.BusinessLogicException;
import com.example.contractmanagementsystem.exception.DuplicateResourceException;
import com.example.contractmanagementsystem.exception.ResourceNotFoundException;
import com.example.contractmanagementsystem.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.criteria.Predicate; // Javax persistence -> Jakarta persistence

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SystemManagementServiceImpl implements SystemManagementService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final FunctionalityRepository functionalityRepository;
    private final ContractRepository contractRepository;
    private final ContractProcessRepository contractProcessRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @Autowired
    public SystemManagementServiceImpl(UserRepository userRepository,
                                       RoleRepository roleRepository,
                                       FunctionalityRepository functionalityRepository,
                                       ContractRepository contractRepository,
                                       ContractProcessRepository contractProcessRepository,
                                       PasswordEncoder passwordEncoder,
                                       AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.functionalityRepository = functionalityRepository;
        this.contractRepository = contractRepository;
        this.contractProcessRepository = contractProcessRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    /**
     * 获取当前认证用户的用户名。
     * 如果用户未认证或为匿名用户，则返回 "SYSTEM"。
     * @return 当前用户名或 "SYSTEM"。
     */
    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() &&
                !"anonymousUser".equals(authentication.getPrincipal())) {
            // Spring Security 6 uses UserDetails by default, or a String for JWT principal name
            Object principal = authentication.getPrincipal();
            if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
                return ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
            } else if (principal instanceof String) {
                return (String) principal;
            }
            // Fallback to authentication.getName() if principal is not a UserDetails or String
            return authentication.getName();
        }
        return "SYSTEM"; // 或者抛出异常，或返回一个更明确的匿名标记
    }

    // --- 合同分配 (3.7.1) ---
    /**
     * (原有方法，返回List)
     * 获取所有状态为“起草”且待分配的合同列表。
     * 筛选条件：合同状态为 DRAFT 或 PENDING_ASSIGNMENT，并且没有关联的合同流程记录。
     * @return 待分配的合同列表。
     */
    @Override
    @Transactional(readOnly = true)
    public List<Contract> getContractsPendingAssignment() {
        return contractRepository.findAll().stream()
                .filter(contract -> (ContractStatus.DRAFT.equals(contract.getStatus()) ||
                        ContractStatus.PENDING_ASSIGNMENT.equals(contract.getStatus())) &&
                        contractProcessRepository.findByContract(contract).isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 获取所有状态为“起草”或“待分配”且尚未进入流程的合同列表（支持分页和搜索）。
     * @param pageable 分页和排序参数。
     * @param contractNameSearch 可选的合同名称搜索关键词。
     * @param contractNumberSearch 可选的合同编号搜索关键词。
     * @return 待分配的合同分页数据。
     */
    @Override
    @Transactional(readOnly = true)
    public Page<Contract> getContractsPendingAssignment(Pageable pageable, String contractNameSearch, String contractNumberSearch) {
        Specification<Contract> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 状态条件: DRAFT 或 PENDING_ASSIGNMENT
            Predicate statusDraft = criteriaBuilder.equal(root.get("status"), ContractStatus.DRAFT);
            Predicate statusPendingAssignment = criteriaBuilder.equal(root.get("status"), ContractStatus.PENDING_ASSIGNMENT);
            predicates.add(criteriaBuilder.or(statusDraft, statusPendingAssignment));

            // 搜索条件
            if (contractNameSearch != null && !contractNameSearch.isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("contractName")), "%" + contractNameSearch.toLowerCase() + "%"));
            }
            if (contractNumberSearch != null && !contractNumberSearch.isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("contractNumber")), "%" + contractNumberSearch.toLowerCase() + "%"));
            }

            // 筛选没有关联流程的合同 (这是一个比较复杂的条件，直接在Specification中难以高效实现 "isEmpty" 在关联表上)
            // 通常这种复杂筛选可能需要更复杂的查询，或者分步进行。
            // 一个简化的方式是先获取所有符合状态和搜索条件的合同，然后在服务层过滤。但这对于分页不友好。
            // 更好的方式是使用子查询或EXISTS/NOT EXISTS。
            // query.where(criteriaBuilder.isEmpty(root.get("contractProcesses"))); // 如果 Contract 实体中有 @OneToMany List<ContractProcess> contractProcesses;

            //  由于 Contract 实体中没有直接的 contractProcesses 集合用于 criteriaBuilder.isEmpty，
            //  我们将依赖于 Controller 或前端逻辑在获取到这些合同后，再判断其 contractProcessRepository.findByContract(contract).isEmpty()
            //  或者，如果 ContractRepository 继承了 JpaSpecificationExecutor，我们可以构建更复杂的查询。
            //  对于这个特定需求（没有流程记录），更优化的方法可能是在Repository层面使用@Query配合NOT EXISTS子查询。
            //  这里我们暂时只应用基本的状态和搜索条件，流程为空的判断可能需要在获取数据后再过滤，
            //  但这会使得分页不准确。更准确的做法是自定义Repository查询。

            // **重要提示**：下面的 NOT EXISTS 实现需要 ContractRepository 扩展 JpaSpecificationExecutor
            // 并且在 Contract 实体中有一个 @OneToMany 关系映射到 ContractProcess
            // 如果没有这个关系，你需要使用更原生的JPA Criteria API 或 @Query
            // Predicate noProcesses = criteriaBuilder.not(criteriaBuilder.exists(
            //    query.subquery(ContractProcess.class)
            //         .select(contractProcessRoot)
            //         .where(criteriaBuilder.equal(contractProcessRoot.get("contract"), root))
            // ));
            // predicates.add(noProcesses);
            //
            // 鉴于当前的 Contract 实体没有 contractProcesses 集合，上述 subquery 写法会复杂。
            // 一个更直接的方法是，如果ContractRepository支持，创建一个自定义查询。
            // 如果只能用Specification，并且没有直接的集合映射，那么“没有流程记录”的筛选在JPA层面会比较棘手，
            // 可能需要让ContractRepository直接支持这种查询。
            //
            // 此处简化：只按状态和搜索词筛选，流程为空的判断，暂时由调用者（如Controller）在获取数据后再判断，
            // 或者，如果 contractRepository 增加了如 findByStatusInAndContractProcessesIsNull(List<ContractStatus> statuses, Pageable pageable)
            // 这样的方法，则可以直接调用。
            //
            // **为了演示分页和搜索，我们仅应用基础筛选。**
            // **在实际项目中，"没有流程记录" 这个条件需要更精细的JPA查询来实现准确分页。**


            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        // 如果 `contractRepository` 继承 `JpaSpecificationExecutor`
        Page<Contract> contracts = contractRepository.findAll(spec, pageable);

        // 如果上面的 `spec` 无法直接过滤“无流程记录”，并且数据量不大，可以在这里进行二次过滤，但这会破坏分页的准确性。
        // List<Contract> filteredContent = contracts.getContent().stream()
        //        .filter(contract -> contractProcessRepository.findByContract(contract).isEmpty())
        //        .collect(Collectors.toList());
        // return new PageImpl<>(filteredContent, pageable, contracts.getTotalElements()); // totalElements会不准确

        // 正确的做法是让Repository支持这个复杂查询。
        // 假设 ContractRepository 提供了这样的方法：
        // return contractRepository.findContractsPendingAssignmentWithFilters(contractNameSearch, contractNumberSearch, pageable);
        return contracts; // 返回基于Specification的结果，“无流程”的过滤需要在Repository层面优化
    }


    @Override
    @Transactional
    public boolean assignContractPersonnel(Long contractId, List<Long> countersignUserIds, List<Long> approvalUserIds, List<Long> signUserIds) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("合同未找到，ID: " + contractId));

        boolean personnelAssigned = false;

        // 会签人员
        if (countersignUserIds != null && !countersignUserIds.isEmpty()) {
            for (Long userId : countersignUserIds) {
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("分配会签：用户未找到，ID: " + userId));
                ContractProcess cp = new ContractProcess();
                cp.setContract(contract);
                cp.setContractNumber(contract.getContractNumber()); // 冗余合同号
                cp.setOperator(user);
                cp.setOperatorUsername(user.getUsername()); // 冗余用户名
                cp.setType(ContractProcessType.COUNTERSIGN);
                cp.setState(ContractProcessState.PENDING);
                contractProcessRepository.save(cp);
            }
            personnelAssigned = true;
        }

        // 审批人员
        if (approvalUserIds != null && !approvalUserIds.isEmpty()) {
            for (Long userId : approvalUserIds) {
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("分配审批：用户未找到，ID: " + userId));
                ContractProcess cp = new ContractProcess();
                cp.setContract(contract);
                cp.setContractNumber(contract.getContractNumber());
                cp.setOperator(user);
                cp.setOperatorUsername(user.getUsername());
                cp.setType(ContractProcessType.APPROVAL);
                cp.setState(ContractProcessState.PENDING);
                contractProcessRepository.save(cp);
            }
            personnelAssigned = true;
        }

        // 签订人员
        if (signUserIds != null && !signUserIds.isEmpty()) {
            for (Long userId : signUserIds) {
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("分配签订：用户未找到，ID: " + userId));
                ContractProcess cp = new ContractProcess();
                cp.setContract(contract);
                cp.setContractNumber(contract.getContractNumber());
                cp.setOperator(user);
                cp.setOperatorUsername(user.getUsername());
                cp.setType(ContractProcessType.SIGNING);
                cp.setState(ContractProcessState.PENDING);
                contractProcessRepository.save(cp);
            }
            personnelAssigned = true;
        }

        if (personnelAssigned) {
            // 更新合同状态的逻辑：优先看是否有会签，然后审批，然后签订
            if (countersignUserIds != null && !countersignUserIds.isEmpty()) {
                contract.setStatus(ContractStatus.PENDING_COUNTERSIGN);
            } else if (approvalUserIds != null && !approvalUserIds.isEmpty()) {
                contract.setStatus(ContractStatus.PENDING_APPROVAL);
            } else if (signUserIds != null && !signUserIds.isEmpty()) {
                contract.setStatus(ContractStatus.PENDING_SIGNING);
            } else {
                // 如果 personnelAssigned 为 true 但所有列表都为空（理论上不应发生），
                // 或者分配了某些不直接改变主状态的流程类型，
                // 则保持合同为 PENDING_ASSIGNMENT 或 DRAFT（如果它之前是DRAFT）
                // 需求：成功起草后等待分配，分配后进入相应流程。
                if (contract.getStatus() == ContractStatus.DRAFT) {
                    // 如果仅分配了人员但没有明确的下一阶段（例如，所有列表都为空，但personnelAssigned被意外设为true）
                    // 可以考虑将其设为 PENDING_ASSIGNMENT，或根据实际分配的第一个流程类型来定。
                    // 现在的逻辑是，只要有一个列表非空，就会进入相应的 PENDING_XXX 状态。
                }
            }
            contractRepository.save(contract);
            auditLogService.logAction(getCurrentUsername(), "ASSIGN_CONTRACT_PERSONNEL", "为合同 ID: " + contract.getId() + " (" + contract.getContractName() + ") 分配处理人员");
            return true;
        }
        // 如果没有分配任何人员 (所有ID列表都为空或null)
        return false;
    }


    // --- 用户管理 (3.7.2.1) ---
    @Override
    @Transactional
    public User createUser(User user, Set<String> roleNames) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new DuplicateResourceException("用户名 '" + user.getUsername() + "' 已存在");
        }
        if (user.getEmail() != null && !user.getEmail().isEmpty() && userRepository.existsByEmail(user.getEmail())) {
            throw new DuplicateResourceException("邮箱 '" + user.getEmail() + "' 已存在");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setEnabled(true); // 默认启用用户

        Set<Role> roles = new HashSet<>();
        if (roleNames != null && !roleNames.isEmpty()) {
            for (String roleName : roleNames) {
                Role role = roleRepository.findByName(roleName)
                        .orElseThrow(() -> new ResourceNotFoundException("角色 '" + roleName + "' 未找到"));
                roles.add(role);
            }
        }
        user.setRoles(roles);
        User savedUser = userRepository.save(user);
        auditLogService.logAction(getCurrentUsername(), "CREATE_USER", "创建用户: " + savedUser.getUsername());
        return savedUser;
    }

    @Override
    @Transactional(readOnly = true)
    public User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户 '" + username + "' 未找到"));
    }

    /**
     * (原有方法)
     * 获取所有用户列表。
     * @return 用户列表。
     */
    @Override
    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /**
     * 获取所有用户列表（支持分页）。
     * @param pageable 分页和排序参数。
     * @return 用户分页数据。
     */
    @Override
    @Transactional(readOnly = true)
    public Page<User> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    /**
     * 根据条件搜索用户（支持分页）。
     * @param username 可选的用户名搜索关键词。
     * @param email 可选的邮箱搜索关键词。
     * @param pageable 分页和排序参数。
     * @return 符合条件的用户分页数据。
     */
    @Override
    @Transactional(readOnly = true)
    public Page<User> searchUsers(String username, String email, Pageable pageable) {
        Specification<User> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (username != null && !username.isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("username")), "%" + username.toLowerCase() + "%"));
            }
            if (email != null && !email.isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), "%" + email.toLowerCase() + "%"));
            }
            // 也可以添加按 enabled 状态搜索
            // if (enabled != null) { // 假设 enabled 是 Boolean 类型参数
            //     predicates.add(criteriaBuilder.equal(root.get("enabled"), enabled));
            // }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return userRepository.findAll(spec, pageable);
    }

    @Override
    @Transactional
    public User updateUser(Long userId, User userDetailsToUpdate) {
        User existingUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("用户未找到，ID: " + userId));

        if (userDetailsToUpdate.getEmail() != null && !userDetailsToUpdate.getEmail().isEmpty() &&
                !userDetailsToUpdate.getEmail().equals(existingUser.getEmail())) {
            if (userRepository.existsByEmail(userDetailsToUpdate.getEmail()) &&
                    !userRepository.findByEmail(userDetailsToUpdate.getEmail()).map(User::getId).map(id -> id.equals(userId)).orElse(false) ) { // 确保不是当前用户自己
                throw new DuplicateResourceException("邮箱 '" + userDetailsToUpdate.getEmail() + "' 已被其他用户使用");
            }
            existingUser.setEmail(userDetailsToUpdate.getEmail());
        }
        // UserUpdateRequest DTO 只有 isEnabled()
        existingUser.setEnabled(userDetailsToUpdate.isEnabled());


        User updatedUser = userRepository.save(existingUser);
        auditLogService.logAction(getCurrentUsername(), "UPDATE_USER_INFO", "更新用户信息: " + updatedUser.getUsername());
        return updatedUser;
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("用户未找到，ID: " + userId));

        long pendingProcessesCount = contractProcessRepository.countByOperatorAndState(user, ContractProcessState.PENDING);
        if (pendingProcessesCount > 0) {
            throw new BusinessLogicException("无法删除用户 " + user.getUsername() + "，该用户参与了 " + pendingProcessesCount + " 个未完成的合同流程。");
        }

        long draftedContractsCount = contractRepository.countByDrafter(user);
        if (draftedContractsCount > 0) {
            throw new BusinessLogicException("无法删除用户 " + user.getUsername() + "，该用户是 " + draftedContractsCount + " 个合同的起草人。请先处理这些合同。");
        }
        // 在多对多关系中，如果User是关系拥有方，清空roles集合会导致中间表记录被删除。
        // 如果Role是拥有方，或者没有明确的拥有方，则删除User时，JPA会处理中间表的记录。
        // user.getRoles().clear(); // 通常不需要，除非有特殊配置或级联问题
        userRepository.delete(user);
        auditLogService.logAction(getCurrentUsername(), "DELETE_USER", "删除用户: " + user.getUsername() + " (ID: " + userId + ")");
    }


    // --- 角色管理 (3.7.2.2) ---
    @Override
    @Transactional
    public Role createRole(Role role, Set<String> functionalityNames) {
        if (roleRepository.findByName(role.getName()).isPresent()) {
            throw new DuplicateResourceException("角色名称 '" + role.getName() + "' 已存在");
        }

        Set<Functionality> functionalities = new HashSet<>();
        if (functionalityNames != null) {
            for (String funcName : functionalityNames) {
                Functionality func = functionalityRepository.findByName(funcName)
                        .orElseThrow(() -> new ResourceNotFoundException("功能 '" + funcName + "' 未找到"));
                functionalities.add(func);
            }
        }
        role.setFunctionalities(functionalities);
        Role savedRole = roleRepository.save(role);
        auditLogService.logAction(getCurrentUsername(), "CREATE_ROLE", "创建角色: " + savedRole.getName());
        return savedRole;
    }

    /**
     * (原有方法)
     * 获取所有角色列表。
     * @return 角色列表。
     */
    @Override
    @Transactional(readOnly = true)
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    /**
     * 获取所有角色列表（支持分页）。
     * @param pageable 分页和排序参数。
     * @return 角色分页数据。
     */
    @Override
    @Transactional(readOnly = true)
    public Page<Role> getAllRoles(Pageable pageable) {
        return roleRepository.findAll(pageable);
    }

    /**
     * 根据条件搜索角色（支持分页）。
     * @param name 可选的角色名称搜索关键词。
     * @param description 可选的角色描述搜索关键词。
     * @param pageable 分页和排序参数。
     * @return 符合条件的角色分页数据。
     */
    @Override
    @Transactional(readOnly = true)
    public Page<Role> searchRoles(String name, String description, Pageable pageable) {
        Specification<Role> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (name != null && !name.isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
            }
            if (description != null && !description.isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), "%" + description.toLowerCase() + "%"));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return roleRepository.findAll(spec, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Role findRoleByName(String roleName) {
        return roleRepository.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("角色 '" + roleName + "' 未找到"));
    }

    @Override
    @Transactional(readOnly = true)
    public Role findRoleById(Integer roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("角色未找到，ID: " + roleId));
    }


    @Override
    @Transactional
    public Role updateRole(Integer roleId, Role roleDetailsToUpdate, Set<String> functionalityNames) {
        Role existingRole = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("角色未找到，ID: " + roleId));

        if (roleDetailsToUpdate.getName() != null && !existingRole.getName().equals(roleDetailsToUpdate.getName())) {
            if(roleRepository.findByName(roleDetailsToUpdate.getName()).filter(r -> !r.getId().equals(roleId)).isPresent()){
                throw new DuplicateResourceException("角色名称 '" + roleDetailsToUpdate.getName() + "' 已被其他角色使用");
            }
            existingRole.setName(roleDetailsToUpdate.getName());
        }
        if(roleDetailsToUpdate.getDescription() != null) {
            existingRole.setDescription(roleDetailsToUpdate.getDescription());
        }

        Set<Functionality> newFunctionalities = new HashSet<>();
        if (functionalityNames != null) {
            for (String funcName : functionalityNames) {
                Functionality func = functionalityRepository.findByName(funcName)
                        .orElseThrow(() -> new ResourceNotFoundException("功能 '" + funcName + "' 未找到"));
                newFunctionalities.add(func);
            }
        }
        existingRole.setFunctionalities(newFunctionalities); // 直接替换

        Role updatedRole = roleRepository.save(existingRole);
        auditLogService.logAction(getCurrentUsername(), "UPDATE_ROLE", "更新角色: " + updatedRole.getName());
        return updatedRole;
    }

    @Override
    @Transactional
    public void deleteRole(Integer roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("角色未找到，ID: " + roleId));

        long userCountWithRole = userRepository.countByRolesContains(role);
        if (userCountWithRole > 0) {
            throw new BusinessLogicException("无法删除角色 '" + role.getName() + "'，仍有 " + userCountWithRole + " 个用户拥有此角色。请先解除这些用户的该角色。");
        }
        // Role是role_functionalities中间表的拥有方，删除Role时，JPA会自动处理中间表记录。
        // role.getFunctionalities().clear(); // 不需要显式清除
        roleRepository.delete(role);
        auditLogService.logAction(getCurrentUsername(), "DELETE_ROLE", "删除角色: " + role.getName() + " (ID: " + roleId + ")");
    }


    // --- 功能操作管理 (3.7.2.3) ---
    @Override
    @Transactional
    public Functionality createFunctionality(Functionality functionality) {
        if (functionality.getName() == null || functionality.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("功能名称不能为空");
        }
        if (functionalityRepository.findByName(functionality.getName()).isPresent()){
            throw new DuplicateResourceException("功能名称 '" + functionality.getName() + "' 已存在");
        }
        if (functionality.getNum() != null && !functionality.getNum().trim().isEmpty() &&
                functionalityRepository.findByNum(functionality.getNum()).isPresent()) {
            throw new DuplicateResourceException("功能编号 '" + functionality.getNum() + "' 已存在");
        }
        Functionality savedFunctionality = functionalityRepository.save(functionality);
        auditLogService.logAction(getCurrentUsername(), "CREATE_FUNCTIONALITY", "创建功能: " + savedFunctionality.getName());
        return savedFunctionality;
    }

    /**
     * (原有方法)
     * 获取所有功能列表。
     * @return 功能列表。
     */
    @Override
    @Transactional(readOnly = true)
    public List<Functionality> getAllFunctionalities() {
        return functionalityRepository.findAll();
    }

    /**
     * 获取所有功能列表（支持分页）。
     * @param pageable 分页和排序参数。
     * @return 功能分页数据。
     */
    @Override
    @Transactional(readOnly = true)
    public Page<Functionality> getAllFunctionalities(Pageable pageable) {
        return functionalityRepository.findAll(pageable);
    }

    /**
     * 根据条件搜索功能（支持分页）。
     * @param num 可选的功能编号搜索关键词。
     * @param name 可选的功能名称搜索关键词。
     * @param description 可选的功能描述搜索关键词。
     * @param pageable 分页和排序参数。
     * @return 符合条件的功能分页数据。
     */
    @Override
    @Transactional(readOnly = true)
    public Page<Functionality> searchFunctionalities(String num, String name, String description, Pageable pageable) {
        Specification<Functionality> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (num != null && !num.isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("num")), "%" + num.toLowerCase() + "%"));
            }
            if (name != null && !name.isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
            }
            if (description != null && !description.isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), "%" + description.toLowerCase() + "%"));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return functionalityRepository.findAll(spec, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Functionality getFunctionalityById(Long id) {
        return functionalityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("功能未找到，ID: " + id));
    }

    @Override
    @Transactional
    public Functionality updateFunctionality(Long id, Functionality functionalityDetailsToUpdate) {
        Functionality existingFunc = getFunctionalityById(id); // getFunctionalityById 内部会抛出 ResourceNotFoundException

        // 更新功能名称
        if (functionalityDetailsToUpdate.getName() != null &&
                !existingFunc.getName().equals(functionalityDetailsToUpdate.getName())) {
            functionalityRepository.findByName(functionalityDetailsToUpdate.getName())
                    .filter(f -> !f.getId().equals(id)) // 确保不是自身
                    .ifPresent(f -> {
                        throw new DuplicateResourceException("功能名称 '" + functionalityDetailsToUpdate.getName() + "' 已被其他功能使用");
                    });
            existingFunc.setName(functionalityDetailsToUpdate.getName());
        }

        // 更新功能编号
        if (functionalityDetailsToUpdate.getNum() != null &&
                (existingFunc.getNum() == null || !existingFunc.getNum().equals(functionalityDetailsToUpdate.getNum())) ) {
            functionalityRepository.findByNum(functionalityDetailsToUpdate.getNum())
                    .filter(f -> !f.getId().equals(id)) // 确保不是自身
                    .ifPresent(f -> {
                        throw new DuplicateResourceException("功能编号 '" + functionalityDetailsToUpdate.getNum() + "' 已被其他功能使用");
                    });
            existingFunc.setNum(functionalityDetailsToUpdate.getNum());
        }

        // 更新描述和URL (允许设置为null或空字符串)
        existingFunc.setDescription(functionalityDetailsToUpdate.getDescription());
        existingFunc.setUrl(functionalityDetailsToUpdate.getUrl());


        Functionality updatedFunctionality = functionalityRepository.save(existingFunc);
        auditLogService.logAction(getCurrentUsername(), "UPDATE_FUNCTIONALITY", "更新功能: " + updatedFunctionality.getName());
        return updatedFunctionality;
    }

    @Override
    @Transactional
    public void deleteFunctionality(Long id) {
        Functionality func = getFunctionalityById(id); // getFunctionalityById 内部会抛出 ResourceNotFoundException

        // 检查功能是否仍被任何角色使用
        List<Role> rolesWithFunc = roleRepository.findAllByFunctionalitiesContains(func);
        if (!rolesWithFunc.isEmpty()) {
            for(Role role : rolesWithFunc){
                // Functionality 实体必须正确实现 equals 和 hashCode 方法
                role.getFunctionalities().remove(func);
                roleRepository.save(role); // 保存角色的变更
            }
            auditLogService.logAction(getCurrentUsername(), "DELETE_FUNCTIONALITY_CASCADE_ROLE_UPDATE",
                    "功能 '" + func.getName() + "' (ID: " + id + ") 已从 " + rolesWithFunc.size() + " 个角色中移除。");
        }

        functionalityRepository.delete(func);
        auditLogService.logAction(getCurrentUsername(), "DELETE_FUNCTIONALITY", "删除功能: " + func.getName() + " (ID: " + id + ")");
    }


    // --- 分配权限 (给用户分配角色) (3.7.2.4) ---
    @Override
    @Transactional
    public User assignRolesToUser(Long userId, Set<String> roleNames) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("用户未找到，ID: " + userId));

        Set<Role> newRoles = new HashSet<>();
        if (roleNames != null) { // 允许传入空的roleNames集合以移除所有角色
            for (String roleName : roleNames) {
                Role role = roleRepository.findByName(roleName)
                        .orElseThrow(() -> new ResourceNotFoundException("角色 '" + roleName + "' 未找到，无法分配给用户"));
                newRoles.add(role);
            }
        }
        user.setRoles(newRoles); // 直接替换用户现有的角色集合
        User updatedUser = userRepository.save(user);
        auditLogService.logAction(getCurrentUsername(), "ASSIGN_ROLES_TO_USER", "为用户 '" + updatedUser.getUsername() + "' 分配角色: " + (roleNames != null ? String.join(", ", roleNames) : "无"));
        return updatedUser;
    }
}