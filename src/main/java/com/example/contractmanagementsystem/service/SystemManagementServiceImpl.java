package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.entity.*;
import com.example.contractmanagementsystem.exception.BusinessLogicException; // 导入
import com.example.contractmanagementsystem.exception.DuplicateResourceException;
import com.example.contractmanagementsystem.exception.ResourceNotFoundException;
import com.example.contractmanagementsystem.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() &&
                !"anonymousUser".equals(authentication.getPrincipal())) {
            if (authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.User) {
                return ((org.springframework.security.core.userdetails.User) authentication.getPrincipal()).getUsername();
            } else if (authentication.getPrincipal() instanceof String) {
                return (String) authentication.getPrincipal();
            }
            return authentication.getName();
        }
        return "SYSTEM";
    }

    // --- 分配合同 (3.7.1) ---
    @Override
    @Transactional(readOnly = true)
    public List<Contract> getContractsPendingAssignment() {
        return contractRepository.findAll().stream()
                .filter(contract -> (ContractStatus.DRAFT.equals(contract.getStatus()) ||
                        ContractStatus.PENDING_ASSIGNMENT.equals(contract.getStatus())) &&
                        contractProcessRepository.findByContract(contract).isEmpty())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public boolean assignContractPersonnel(Long contractId, List<Long> countersignUserIds, List<Long> approvalUserIds, List<Long> signUserIds) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("合同未找到，ID: " + contractId));

        boolean personnelAssigned = false;

        if (countersignUserIds != null && !countersignUserIds.isEmpty()) {
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
            if (countersignUserIds != null && !countersignUserIds.isEmpty()) {
                contract.setStatus(ContractStatus.PENDING_COUNTERSIGN);
            } else if (approvalUserIds != null && !approvalUserIds.isEmpty()) {
                contract.setStatus(ContractStatus.PENDING_APPROVAL);
            } else if (signUserIds != null && !signUserIds.isEmpty()) {
                contract.setStatus(ContractStatus.PENDING_SIGNING);
            } else {
                // This case should ideally not happen if personnelAssigned is true
                // but if it does, it implies assignment might be for a type not changing overall status yet
                // or the status logic needs refinement based on which groups are assigned.
                // For now, let's assume if personnelAssigned is true, at least one group had IDs.
                // If no specific primary phase is started by assignment, PENDING_ASSIGNMENT could be a fallback.
                if (contract.getStatus() == ContractStatus.DRAFT) { // Only update from DRAFT if not already in a pending state
                    contract.setStatus(ContractStatus.PENDING_ASSIGNMENT); // Or a more specific initial pending state
                }
            }
            contractRepository.save(contract);
            auditLogService.logAction(getCurrentUsername(), "ASSIGN_CONTRACT_PERSONNEL", "为合同 ID: " + contract.getId() + " (" + contract.getContractName() + ") 分配处理人员");
            return true;
        } else {
            // If no personnel were actually assigned (e.g., all lists were empty or null)
            // Optionally, log this or handle as an invalid operation if assignment implies at least one person.
            return false;
        }
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
        } else {
            // 如果需求是新用户必须有角色，可以在此抛出异常或分配一个默认角色
            // 如果新用户可以没有角色，则此else块可以省略
            // 根据需求文档 “新用户：没有任何权限，等待合同管理员分配权限”
            // 这里我们不自动分配角色，除非明确指定。
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

    @Override
    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    @Transactional
    public User updateUser(Long userId, User userDetailsToUpdate) {
        User existingUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("用户未找到，ID: " + userId));

        // 更新邮箱（如果提供且与现有不同，并检查是否已被其他用户使用）
        if (userDetailsToUpdate.getEmail() != null && !userDetailsToUpdate.getEmail().isEmpty() &&
                !userDetailsToUpdate.getEmail().equals(existingUser.getEmail())) {
            if (userRepository.existsByEmail(userDetailsToUpdate.getEmail())) {
                throw new DuplicateResourceException("邮箱 '" + userDetailsToUpdate.getEmail() + "' 已被其他用户使用");
            }
            existingUser.setEmail(userDetailsToUpdate.getEmail());
        }
        // 更新启用状态
        // userDetailsToUpdate.isEnabled() 在DTO中通常是 isEnabled() 或 getEnabled()
        // User实体中的是 isEnabled()
        existingUser.setEnabled(userDetailsToUpdate.isEnabled());


        // 注意：此方法不更新密码或角色。密码和角色更新应通过专门的方法进行。
        User updatedUser = userRepository.save(existingUser);
        auditLogService.logAction(getCurrentUsername(), "UPDATE_USER_INFO", "更新用户信息: " + updatedUser.getUsername());
        return updatedUser;
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("用户未找到，ID: " + userId));

        // 检查用户是否是任何未完成合同流程的操作员
        long pendingProcessesCount = contractProcessRepository.countByOperatorAndState(user, ContractProcessState.PENDING);
        if (pendingProcessesCount > 0) {
            throw new BusinessLogicException("无法删除用户 " + user.getUsername() + "，该用户参与了 " + pendingProcessesCount + " 个未完成的合同流程。");
        }

        // 检查用户是否是任何合同的起草人
        // 需求文档中没有明确指出起草人不能删除，但这是一个常见的业务约束。
        // 如果允许删除，可能需要将相关合同的起草人置空或重新分配。
        // 这里我们遵循一个更严格的规则，如果用户是起草人，则不允许删除。
        long draftedContractsCount = contractRepository.countByDrafter(user);
        if (draftedContractsCount > 0) {
            throw new BusinessLogicException("无法删除用户 " + user.getUsername() + "，该用户是 " + draftedContractsCount + " 个合同的起草人。请先处理这些合同。");
        }

        // 在删除用户之前，需要解除其与角色的关联 (多对多关系)
        // JPA 通常会自动处理中间表 user_roles 中的记录，当 User 是拥有方或者关系配置为 CascadeType.REMOVE (不推荐用于多对多)
        // 最安全的方式是显式清除关联，或者确保 User 实体中 @ManyToMany 注解正确配置，并且没有阻止删除的级联设置。
        // 对于 @ManyToMany, 通常不需要显式清除，JPA会处理。
        // user.getRoles().clear(); // 如果User是关系拥有方，这会清空关联。
        // userRepository.save(user); // 保存变更

        userRepository.delete(user); // 这将删除用户，并由于 User.roles 的配置，会删除 user_roles 表中的关联记录。
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

    @Override
    @Transactional(readOnly = true)
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Role findRoleByName(String roleName) {
        return roleRepository.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("角色 '" + roleName + "' 未找到"));
    }

    @Override
    @Transactional(readOnly = true)
    public Role findRoleById(Integer roleId) { // 新增方法的实现
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("角色未找到，ID: " + roleId));
    }


    @Override
    @Transactional
    public Role updateRole(Integer roleId, Role roleDetailsToUpdate, Set<String> functionalityNames) {
        Role existingRole = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("角色未找到，ID: " + roleId));

        // 更新角色名（如果提供且与现有不同，并检查是否已被其他角色使用）
        if (roleDetailsToUpdate.getName() != null && !existingRole.getName().equals(roleDetailsToUpdate.getName())) {
            if(roleRepository.findByName(roleDetailsToUpdate.getName()).filter(r -> !r.getId().equals(roleId)).isPresent()){
                throw new DuplicateResourceException("角色名称 '" + roleDetailsToUpdate.getName() + "' 已被其他角色使用");
            }
            existingRole.setName(roleDetailsToUpdate.getName());
        }
        // 更新描述
        if(roleDetailsToUpdate.getDescription() != null) {
            existingRole.setDescription(roleDetailsToUpdate.getDescription());
        }

        // 更新关联的功能
        Set<Functionality> functionalities = new HashSet<>();
        if (functionalityNames != null) {
            for (String funcName : functionalityNames) {
                Functionality func = functionalityRepository.findByName(funcName)
                        .orElseThrow(() -> new ResourceNotFoundException("功能 '" + funcName + "' 未找到"));
                functionalities.add(func);
            }
        }
        existingRole.setFunctionalities(functionalities); // 直接替换现有的功能集合

        Role updatedRole = roleRepository.save(existingRole);
        auditLogService.logAction(getCurrentUsername(), "UPDATE_ROLE", "更新角色: " + updatedRole.getName());
        return updatedRole;
    }

    @Override
    @Transactional
    public void deleteRole(Integer roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("角色未找到，ID: " + roleId));

        // 检查角色是否仍被用户使用
        long userCountWithRole = userRepository.countByRolesContains(role);
        if (userCountWithRole > 0) {
            throw new BusinessLogicException("无法删除角色 '" + role.getName() + "'，仍有 " + userCountWithRole + " 个用户拥有此角色。请先解除这些用户的该角色。");
        }

        // 如果没有用户拥有此角色，可以直接删除。
        // JPA 会自动处理关联表 role_functionalities 中的记录，因为 Role 是拥有方。
        // 对于 @ManyToMany, Role 是拥有方 (因为它有 @JoinTable)。
        // Role.functionalities 的移除由 JPA 在删除 Role 时处理。
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

    @Override
    @Transactional(readOnly = true)
    public List<Functionality> getAllFunctionalities() {
        return functionalityRepository.findAll();
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

        // 更新功能名称（如果提供且与现有不同，并检查是否已被其他功能使用）
        if (functionalityDetailsToUpdate.getName() != null &&
                !existingFunc.getName().equals(functionalityDetailsToUpdate.getName())) {
            Optional<Functionality> byName = functionalityRepository.findByName(functionalityDetailsToUpdate.getName());
            if (byName.isPresent() && !byName.get().getId().equals(id)) { // 确保不是自身
                throw new DuplicateResourceException("功能名称 '" + functionalityDetailsToUpdate.getName() + "' 已被其他功能使用");
            }
            existingFunc.setName(functionalityDetailsToUpdate.getName());
        }

        // 更新功能编号（如果提供且与现有不同，并检查是否已被其他功能使用）
        if (functionalityDetailsToUpdate.getNum() != null &&
                !(existingFunc.getNum() != null && existingFunc.getNum().equals(functionalityDetailsToUpdate.getNum())) ) { // 确保编号有变化
            Optional<Functionality> byNum = functionalityRepository.findByNum(functionalityDetailsToUpdate.getNum());
            if (byNum.isPresent() && !byNum.get().getId().equals(id)) { // 确保不是自身
                throw new DuplicateResourceException("功能编号 '" + functionalityDetailsToUpdate.getNum() + "' 已被其他功能使用");
            }
            existingFunc.setNum(functionalityDetailsToUpdate.getNum());
        }

        // 更新描述和URL
        if(functionalityDetailsToUpdate.getDescription() != null) {
            existingFunc.setDescription(functionalityDetailsToUpdate.getDescription());
        }
        if(functionalityDetailsToUpdate.getUrl() != null) {
            existingFunc.setUrl(functionalityDetailsToUpdate.getUrl());
        }

        Functionality updatedFunctionality = functionalityRepository.save(existingFunc);
        auditLogService.logAction(getCurrentUsername(), "UPDATE_FUNCTIONALITY", "更新功能: " + updatedFunctionality.getName());
        return updatedFunctionality;
    }

    @Override
    @Transactional
    public void deleteFunctionality(Long id) {
        Functionality func = getFunctionalityById(id);

        // 检查功能是否仍被任何角色使用
        List<Role> rolesWithFunc = roleRepository.findAllByFunctionalitiesContains(func);
        if (!rolesWithFunc.isEmpty()) {
            // 业务决定：是抛出异常，还是自动解除关联
            // 这里选择自动解除关联并记录
            // 注意：Functionality实体必须正确实现equals和hashCode方法，以便Set的remove操作能正常工作
            for(Role role : rolesWithFunc){
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
        if (roleNames != null) {
            for (String roleName : roleNames) {
                Role role = roleRepository.findByName(roleName)
                        .orElseThrow(() -> new ResourceNotFoundException("角色 '" + roleName + "' 未找到，无法分配给用户"));
                newRoles.add(role);
            }
        }
        user.setRoles(newRoles); // 直接替换用户现有的角色集合
        User updatedUser = userRepository.save(user);
        auditLogService.logAction(getCurrentUsername(), "ASSIGN_ROLES_TO_USER", "为用户 '" + updatedUser.getUsername() + "' 分配角色: " + roleNames);
        return updatedUser;
    }
}