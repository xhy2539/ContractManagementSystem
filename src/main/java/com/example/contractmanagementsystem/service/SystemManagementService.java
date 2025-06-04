package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.entity.Functionality;
import com.example.contractmanagementsystem.entity.Role;
import com.example.contractmanagementsystem.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Set;

/**
 * 系统管理服务接口，定义了系统管理模块的核心业务逻辑，
 * 包括合同分配、用户管理、角色管理、功能管理以及权限分配等。
 */
public interface SystemManagementService {

    // --- 合同分配 (3.7.1) ---
    /**
     * 获取所有状态为“起草”或“待分配”且尚未进入流程的合同列表（支持分页和搜索）。
     * @param pageable 分页和排序参数。
     * @param contractNameSearch 可选的合同名称搜索关键词。
     * @param contractNumberSearch 可选的合同编号搜索关键词。
     * @return 待分配的合同分页数据。
     */
    Page<Contract> getContractsPendingAssignment(Pageable pageable, String contractNameSearch, String contractNumberSearch);

    /**
     * (原有方法，可以保留或标记为废弃，如果前端不再直接使用)
     * 获取所有状态为“起草”且待分配的合同列表。
     * @return 待分配的合同列表。
     */
    List<Contract> getContractsPendingAssignment();


    /**
     * 为合同分配处理人员 (会签、审批、签订、定稿)。
     * @param contractId 合同ID。
     * @param countersignUserIds 会签人员ID列表。
     * @param approvalUserIds 审批人员ID列表。
     * @param signUserIds 签订人员ID列表。
     * @param finalizeUserIds 定稿人员ID列表。 // 新增参数
     * @return boolean 分配是否成功。
     */
    boolean assignContractPersonnel(Long contractId, List<Long> countersignUserIds, List<Long> approvalUserIds, List<Long> signUserIds, List<Long> finalizeUserIds); // 修改方法签名

    // --- 用户管理 (3.7.2.1) ---
    /**
     * 新增用户。
     * @param user 用户实体 (密码应为原始密码，服务层进行加密处理)。
     * @param roleNames 用户初始角色名称列表。
     * @return 创建后的用户实体。
     */
    User createUser(User user, Set<String> roleNames);

    /**
     * 根据用户名查找用户。
     * @param username 用户名。
     * @return 用户实体。
     * @throws com.example.contractmanagementsystem.exception.ResourceNotFoundException 如果用户未找到。
     */
    User findUserByUsername(String username);

    /**
     * (原有方法，可以保留或标记为废弃)
     * 获取所有用户列表。
     * @return 用户列表。
     */
    List<User> getAllUsers();

    /**
     * 获取所有用户列表（支持分页）。
     * @param pageable 分页和排序参数。
     * @return 用户分页数据。
     */
    Page<User> getAllUsers(Pageable pageable);

    /**
     * 根据条件搜索用户（支持分页）。
     * @param username 可选的用户名搜索关键词。
     * @param email 可选的邮箱搜索关键词。
     * @param pageable 分页和排序参数。
     * @return 符合条件的用户分页数据。
     */
    Page<User> searchUsers(String username, String email, Pageable pageable);

    /**
     * 更新用户信息 (不包括密码和角色)。
     * @param userId 用户ID。
     * @param userDetailsToUpdate 包含更新信息的用户实体。
     * @return 更新后的用户实体。
     */
    User updateUser(Long userId, User userDetailsToUpdate);

    /**
     * 删除用户。
     * @param userId 用户ID。
     * @throws com.example.contractmanagementsystem.exception.ResourceNotFoundException 如果用户未找到。
     * @throws com.example.contractmanagementsystem.exception.BusinessLogicException 如果用户因业务规则不能被删除。
     */
    void deleteUser(Long userId);

    // --- 角色管理 (3.7.2.2) ---
    /**
     * 新增角色，并根据功能编号列表分配功能。
     * @param role 角色实体。
     * @param functionalityNums 该角色拥有的功能编号列表。
     * @return 创建后的角色实体。
     */
    Role createRoleWithFunctionalityNums(Role role, Set<String> functionalityNums);

    /**
     * 更新角色信息，并根据功能编号列表更新关联的功能。
     * @param roleId 角色ID。
     * @param roleDetailsToUpdate 包含更新信息的角色实体。
     * @param functionalityNums 更新后的功能编号列表。
     * @return 更新后的角色实体。
     */
    Role updateRoleWithFunctionalityNums(Integer roleId, Role roleDetailsToUpdate, Set<String> functionalityNums);

    /**
     * (原有方法，可以保留或标记为废弃)
     * 获取所有角色列表。
     * @return 角色列表。
     */
    List<Role> getAllRoles();

    /**
     * 获取所有角色列表（支持分页）。
     * @param pageable 分页和排序参数。
     * @return 角色分页数据。
     */
    Page<Role> getAllRoles(Pageable pageable);

    /**
     * 根据条件搜索角色（支持分页）。
     * @param name 可选的角色名称搜索关键词。
     * @param description 可选的角色描述搜索关键词。
     * @param pageable 分页和排序参数。
     * @return 符合条件的角色分页数据。
     */
    Page<Role> searchRoles(String name, String description, Pageable pageable);

    /**
     * 根据角色名查找角色。
     * @param roleName 角色名。
     * @return 角色实体。
     * @throws com.example.contractmanagementsystem.exception.ResourceNotFoundException 如果角色未找到。
     */
    Role findRoleByName(String roleName);

    /**
     * 根据角色ID查找角色。
     * @param roleId 角色ID。
     * @return 角色实体。
     * @throws com.example.contractmanagementsystem.exception.ResourceNotFoundException 如果角色未找到。
     */
    Role findRoleById(Integer roleId);

    /**
     * 删除角色。
     * @param roleId 角色ID。
     * @throws com.example.contractmanagementsystem.exception.ResourceNotFoundException 如果角色未找到。
     * @throws com.example.contractmanagementsystem.exception.BusinessLogicException 如果角色因业务规则不能被删除。
     */
    void deleteRole(Integer roleId);


    // --- 功能操作管理 (3.7.2.3) ---
    /**
     * 新增功能。
     * @param functionality 功能实体。
     * @return 创建后的功能实体。
     */
    Functionality createFunctionality(Functionality functionality);

    /**
     * (原有方法，可以保留或标记为废弃)
     * 获取所有功能列表。
     * @return 功能列表。
     */
    List<Functionality> getAllFunctionalities();

    /**
     * 获取所有功能列表（支持分页）。
     * @param pageable 分页和排序参数。
     * @return 功能分页数据。
     */
    Page<Functionality> getAllFunctionalities(Pageable pageable);

    /**
     * 根据条件搜索功能（支持分页）。
     * @param num 可选的功能编号搜索关键词。
     * @param name 可选的功能名称搜索关键词。
     * @param description 可选的功能描述搜索关键词。
     * @param pageable 分页和排序参数。
     * @return 符合条件的功能分页数据。
     */
    Page<Functionality> searchFunctionalities(String num, String name, String description, Pageable pageable);

    /**
     * 根据功能ID获取功能。
     * @param id 功能ID。
     * @return 功能实体。
     * @throws com.example.contractmanagementsystem.exception.ResourceNotFoundException 如果功能未找到。
     */
    Functionality getFunctionalityById(Long id);

    /**
     * 更新功能。
     * @param id 功能ID。
     * @param functionalityDetailsToUpdate 包含更新信息的功能实体。
     * @return 更新后的功能实体。
     */
    Functionality updateFunctionality(Long id, Functionality functionalityDetailsToUpdate);

    /**
     * 删除功能。
     * @param id 功能ID。
     * @throws com.example.contractmanagementsystem.exception.ResourceNotFoundException 如果功能未找到。
     */
    void deleteFunctionality(Long id);


    // --- 分配权限 (给用户分配角色) (3.7.2.4) ---
    /**
     * 为用户分配角色。
     * @param userId 用户ID。
     * @param roleNames 要分配的角色名称列表。
     * @return 更新后的用户实体。
     */
    User assignRolesToUser(Long userId, Set<String> roleNames);

}