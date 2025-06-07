package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.entity.*;
import com.example.contractmanagementsystem.exception.BusinessLogicException;
import com.example.contractmanagementsystem.exception.DuplicateResourceException;
import com.example.contractmanagementsystem.exception.ResourceNotFoundException;
import com.example.contractmanagementsystem.repository.*;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
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

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() &&
                !"anonymousUser".equals(authentication.getPrincipal())) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
                return ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
            } else if (principal instanceof String) {
                return (String) principal;
            }
            return authentication.getName(); // Fallback
        }
        return "SYSTEM_UNKNOWN";
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Contract> getContractsPendingAssignment(Pageable pageable, String contractNameSearch, String contractNumberSearch) {
        Specification<Contract> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // 筛选状态为 PENDING_ASSIGNMENT 的合同
            predicates.add(cb.equal(root.get("status"), ContractStatus.PENDING_ASSIGNMENT));

            if (StringUtils.hasText(contractNameSearch)) {
                predicates.add(cb.like(cb.lower(root.get("contractName")), "%" + contractNameSearch.toLowerCase().trim() + "%"));
            }
            if (StringUtils.hasText(contractNumberSearch)) {
                predicates.add(cb.like(cb.lower(root.get("contractNumber")), "%" + contractNumberSearch.toLowerCase().trim() + "%"));
            }

            // 确保只在查询实体时进行fetch, 以避免影响count查询
            if (query.getResultType().equals(Contract.class)) {
                root.fetch("customer", JoinType.LEFT);
                root.fetch("drafter", JoinType.LEFT); // 预加载起草人
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<Contract> contractsPage = contractRepository.findAll(spec, pageable);

        // 显式初始化懒加载的集合，确保在序列化为JSON时数据可用
        contractsPage.getContent().forEach(contract -> {
            Hibernate.initialize(contract.getCustomer());
            User drafter = contract.getDrafter();
            if (drafter != null) {
                Hibernate.initialize(drafter); // 虽然fetch了，但显式初始化更保险
                Set<Role> roles = drafter.getRoles();
                Hibernate.initialize(roles); // 初始化 drafter 的角色集合
                if (roles != null) {
                    for (Role role : roles) {
                        Hibernate.initialize(role.getFunctionalities()); // *** 核心修改：初始化每个角色的功能集合 ***
                    }
                }
            }
        });
        return contractsPage;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Contract> getContractsPendingAssignment() {
        Specification<Contract> spec = (root, query, cb) ->
                cb.and(
                        cb.equal(root.get("status"), ContractStatus.PENDING_ASSIGNMENT)
                );
        List<Contract> contracts = contractRepository.findAll(spec);
        contracts.forEach(contract -> {
            Hibernate.initialize(contract.getCustomer());
            User drafter = contract.getDrafter();
            if (drafter != null) {
                Hibernate.initialize(drafter);
                Set<Role> roles = drafter.getRoles();
                Hibernate.initialize(roles);
                if (roles != null) {
                    for (Role role : roles) {
                        Hibernate.initialize(role.getFunctionalities()); // *** 核心修改：初始化每个角色的功能集合 ***
                    }
                }
            }
        });
        return contracts;
    }


    @Override
    @Transactional
    public boolean assignContractPersonnel(Long contractId, List<Long> finalizerUserIds, List<Long> countersignUserIds, List<Long> approvalUserIds, List<Long> signUserIds) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("合同未找到，ID: " + contractId));

        if (contract.getStatus() != ContractStatus.PENDING_ASSIGNMENT) {
            throw new BusinessLogicException("合同 " + contract.getContractNumber() + " 当前状态为 " +
                    contract.getStatus().getDescription() + "，不能进行人员分配。必须处于“待分配”状态。");
        }

        if (finalizerUserIds == null || finalizerUserIds.isEmpty()) {
            throw new BusinessLogicException("必须至少指定一名定稿人员。");
        }
        if (countersignUserIds == null || countersignUserIds.isEmpty()) {
            throw new BusinessLogicException("必须至少指定一名会签人员。");
        }
        if (approvalUserIds == null || approvalUserIds.isEmpty()) {
            throw new BusinessLogicException("必须至少指定一名审批人员。");
        }
        if (signUserIds == null || signUserIds.isEmpty()) {
            throw new BusinessLogicException("必须至少指定一名签订人员。");
        }

        // 创建会签流程
        for (Long userId : countersignUserIds) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("分配会签：用户未找到，ID: " + userId));
            ContractProcess cp = new ContractProcess();
            cp.setContract(contract);
            cp.setContractNumber(contract.getContractNumber());
            cp.setOperator(user);
            cp.setOperatorUsername(user.getUsername());
            cp.setType(ContractProcessType.COUNTERSIGN);
            cp.setState(ContractProcessState.PENDING);
            contractProcessRepository.save(cp);
        }

        // 创建审批流程
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

        // 创建签订流程
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

        // 创建定稿流程
        for (Long userId : finalizerUserIds) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("分配定稿：用户未找到，ID: " + userId));
            ContractProcess cp = new ContractProcess();
            cp.setContract(contract);
            cp.setContractNumber(contract.getContractNumber());
            cp.setOperator(user);
            cp.setOperatorUsername(user.getUsername());
            cp.setType(ContractProcessType.FINALIZE);
            cp.setState(ContractProcessState.PENDING);
            cp.setComments("请对合同内容进行最终定稿。");
            contractProcessRepository.save(cp);
        }


        // 更新合同状态并记录日志
        contract.setStatus(ContractStatus.PENDING_COUNTERSIGN);
        contract.setUpdatedAt(LocalDateTime.now());
        contractRepository.save(contract);

        auditLogService.logAction(getCurrentUsername(), "ASSIGN_CONTRACT_PERSONNEL",
                "为合同 ID: " + contract.getId() + " (" + contract.getContractName() + ") 分配了所有处理人员，合同进入待会签状态。");
        return true;
    }

    // --- 用户管理 ---
    @Override
    @Transactional
    public User createUser(User user, Set<String> roleNames) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new DuplicateResourceException("用户名 '" + user.getUsername() + "' 已存在");
        }
        if (user.getEmail() != null && !user.getEmail().trim().isEmpty() && userRepository.existsByEmail(user.getEmail())) {
            throw new DuplicateResourceException("邮箱 '" + user.getEmail() + "' 已存在");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));

        Set<Role> roles = new HashSet<>();
        if (roleNames != null && !roleNames.isEmpty()) {
            for (String roleName : roleNames) {
                Role role = roleRepository.findByName(roleName)
                        .orElseThrow(() -> new ResourceNotFoundException("角色 '" + roleName + "' 未找到，无法分配给用户"));
                roles.add(role);
            }
        }
        user.setRoles(roles);
        User savedUser = userRepository.save(user);

        // 初始化新创建用户的角色及其功能，以便返回给前端时完整
        if (savedUser.getRoles() != null) {
            savedUser.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
        }
        auditLogService.logAction(getCurrentUsername(), "CREATE_USER", "创建用户: " + savedUser.getUsername());
        return savedUser;
    }

    @Override
    @Transactional(readOnly = true)
    public User findUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户 '" + username + "' 未找到"));
        if (user.getRoles() != null) {
            user.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
        }
        return user;
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        List<User> users = userRepository.findAll();
        users.forEach(user -> {
            if (user.getRoles() != null) {
                user.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
            }
        });
        return users;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<User> getAllUsers(Pageable pageable) {
        Page<User> usersPage = userRepository.findAll(pageable);
        usersPage.getContent().forEach(user -> {
            if (user.getRoles() != null) {
                user.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
            }
        });
        return usersPage;
    }

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
            // 确保在查询用户列表时，也预加载角色和功能，以避免序列化问题
            if (query.getResultType().equals(User.class)) {
                // root.fetch("roles", JoinType.LEFT); // Fetch roles
                // 如果需要更深层次的fetch (roles -> functionalities), 可能需要更复杂的Join或EntityGraph
                // 但对于当前问题，在forEach中显式初始化更直接
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        Page<User> usersPage = userRepository.findAll(spec, pageable);
        usersPage.getContent().forEach(user -> {
            if (user.getRoles() != null) {
                user.getRoles().forEach(role -> {
                    Hibernate.initialize(role.getFunctionalities()); // 确保角色的功能被初始化
                });
            }
        });
        return usersPage;
    }

    @Override
    @Transactional
    public User updateUser(Long userId, User userDetailsToUpdate) {
        User existingUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("用户未找到，ID: " + userId));

        if (userDetailsToUpdate.getEmail() != null && !userDetailsToUpdate.getEmail().isEmpty() &&
                !userDetailsToUpdate.getEmail().equals(existingUser.getEmail())) {
            userRepository.findByEmail(userDetailsToUpdate.getEmail())
                    .filter(userWithEmail -> !userWithEmail.getId().equals(userId))
                    .ifPresent(u -> {
                        throw new DuplicateResourceException("邮箱 '" + userDetailsToUpdate.getEmail() + "' 已被其他用户使用");
                    });
            existingUser.setEmail(userDetailsToUpdate.getEmail());
        }

        if (userDetailsToUpdate.isEnabled() != existingUser.isEnabled()) {
            existingUser.setEnabled(userDetailsToUpdate.isEnabled());
        }
        if (userDetailsToUpdate.getRealName() != null) {
            existingUser.setRealName(userDetailsToUpdate.getRealName());
        }

        User updatedUser = userRepository.save(existingUser);
        if (updatedUser.getRoles() != null) { // 初始化返回的User对象的角色和功能
            updatedUser.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
        }
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
        userRepository.delete(user);
        auditLogService.logAction(getCurrentUsername(), "DELETE_USER", "删除用户: " + user.getUsername() + " (ID: " + userId + ")");
    }

    // --- 角色管理 ---
    @Override
    @Transactional
    public Role createRoleWithFunctionalityNums(Role role, Set<String> functionalityNums) {
        if (roleRepository.findByName(role.getName()).isPresent()) {
            throw new DuplicateResourceException("角色名称 '" + role.getName() + "' 已存在");
        }

        Set<Functionality> functionalities = new HashSet<>();
        if (functionalityNums != null && !functionalityNums.isEmpty()) {
            for (String funcNum : functionalityNums) {
                Functionality func = functionalityRepository.findByNum(funcNum)
                        .orElseThrow(() -> new ResourceNotFoundException("功能编号为 '" + funcNum + "' 的功能未找到，无法添加到角色"));
                functionalities.add(func);
            }
        }
        role.setFunctionalities(functionalities);
        Role savedRole = roleRepository.save(role);
        Hibernate.initialize(savedRole.getFunctionalities()); // 初始化返回的Role对象的功能
        auditLogService.logAction(getCurrentUsername(), "CREATE_ROLE", "创建角色: " + savedRole.getName() + " 并分配功能编号: " + (functionalityNums != null ? String.join(", ", functionalityNums) : "无"));
        return savedRole;
    }

    @Override
    @Transactional
    public Role updateRoleWithFunctionalityNums(Integer roleId, Role roleDetailsToUpdate, Set<String> functionalityNums) {
        Role existingRole = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("角色未找到，ID: " + roleId));

        if (roleDetailsToUpdate.getName() != null && !existingRole.getName().equals(roleDetailsToUpdate.getName())) {
            roleRepository.findByName(roleDetailsToUpdate.getName())
                    .filter(r -> !r.getId().equals(roleId))
                    .ifPresent(r -> {
                        throw new DuplicateResourceException("角色名称 '" + roleDetailsToUpdate.getName() + "' 已被其他角色使用");
                    });
            existingRole.setName(roleDetailsToUpdate.getName());
        }
        if (roleDetailsToUpdate.getDescription() != null) {
            existingRole.setDescription(roleDetailsToUpdate.getDescription());
        }

        Set<Functionality> newFunctionalities = new HashSet<>();
        if (functionalityNums != null && !functionalityNums.isEmpty()) {
            for (String funcNum : functionalityNums) {
                Functionality func = functionalityRepository.findByNum(funcNum)
                        .orElseThrow(() -> new ResourceNotFoundException("功能编号为 '" + funcNum + "' 的功能未找到，无法更新角色权限"));
                newFunctionalities.add(func);
            }
        }
        existingRole.setFunctionalities(newFunctionalities);

        Role updatedRole = roleRepository.save(existingRole);
        Hibernate.initialize(updatedRole.getFunctionalities()); // 初始化返回的Role对象的功能
        auditLogService.logAction(getCurrentUsername(), "UPDATE_ROLE", "更新角色: " + updatedRole.getName() + " 并更新功能编号为: " + (functionalityNums != null ? String.join(", ", functionalityNums) : "无"));
        return updatedRole;
    }


    @Override
    @Transactional(readOnly = true)
    public List<Role> getAllRoles() {
        List<Role> roles = roleRepository.findAll();
        roles.forEach(role -> Hibernate.initialize(role.getFunctionalities()));
        return roles;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Role> getAllRoles(Pageable pageable) {
        Page<Role> rolesPage = roleRepository.findAll(pageable);
        rolesPage.getContent().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
        return rolesPage;
    }

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
            query.distinct(true);
            // 同样，为角色列表查询预加载功能
            if (query.getResultType().equals(Role.class)) {
                // root.fetch("functionalities", JoinType.LEFT); // 如果用fetch
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        Page<Role> rolesPage = roleRepository.findAll(spec, pageable);
        rolesPage.getContent().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
        return rolesPage;
    }

    @Override
    @Transactional(readOnly = true)
    public Role findRoleByName(String roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("角色 '" + roleName + "' 未找到"));
        Hibernate.initialize(role.getFunctionalities());
        return role;
    }

    @Override
    @Transactional(readOnly = true)
    public Role findRoleById(Integer roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("角色未找到，ID: " + roleId));
        Hibernate.initialize(role.getFunctionalities());
        return role;
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
        roleRepository.delete(role);
        auditLogService.logAction(getCurrentUsername(), "DELETE_ROLE", "删除角色: " + role.getName() + " (ID: " + roleId + ")");
    }

    // --- 功能操作管理 ---
    @Override
    @Transactional
    public Functionality createFunctionality(Functionality functionality) {
        if (functionality.getName() == null || functionality.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("功能名称不能为空");
        }
        if (functionality.getNum() == null || functionality.getNum().trim().isEmpty()) {
            throw new IllegalArgumentException("功能编号不能为空");
        }
        functionalityRepository.findByName(functionality.getName()).ifPresent(f -> {
            throw new DuplicateResourceException("功能名称 '" + functionality.getName() + "' 已存在");
        });
        functionalityRepository.findByNum(functionality.getNum()).ifPresent(f -> {
            throw new DuplicateResourceException("功能编号 '" + functionality.getNum() + "' 已存在");
        });
        Functionality savedFunctionality = functionalityRepository.save(functionality);
        auditLogService.logAction(getCurrentUsername(), "CREATE_FUNCTIONALITY", "创建功能: " + savedFunctionality.getName() + " (编号: " + savedFunctionality.getNum() + ")");
        return savedFunctionality;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Functionality> getAllFunctionalities() {
        return functionalityRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Functionality> getAllFunctionalities(Pageable pageable) {
        return functionalityRepository.findAll(pageable);
    }

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
            query.distinct(true);
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
        Functionality existingFunc = getFunctionalityById(id);

        if (functionalityDetailsToUpdate.getNum() != null &&
                !functionalityDetailsToUpdate.getNum().equals(existingFunc.getNum())) {
            functionalityRepository.findByNum(functionalityDetailsToUpdate.getNum())
                    .filter(f -> !f.getId().equals(id))
                    .ifPresent(f -> {
                        throw new DuplicateResourceException("功能编号 '" + functionalityDetailsToUpdate.getNum() + "' 已被其他功能使用");
                    });
            existingFunc.setNum(functionalityDetailsToUpdate.getNum());
        }

        if (functionalityDetailsToUpdate.getName() != null &&
                !functionalityDetailsToUpdate.getName().equals(existingFunc.getName())) {
            functionalityRepository.findByName(functionalityDetailsToUpdate.getName())
                    .filter(f -> !f.getId().equals(id))
                    .ifPresent(f -> {
                        throw new DuplicateResourceException("功能名称 '" + functionalityDetailsToUpdate.getName() + "' 已被其他功能使用");
                    });
            existingFunc.setName(functionalityDetailsToUpdate.getName());
        }

        existingFunc.setDescription(functionalityDetailsToUpdate.getDescription());
        existingFunc.setUrl(functionalityDetailsToUpdate.getUrl());

        Functionality updatedFunctionality = functionalityRepository.save(existingFunc);
        auditLogService.logAction(getCurrentUsername(), "UPDATE_FUNCTIONALITY", "更新功能: " + updatedFunctionality.getName() + " (编号: " + updatedFunctionality.getNum() + ")");
        return updatedFunctionality;
    }

    @Override
    @Transactional
    public void deleteFunctionality(Long id) {
        Functionality func = getFunctionalityById(id);

        List<Role> rolesWithFunc = roleRepository.findAllByFunctionalitiesContains(func);
        if (!rolesWithFunc.isEmpty()) {
            for(Role role : rolesWithFunc){
                role.getFunctionalities().remove(func);
            }
            roleRepository.saveAll(rolesWithFunc);
        }

        functionalityRepository.delete(func);
        auditLogService.logAction(getCurrentUsername(), "DELETE_FUNCTIONALITY", "删除功能: " + func.getName() + " (ID: " + id + ")");
    }

    // --- 分配权限 (给用户分配角色) ---
    @Override
    @Transactional
    public User assignRolesToUser(Long userId, Set<String> roleNames) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("用户未找到，ID: " + userId));

        Set<Role> newRoles = new HashSet<>();
        if (roleNames != null && !roleNames.isEmpty()) {
            for (String roleName : roleNames) {
                Role role = roleRepository.findByName(roleName)
                        .orElseThrow(() -> new ResourceNotFoundException("角色 '" + roleName + "' 未找到，无法分配给用户"));
                newRoles.add(role);
            }
        }
        user.setRoles(newRoles);
        User updatedUser = userRepository.save(user);

        if (updatedUser.getRoles() != null) { // 初始化返回的User对象的角色和功能
            updatedUser.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
        }
        auditLogService.logAction(getCurrentUsername(), "ASSIGN_ROLES_TO_USER", "为用户 '" + updatedUser.getUsername() + "' 分配角色: " + (roleNames != null ? String.join(", ", roleNames) : "无"));
        return updatedUser;
    }
}