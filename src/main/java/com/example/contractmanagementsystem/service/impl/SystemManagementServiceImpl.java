package com.example.contractmanagementsystem.service.impl;

import com.example.contractmanagementsystem.entity.*;
import com.example.contractmanagementsystem.exception.BusinessLogicException;
import com.example.contractmanagementsystem.exception.DuplicateResourceException;
import com.example.contractmanagementsystem.exception.ResourceNotFoundException;
import com.example.contractmanagementsystem.repository.*;
import com.example.contractmanagementsystem.service.AuditLogService;
import com.example.contractmanagementsystem.service.EmailService;
import com.example.contractmanagementsystem.service.SystemManagementService;
import jakarta.persistence.criteria.Fetch; // 新增导入
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import java.util.*;

@Service
public class SystemManagementServiceImpl implements SystemManagementService {

    private static final Logger logger = LoggerFactory.getLogger(SystemManagementServiceImpl.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final FunctionalityRepository functionalityRepository;
    private final ContractRepository contractRepository;
    private final ContractProcessRepository contractProcessRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final EmailService emailService;

    @Autowired
    public SystemManagementServiceImpl(UserRepository userRepository,
                                       RoleRepository roleRepository,
                                       FunctionalityRepository functionalityRepository,
                                       ContractRepository contractRepository,
                                       ContractProcessRepository contractProcessRepository,
                                       PasswordEncoder passwordEncoder,
                                       AuditLogService auditLogService,
                                       EmailService emailService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.functionalityRepository = functionalityRepository;
        this.contractRepository = contractRepository;
        this.contractProcessRepository = contractProcessRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.emailService = emailService;
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
            predicates.add(cb.equal(root.get("status"), ContractStatus.PENDING_ASSIGNMENT));

            if (StringUtils.hasText(contractNameSearch)) {
                predicates.add(cb.like(cb.lower(root.get("contractName")), "%" + contractNameSearch.toLowerCase() + "%"));
            }
            if (StringUtils.hasText(contractNumberSearch)) {
                predicates.add(cb.like(cb.lower(root.get("contractNumber")), "%" + contractNumberSearch.toLowerCase() + "%"));
            }
            // 使用 JOIN FETCH 预先加载 customer, drafter, drafter的roles和functionalities
            if (query.getResultType().equals(Contract.class)) {
                root.fetch("customer", JoinType.LEFT);
                Fetch<Object, Object> drafterFetch = root.fetch("drafter", JoinType.LEFT);
                drafterFetch.fetch("roles", JoinType.LEFT)
                        .fetch("functionalities", JoinType.LEFT);
            }
            query.distinct(true); // 使用多个 fetch 时，防止重复结果至关重要
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        // 移除显式的 Hibernate.initialize 调用，因为 JOIN FETCH 会处理它们
        return contractRepository.findAll(spec, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Contract> getContractsPendingAssignment() {
        Specification<Contract> spec = (root, query, cb) -> {
            // 使用 JOIN FETCH 预先加载 customer, drafter, drafter的roles和functionalities
            if (query.getResultType().equals(Contract.class)) {
                root.fetch("customer", JoinType.LEFT);
                Fetch<Object, Object> drafterFetch = root.fetch("drafter", JoinType.LEFT);
                drafterFetch.fetch("roles", JoinType.LEFT)
                        .fetch("functionalities", JoinType.LEFT);
            }
            query.distinct(true); // 使用多个 fetch 时，防止重复结果至关重要
            return cb.equal(root.get("status"), ContractStatus.PENDING_ASSIGNMENT);
        };
        List<Contract> contracts = contractRepository.findAll(spec);
        // 移除显式的 Hibernate.initialize 调用
        return contracts;
    }

    @Override
    @Transactional
    public boolean assignContractPersonnel(Long contractId, List<Long> finalizerUserIds, List<Long> countersignUserIds, List<Long> approvalUserIds, List<Long> signUserIds) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("分配人员失败：找不到合同，ID: " + contractId));

        if (contract.getStatus() != ContractStatus.PENDING_ASSIGNMENT) {
            throw new BusinessLogicException("分配失败：合同不处于待分配状态。当前状态: " + contract.getStatus().getDescription());
        }

        List<ContractProcess> existingPendingProcesses = contractProcessRepository.findByContractAndState(contract, ContractProcessState.PENDING);
        if (!existingPendingProcesses.isEmpty()) {
            contractProcessRepository.deleteAll(existingPendingProcesses);
            logger.info("已删除合同 ID {} 的 {} 个旧的待处理流程记录。", contractId, existingPendingProcesses.size());
        }

        validateUserIds(finalizerUserIds, "定稿人");
        validateUserIds(countersignUserIds, "会签人");
        validateUserIds(approvalUserIds, "审批人");
        validateUserIds(signUserIds, "签订人");

        countersignUserIds.forEach(userId -> {
            User operator = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("指定的会签人ID不存在: " + userId));
            createAndNotify(contract, operator, ContractProcessType.COUNTERSIGN);
        });

        approvalUserIds.forEach(userId -> {
            User operator = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("指定的审批人ID不存在: " + userId));
            createAndNotify(contract, operator, ContractProcessType.APPROVAL);
        });

        signUserIds.forEach(userId -> {
            User operator = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("指定的签订人ID不存在: " + userId));
            createAndNotify(contract, operator, ContractProcessType.SIGNING);
        });

        finalizerUserIds.forEach(userId -> {
            User operator = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("指定的定稿人ID不存在: " + userId));
            createAndNotify(contract, operator, ContractProcessType.FINALIZE);
        });

        if (countersignUserIds != null && !countersignUserIds.isEmpty()) {
            contract.setStatus(ContractStatus.PENDING_COUNTERSIGN);
        } else if (finalizerUserIds != null && !finalizerUserIds.isEmpty()) {
            contract.setStatus(ContractStatus.PENDING_FINALIZATION);
        } else if (approvalUserIds != null && !approvalUserIds.isEmpty()) {
            contract.setStatus(ContractStatus.PENDING_APPROVAL);
        } else if (signUserIds != null && !signUserIds.isEmpty()) {
            contract.setStatus(ContractStatus.PENDING_SIGNING);
        } else {
            throw new BusinessLogicException("必须至少分配一个处理人员（定稿、会签、审批、或签订）。");
        }

        contractRepository.save(contract);
        auditLogService.logAction(getCurrentUsername(), "CONTRACT_ASSIGNED", "合同 ID " + contractId + " 的处理人员已分配，流程启动。");

        return true;
    }


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

        // 由于 User.roles 是 FetchType.EAGER，通常无需显式初始化
        // 但 Role.functionalities 是 FetchType.LAZY，如果需要立即访问，可能需要初始化
        if (savedUser.getRoles() != null) {
            savedUser.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
        }
        auditLogService.logAction(getCurrentUsername(), "CREATE_USER", "创建用户: " + savedUser.getUsername());
        return savedUser;
    }

    @Override
    @Transactional(readOnly = true)
    public User findUserByUsername(String username) {
        // 使用新的仓库方法预先加载角色和功能
        User user = userRepository.findByUsernameWithRolesAndFunctionalities(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户 '" + username + "' 未找到"));
        return user;
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        // 使用新的仓库方法预先加载角色和功能
        return userRepository.findAllWithRolesAndFunctionalities();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<User> getAllUsers(Pageable pageable) {
        Specification<User> spec = (root, query, cb) -> {
            // 使用 JOIN FETCH 预先加载角色和功能
            if (query.getResultType().equals(User.class)) {
                Fetch<User, Role> rolesFetch = root.fetch("roles", JoinType.LEFT);
                rolesFetch.fetch("functionalities", JoinType.LEFT);
            }
            query.distinct(true); // 重要！
            return cb.conjunction(); // 没有特定的谓词
        };
        return userRepository.findAll(spec, pageable);
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
            // 使用 JOIN FETCH 预先加载角色和功能
            if (query.getResultType().equals(User.class)) {
                Fetch<User, Role> rolesFetch = root.fetch("roles", JoinType.LEFT);
                rolesFetch.fetch("functionalities", JoinType.LEFT);
            }
            query.distinct(true); // 重要！
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
        // 如果 Role.functionalities 是 LAZY，这里仍然需要显式初始化才能在事务外访问
        if (updatedUser.getRoles() != null) {
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
        // 显式初始化 functionalities
        Hibernate.initialize(savedRole.getFunctionalities());
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
        // 显式初始化 functionalities
        Hibernate.initialize(updatedRole.getFunctionalities());
        auditLogService.logAction(getCurrentUsername(), "UPDATE_ROLE", "更新角色: " + updatedRole.getName() + " 并更新功能编号为: " + (functionalityNums != null ? String.join(", ", functionalityNums) : "无"));
        return updatedRole;
    }


    @Override
    @Transactional(readOnly = true)
    public List<Role> getAllRoles() {
        // 使用新的仓库方法预先加载功能
        return roleRepository.findAllWithFunctionalities();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Role> getAllRoles(Pageable pageable) {
        Specification<Role> spec = (root, query, cb) -> {
            // 使用 JOIN FETCH 预先加载功能
            if (query.getResultType().equals(Role.class)) {
                root.fetch("functionalities", JoinType.LEFT);
            }
            query.distinct(true);
            return cb.conjunction();
        };
        return roleRepository.findAll(spec, pageable);
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
            // 使用 JOIN FETCH 预先加载功能
            if (query.getResultType().equals(Role.class)) {
                root.fetch("functionalities", JoinType.LEFT);
            }
            query.distinct(true);
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return roleRepository.findAll(spec, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Role findRoleByName(String roleName) {
        // 使用新的仓库方法预先加载功能
        Role role = roleRepository.findByNameWithFunctionalities(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("角色 '" + roleName + "' 未找到"));
        return role;
    }

    @Override
    @Transactional(readOnly = true)
    public Role findRoleById(Integer roleId) {
        // 使用新的仓库方法预先加载功能
        Role role = roleRepository.findByIdWithFunctionalities(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("角色未找到，ID: " + roleId));
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

        // 由于 User.roles 是 FetchType.EAGER，通常无需显式初始化
        // 但 Role.functionalities 是 FetchType.LAZY，如果需要立即访问，可能需要初始化
        if (updatedUser.getRoles() != null) {
            updatedUser.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
        }
        auditLogService.logAction(getCurrentUsername(), "ASSIGN_ROLES_TO_USER", "为用户 '" + updatedUser.getUsername() + "' 分配角色: " + (roleNames != null ? String.join(", ", roleNames) : "无"));
        return updatedUser;
    }

    private void validateUserIds(List<Long> userIds, String roleName) {
        if (userIds == null) {
            logger.warn("为角色 '{}' 提供的用户ID列表为null，将视为空列表处理。", roleName);
        }
    }

    private ContractProcess createContractProcess(Contract contract, User operator, ContractProcessType type, ContractProcessState state) {
        ContractProcess process = new ContractProcess();
        process.setContract(contract);
        process.setContractNumber(contract.getContractNumber());
        process.setOperator(operator);
        process.setOperatorUsername(operator.getUsername());
        process.setType(type);
        process.setState(state);
        return process;
    }

    private void createAndNotify(Contract contract, User operator, ContractProcessType type) {
        ContractProcess process = createContractProcess(contract, operator, type, ContractProcessState.PENDING);
        contractProcessRepository.save(process);
        sendTaskNotificationEmail(operator, type.getDescription(), contract);
    }

    private void sendTaskNotificationEmail(User operator, String taskType, Contract contract) {
        if (operator != null && StringUtils.hasText(operator.getEmail())) {
            Map<String, Object> context = new HashMap<>();
            context.put("recipientName", operator.getRealName() != null ? operator.getRealName() : operator.getUsername());
            context.put("taskType", taskType);
            context.put("contractName", contract.getContractName());

            String actionUrl = "http://localhost:8080/dashboard";
            context.put("actionUrl", actionUrl);

            emailService.sendHtmlMessage(
                    operator.getEmail(),
                    "【合同管理系统】您有新的待处理任务：" + taskType,
                    "email/task-notification-email",
                    context
            );
        }
    }
}