package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.entity.*;
import com.example.contractmanagementsystem.exception.BusinessLogicException;
import com.example.contractmanagementsystem.exception.DuplicateResourceException;
import com.example.contractmanagementsystem.exception.ResourceNotFoundException;
import com.example.contractmanagementsystem.repository.*;
import org.hibernate.Hibernate; // <--- 确保导入 Hibernate
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.criteria.Predicate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
        return "SYSTEM_UNKNOWN"; // Or throw an exception if an authenticated user is strictly required
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Contract> getContractsPendingAssignment(Pageable pageable, String contractNameSearch, String contractNumberSearch) {
        return contractRepository.findContractsForAssignmentWithFilters(
                List.of(ContractStatus.DRAFT),
                contractNameSearch,
                contractNumberSearch,
                pageable
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<Contract> getContractsPendingAssignment() {
        return contractRepository.findAll().stream()
                .filter(contract -> ContractStatus.DRAFT.equals(contract.getStatus()) &&
                        contractProcessRepository.findByContract(contract).isEmpty())
                .collect(Collectors.toList());
    }


    @Override
    @Transactional
    public boolean assignContractPersonnel(Long contractId, List<Long> countersignUserIds, List<Long> approvalUserIds, List<Long> signUserIds) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("合同未找到，ID: " + contractId));

        boolean countersignersProvided = countersignUserIds != null && !countersignUserIds.isEmpty();
        boolean approversProvided = approvalUserIds != null && !approvalUserIds.isEmpty();
        boolean signersProvided = signUserIds != null && !signUserIds.isEmpty();

        if (!countersignersProvided && !approversProvided && !signersProvided) {
            throw new BusinessLogicException("必须至少为合同分配一种类型的处理人员（会签、审批或签订）。");
        }

        boolean personnelAssigned = false;

        if (countersignersProvided) {
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
            personnelAssigned = true;
        }

        if (approversProvided) {
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

        if (signersProvided) {
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
            if (countersignersProvided) {
                contract.setStatus(ContractStatus.PENDING_COUNTERSIGN);
            } else if (approversProvided) {
                contract.setStatus(ContractStatus.PENDING_APPROVAL);
            } else if (signersProvided) {
                contract.setStatus(ContractStatus.PENDING_SIGNING);
            }
            contractRepository.save(contract);
            auditLogService.logAction(getCurrentUsername(), "ASSIGN_CONTRACT_PERSONNEL", "为合同 ID: " + contract.getId() + " (" + contract.getContractName() + ") 分配处理人员");
            return true;
        }
        return false;
    }

    @Override
    @Transactional
    public User createUser(User user, Set<String> roleNames) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new DuplicateResourceException("用户名 '" + user.getUsername() + "' 已存在");
        }

        // 如果采用方案二（邮箱必填），则 UserCreationRequestDTO 层面已经用 @NotBlank 校验，
        // 此处 user.getEmail() 理论上不会是空字符串或仅空格。
        // 如果 email 仍然可能为空字符串（例如，DTO没有@NotBlank），则需要处理：
        /*
        if (user.getEmail() != null && user.getEmail().trim().isEmpty()) {
            user.setEmail(null); // 将空字符串转换为 null，以避免唯一约束问题（如果 email 可选）
        }
        */

        // 确保只有在 email 非 null 且非空时才检查其唯一性
        if (user.getEmail() != null && !user.getEmail().trim().isEmpty() && userRepository.existsByEmail(user.getEmail())) {
            throw new DuplicateResourceException("邮箱 '" + user.getEmail() + "' 已存在");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        // user.setEnabled(true); // User 实体中 enabled 默认为 true

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

        // 初始化懒加载的集合以避免 LazyInitializationException
        if (savedUser.getRoles() != null) {
            savedUser.getRoles().forEach(role -> {
                Hibernate.initialize(role.getFunctionalities()); // 初始化每个角色的功能列表
            });
        }

        auditLogService.logAction(getCurrentUsername(), "CREATE_USER", "创建用户: " + savedUser.getUsername());
        return savedUser;
    }

    @Override
    @Transactional(readOnly = true)
    public User findUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户 '" + username + "' 未找到"));
        if (user != null && user.getRoles() != null) { // 防御性检查并初始化
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
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        Page<User> usersPage = userRepository.findAll(spec, pageable);
        usersPage.getContent().forEach(user -> {
            if (user.getRoles() != null) {
                user.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
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
        // 初始化懒加载的集合
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
        // 初始化懒加载集合 (虽然 Role 本身被返回，但如果 Role 内部的 Functionality 还有懒加载的集合，也需要考虑)
        // 在这个场景下，Functionality 是简单对象，所以 Hibernate.initialize(savedRole.getFunctionalities()); 就足够了。
        // 但为了保持一致性，如果 Functionality 有更深层次的懒加载，需要递归初始化。
        // 此处假设 Functionality 本身没有需要进一步初始化的懒加载集合。
        if (savedRole.getFunctionalities() != null) {
            Hibernate.initialize(savedRole.getFunctionalities());
        }
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
        if (updatedRole.getFunctionalities() != null) {
            Hibernate.initialize(updatedRole.getFunctionalities());
        }
        auditLogService.logAction(getCurrentUsername(), "UPDATE_ROLE", "更新角色: " + updatedRole.getName() + " 并更新功能编号为: " + (functionalityNums != null ? String.join(", ", functionalityNums) : "无"));
        return updatedRole;
    }


    @Override
    @Transactional(readOnly = true)
    public List<Role> getAllRoles() {
        List<Role> roles = roleRepository.findAll();
        roles.forEach(role -> {
            if (role.getFunctionalities() != null) Hibernate.initialize(role.getFunctionalities());
        });
        return roles;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Role> getAllRoles(Pageable pageable) {
        Page<Role> rolesPage = roleRepository.findAll(pageable);
        rolesPage.getContent().forEach(role -> {
            if (role.getFunctionalities() != null) Hibernate.initialize(role.getFunctionalities());
        });
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
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        Page<Role> rolesPage = roleRepository.findAll(spec, pageable);
        rolesPage.getContent().forEach(role -> {
            if (role.getFunctionalities() != null) Hibernate.initialize(role.getFunctionalities());
        });
        return rolesPage;
    }

    @Override
    @Transactional(readOnly = true)
    public Role findRoleByName(String roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("角色 '" + roleName + "' 未找到"));
        if (role != null && role.getFunctionalities() != null) {
            Hibernate.initialize(role.getFunctionalities());
        }
        return role;
    }

    @Override
    @Transactional(readOnly = true)
    public Role findRoleById(Integer roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("角色未找到，ID: " + roleId));
        if (role != null && role.getFunctionalities() != null) {
            Hibernate.initialize(role.getFunctionalities());
        }
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

        // 初始化懒加载的集合
        if (updatedUser != null && updatedUser.getRoles() != null) {
            updatedUser.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
        }
        auditLogService.logAction(getCurrentUsername(), "ASSIGN_ROLES_TO_USER", "为用户 '" + updatedUser.getUsername() + "' 分配角色: " + (roleNames != null ? String.join(", ", roleNames) : "无"));
        return updatedUser;
    }
}