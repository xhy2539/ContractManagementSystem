package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.entity.*;
import com.example.contractmanagementsystem.exception.BusinessLogicException;
import com.example.contractmanagementsystem.exception.DuplicateResourceException;
import com.example.contractmanagementsystem.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemManagementServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private FunctionalityRepository functionalityRepository;
    @Mock
    private ContractRepository contractRepository;
    @Mock
    private ContractProcessRepository contractProcessRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private SystemManagementServiceImpl systemManagementService;

    private User testUserInstance;
    private Role testRoleInstance;
    private Functionality testFunctionalityInstance;
    private Contract testContractInstance;


    @BeforeEach
    void setUp() {
        testUserInstance = new User();
        testUserInstance.setId(1L); // ID 对于 equals/hashCode 很重要
        testUserInstance.setUsername("testuser");
        testUserInstance.setPassword("password123");
        testUserInstance.setEmail("testuser@example.com");
        testUserInstance.setEnabled(true);
        testUserInstance.setRoles(new HashSet<>());

        testRoleInstance = new Role();
        testRoleInstance.setId(1); // ID 对于 equals/hashCode 很重要
        testRoleInstance.setName("ROLE_USER_INSTANCE");
        testRoleInstance.setDescription("Test Role Instance");
        testRoleInstance.setFunctionalities(new HashSet<>());

        testFunctionalityInstance = new Functionality();
        testFunctionalityInstance.setId(1L); // ID 对于 equals/hashCode 很重要
        testFunctionalityInstance.setName("Test Functionality Instance");
        testFunctionalityInstance.setNum("F001_INSTANCE");


        testContractInstance = new Contract();
        testContractInstance.setId(1L);
        testContractInstance.setContractName("Test Contract Instance");
        testContractInstance.setContractNumber("C001_INSTANCE");
        testContractInstance.setStatus(ContractStatus.DRAFT);

        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
        lenient().when(authentication.getPrincipal()).thenReturn("testadmin");
    }

    // --- 用户管理测试 ---
    @Test
    void createUser_success() {
        User userToCreate = new User();
        userToCreate.setUsername("newuser");
        userToCreate.setPassword("password");
        userToCreate.setEmail("newuser@example.com");

        Set<String> roleNames = Set.of("ROLE_CREATE_TEST");
        Role roleForCreation = new Role();
        roleForCreation.setId(100); // 给一个ID，以便 equals/hashCode 能工作
        roleForCreation.setName("ROLE_CREATE_TEST");

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password")).thenReturn("encodedPassword");
        when(roleRepository.findByName("ROLE_CREATE_TEST")).thenReturn(Optional.of(roleForCreation));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User createdUser = systemManagementService.createUser(userToCreate, roleNames);

        assertNotNull(createdUser);
        assertEquals("newuser", createdUser.getUsername());
        assertEquals("encodedPassword", createdUser.getPassword());
        assertTrue(createdUser.getRoles().contains(roleForCreation));
        verify(auditLogService).logAction("testadmin", "CREATE_USER", "创建用户: newuser");
        verify(userRepository).save(userToCreate);
    }


    @Test
    void createUser_whenUsernameExists_throwsDuplicateResourceException() {
        User userToCreate = new User();
        userToCreate.setUsername("existinguser");
        when(userRepository.existsByUsername("existinguser")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> systemManagementService.createUser(userToCreate, Collections.emptySet()));
        verify(auditLogService, never()).logAction(anyString(), anyString(), anyString());
    }

    @Test
    void deleteUser_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUserInstance));
        lenient().when(contractProcessRepository.countByOperatorAndState(eq(testUserInstance), eq(ContractProcessState.PENDING))).thenReturn(0L);
        lenient().when(contractRepository.countByDrafter(eq(testUserInstance))).thenReturn(0L);

        systemManagementService.deleteUser(1L);

        verify(userRepository).delete(testUserInstance);
        verify(auditLogService).logAction(eq("testadmin"), eq("DELETE_USER"), contains("testuser"));
    }

    @Test
    void deleteUser_whenUserInPendingProcess_throwsBusinessLogicException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUserInstance));
        when(contractProcessRepository.countByOperatorAndState(eq(testUserInstance), eq(ContractProcessState.PENDING))).thenReturn(1L);

        assertThrows(BusinessLogicException.class, () -> systemManagementService.deleteUser(1L));
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void deleteUser_whenUserIsDrafter_throwsBusinessLogicException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUserInstance));
        when(contractProcessRepository.countByOperatorAndState(eq(testUserInstance), eq(ContractProcessState.PENDING))).thenReturn(0L);
        when(contractRepository.countByDrafter(eq(testUserInstance))).thenReturn(1L);

        assertThrows(BusinessLogicException.class, () -> systemManagementService.deleteUser(1L));
        verify(userRepository, never()).delete(any(User.class));
    }


    // --- 角色管理测试 ---
    @Test
    void createRole_success() {
        Role roleToCreate = new Role();
        roleToCreate.setName("NEW_ROLE");
        roleToCreate.setDescription("New Test Role");

        when(roleRepository.findByName("NEW_ROLE")).thenReturn(Optional.empty());
        when(functionalityRepository.findByName("Test Functionality Instance")).thenReturn(Optional.of(testFunctionalityInstance));
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Role createdRole = systemManagementService.createRole(roleToCreate, Set.of("Test Functionality Instance"));

        assertNotNull(createdRole);
        assertEquals("NEW_ROLE", createdRole.getName());
        // 使用 eq() 来确保比较的是基于 equals/hashCode 的，而不是引用
        assertTrue(createdRole.getFunctionalities().stream().anyMatch(f -> f.equals(testFunctionalityInstance)));
        verify(auditLogService).logAction(eq("testadmin"), eq("CREATE_ROLE"), eq("创建角色: NEW_ROLE"));
    }

    @Test
    void deleteRole_success_whenNotUsed() {
        when(roleRepository.findById(1)).thenReturn(Optional.of(testRoleInstance));
        lenient().when(userRepository.countByRolesContains(eq(testRoleInstance))).thenReturn(0L);

        systemManagementService.deleteRole(1);

        verify(roleRepository).delete(testRoleInstance);
        verify(auditLogService).logAction(eq("testadmin"), eq("DELETE_ROLE"), contains(testRoleInstance.getName()));
    }

    @Test
    void deleteRole_throwsBusinessLogicException_whenUsedByUser() {
        when(roleRepository.findById(1)).thenReturn(Optional.of(testRoleInstance));
        when(userRepository.countByRolesContains(eq(testRoleInstance))).thenReturn(1L);

        assertThrows(BusinessLogicException.class, () -> systemManagementService.deleteRole(1));
        verify(roleRepository, never()).delete(any(Role.class));
    }

    // --- 功能管理测试 ---
    @Test
    void createFunctionality_success() {
        Functionality funcToCreate = new Functionality(null, "NF001", "New Function", "/newfunc", "Desc");
        when(functionalityRepository.findByName("New Function")).thenReturn(Optional.empty());
        when(functionalityRepository.findByNum("NF001")).thenReturn(Optional.empty());
        when(functionalityRepository.save(any(Functionality.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Functionality createdFunc = systemManagementService.createFunctionality(funcToCreate);

        assertNotNull(createdFunc);
        assertEquals("New Function", createdFunc.getName());
        verify(auditLogService).logAction(eq("testadmin"), eq("CREATE_FUNCTIONALITY"), eq("创建功能: New Function"));
    }

    @Test
    void deleteFunctionality_success_andRemovesFromRoles() {
        Role roleWithFunc = new Role();
        roleWithFunc.setId(2);
        roleWithFunc.setName("ROLE_USING_FUNC_DEL");
        Set<Functionality> functionalities = new HashSet<>();
        // 添加在 setUp 中创建并带有 ID 的 testFunctionalityInstance
        functionalities.add(testFunctionalityInstance);
        roleWithFunc.setFunctionalities(functionalities);

        when(functionalityRepository.findById(1L)).thenReturn(Optional.of(testFunctionalityInstance));
        // 确保 findAllByFunctionalitiesContains 使用 eq 匹配器
        when(roleRepository.findAllByFunctionalitiesContains(eq(testFunctionalityInstance))).thenReturn(List.of(roleWithFunc));
        lenient().when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));


        systemManagementService.deleteFunctionality(1L);

        // 断言时也要注意 equals/hashCode
        assertFalse(roleWithFunc.getFunctionalities().contains(testFunctionalityInstance), "Functionality should be removed from role");
        verify(roleRepository).save(roleWithFunc);
        verify(functionalityRepository).delete(testFunctionalityInstance);

        InOrder inOrder = inOrder(auditLogService);
        inOrder.verify(auditLogService).logAction(eq("testadmin"), eq("DELETE_FUNCTIONALITY_CASCADE_ROLE_UPDATE"), contains(testFunctionalityInstance.getName()));
        inOrder.verify(auditLogService).logAction(eq("testadmin"), eq("DELETE_FUNCTIONALITY"), contains(testFunctionalityInstance.getName()));
    }


    // --- 合同分配测试 ---
    @Test
    void assignContractPersonnel_success() {
        when(contractRepository.findById(1L)).thenReturn(Optional.of(testContractInstance));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUserInstance));
        when(contractProcessRepository.save(any(ContractProcess.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(contractRepository.save(any(Contract.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Long> countersignUserIds = List.of(1L);
        boolean result = systemManagementService.assignContractPersonnel(1L, countersignUserIds, null, null);

        assertTrue(result);
        assertEquals(ContractStatus.PENDING_COUNTERSIGN, testContractInstance.getStatus());
        verify(contractProcessRepository).save(any(ContractProcess.class));
        verify(contractRepository).save(testContractInstance);
        verify(auditLogService).logAction(eq("testadmin"), eq("ASSIGN_CONTRACT_PERSONNEL"), contains(testContractInstance.getContractName()));
    }

    @Test
    void assignContractPersonnel_noPersonnelAssigned_returnsFalse() {
        when(contractRepository.findById(1L)).thenReturn(Optional.of(testContractInstance));
        testContractInstance.setStatus(ContractStatus.DRAFT);

        boolean result = systemManagementService.assignContractPersonnel(1L, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        assertFalse(result);
        assertEquals(ContractStatus.DRAFT, testContractInstance.getStatus());
        verify(contractProcessRepository, never()).save(any(ContractProcess.class));
        verify(contractRepository, never()).save(any(Contract.class));
        verify(auditLogService, never()).logAction(anyString(), eq("ASSIGN_CONTRACT_PERSONNEL"), anyString());
    }

    // --- 分配角色给用户测试 ---
    @Test
    void assignRolesToUser_success() {
        User userToUpdate = new User();
        userToUpdate.setId(1L); // ID
        userToUpdate.setUsername("userToUpdateRoles");
        userToUpdate.setRoles(new HashSet<>());

        Role adminRole = new Role();
        adminRole.setId(2); // ID
        adminRole.setName("ROLE_ADMIN");

        when(userRepository.findById(1L)).thenReturn(Optional.of(userToUpdate));
        when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        when(userRepository.save(eq(userToUpdate))).thenReturn(userToUpdate); // 使用 eq()

        User updatedUser = systemManagementService.assignRolesToUser(1L, Set.of("ROLE_ADMIN"));

        assertNotNull(updatedUser, "Updated user should not be null");
        assertEquals("userToUpdateRoles", updatedUser.getUsername());
        // 使用 eq() 来确保比较的是基于 equals/hashCode 的
        assertTrue(updatedUser.getRoles().stream().anyMatch(r -> r.equals(adminRole)), "User should have ROLE_ADMIN");
        verify(userRepository).save(userToUpdate);
        verify(auditLogService).logAction(eq("testadmin"), eq("ASSIGN_ROLES_TO_USER"), contains("ROLE_ADMIN"));
    }
}