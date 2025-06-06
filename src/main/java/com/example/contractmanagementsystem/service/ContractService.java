package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.dto.ContractDraftRequest;
import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.entity.ContractProcess;
import com.example.contractmanagementsystem.entity.ContractProcessState;
import com.example.contractmanagementsystem.entity.ContractProcessType;
import org.springframework.security.access.AccessDeniedException; // 确保导入
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
/**
 * 合同管理业务接口。
 * 定义合同起草、查询、状态统计、定稿等核心业务操作。
 */
public interface ContractService {

    /**
     * 起草新合同。
     * 附件现在通过 contractDraftRequest.attachmentServerFileNames 间接处理。
     *
     * @param request    合同起草请求数据（包含合同名称、客户信息、日期、内容、以及已上传附件的服务器端文件名列表等）。
     * @param username   当前起草人的用户名。
     * @return 新创建并保存的合同实体。
     * @throws IOException 如果（例如在服务层实现中仍有其他）I/O错误。
     * @throws com.example.contractmanagementsystem.exception.BusinessLogicException 如果业务逻辑校验失败 (例如日期错误)。
     * @throws com.example.contractmanagementsystem.exception.ResourceNotFoundException 如果关联的客户或用户不存在。
     */
    Contract draftContract(ContractDraftRequest request, String username) throws IOException;

    /**
     * 获取合同状态统计数据。
     * 统计不同状态（如草稿、待审批、已完成等）的合同数量。
     *
     * @return 一个Map，键为合同状态的字符串表示 (枚举的名称)，值为对应状态的合同数量。
     */
    Map<String, Long> getContractStatusStatistics();

    /**
     * 根据多种条件搜索和分页查询合同。
     *
     * @param currentUsername   执行查询的当前用户名，如果非null且用户非管理员，则用于过滤"我的合同"。
     * @param isAdmin           标记当前用户是否为管理员。
     * @param contractName   合同名称的模糊搜索关键字（可选）。
     * @param contractNumber 合同编号的模糊搜索关键字（可选）。
     * @param status         合同状态的精确匹配（可选，应为ContractStatus枚举的字符串表示）。
     * @param pageable       分页和排序信息。
     * @return 包含合同列表的分页结果。
     */
    Page<Contract> searchContracts(String currentUsername, boolean isAdmin, String contractName, String contractNumber, String status, Pageable pageable);

    /**
     * 查询指定用户、指定类型和指定状态的合同流程（如待会签、待审批、待签订等），并支持按合同名称搜索和分页。
     *
     * @param username           当前登录用户的用户名。
     * @param type               流程类型（如 COUNTERSIGN, APPROVAL, SIGNING）。
     * @param state              流程状态（如 PENDING, COMPLETED, REJECTED）。
     * @param contractNameSearch 合同名称搜索关键字（可选）。
     * @param pageable           分页信息。
     * @return 包含合同流程的 Page 对象。
     */
    Page<ContractProcess> getPendingProcessesForUser(String username, ContractProcessType type,
                                                     ContractProcessState state, String contractNameSearch,
                                                     Pageable pageable);

    /**
     * 获取指定用户的所有待处理任务列表。
     * 用于仪表盘显示用户需要操作的各类合同流程。
     *
     * @param username 当前登录用户的用户名。
     * @return 该用户的所有状态为 PENDING 的 ContractProcess 列表。
     */
    List<ContractProcess> getAllPendingTasksForUser(String username);


    /**
     * 获取指定附件文件的完整路径。
     * 主要用于文件下载操作。
     *
     * @param filename 附件的文件名（不包含路径）。
     * @return 附件文件的绝对路径。
     * @throws com.example.contractmanagementsystem.exception.BusinessLogicException 如果文件不存在或无法访问，或文件名格式不正确。
     * @throws com.example.contractmanagementsystem.exception.ResourceNotFoundException 如果文件未找到。
     */
    Path getAttachmentPath(String filename);

    /**
     * 根据ID获取合同的详细信息。
     *
     * @param id 合同ID。
     * @return 合同实体。
     * @throws com.example.contractmanagementsystem.exception.ResourceNotFoundException 如果合同未找到。
     */
    Contract getContractById(Long id);

    /**
     * 检查当前用户是否有权限审批指定的合同。
     *
     * @param username   用户名。
     * @param contractId 合同ID。
     * @return 如果用户有权限审批，则返回true；否则返回false。
     */
    boolean canUserApproveContract(String username, Long contractId);

    /**
     * 处理合同审批操作（通过或拒绝）。
     *
     * @param contractId 合同ID。
     * @param username   审批人用户名。
     * @param approved   审批决定 (true表示通过, false表示拒绝)。
     * @param comments   审批意见。
     * @throws com.example.contractmanagementsystem.exception.ResourceNotFoundException 如果合同或用户未找到。
     * @throws com.example.contractmanagementsystem.exception.BusinessLogicException 如果合同状态不允许审批或发生其他业务逻辑错误。
     * @throws AccessDeniedException 如果用户无权执行此操作。
     */
    void processApproval(Long contractId, String username, boolean approved, String comments) throws AccessDeniedException;

    /**
     * 根据流程ID、操作员用户名、期望的流程类型和状态，获取合同流程记录。
     * 用于在执行具体流程操作（如签订、审批）前验证并获取流程对象。
     *
     * @param contractProcessId 合同流程记录ID。
     * @param username          当前操作员的用户名。
     * @param expectedType      期望的合同流程类型。
     * @param expectedState     期望的合同流程状态。
     * @return 验证通过的合同流程实体。
     * @throws com.example.contractmanagementsystem.exception.ResourceNotFoundException 如果流程记录或用户未找到。
     * @throws com.example.contractmanagementsystem.exception.BusinessLogicException 如果流程类型或状态不匹配。
     * @throws AccessDeniedException 如果当前用户不是该流程记录的指定操作员，无权操作。
     */
    ContractProcess getContractProcessByIdAndOperator(Long contractProcessId, String username, ContractProcessType expectedType, ContractProcessState expectedState) throws AccessDeniedException;

    /**
     * 执行合同签订操作。
     *
     * @param contractProcessId 待签订的合同流程记录ID。
     * @param signingOpinion    签订意见 (可选)。
     * @param username          执行签订操作的用户名。
     * @throws com.example.contractmanagementsystem.exception.ResourceNotFoundException 如果流程记录或用户未找到。
     * @throws com.example.contractmanagementsystem.exception.BusinessLogicException 如果流程类型或状态不正确。
     * @throws AccessDeniedException 如果用户无权执行此操作。
     */
    void signContract(Long contractProcessId, String signingOpinion, String username) throws AccessDeniedException;

    /**
     * 获取当前用户（通常假定为起草人，或具有特定权限的用户）的待定稿合同列表。
     *
     * @param username 当前登录用户的用户名。
     * @param contractNameSearch 用于按合同名称进行模糊搜索的关键字 (可选, 为null或空则不进行名称筛选)。
     * @param pageable 分页和排序参数。
     * @return 分页后的待定稿合同列表。列表中的合同应处于 'PENDING_FINALIZATION' 状态。
     */
    Page<Contract> getContractsPendingFinalizationForUser(String username, String contractNameSearch, Pageable pageable);

    /**
     * 获取指定合同的详细信息，用于用户进行定稿操作前的审查。
     * 此方法应包含权限检查，确保当前用户有权对该合同进行定稿操作，
     * 并且合同处于正确的状态（例如 'PENDING_FINALIZATION'）。
     *
     * @param contractId 要获取详情的合同ID。
     * @param username 当前登录用户的用户名。
     * @return 合同实体，包含了定稿所需的信息。
     * @throws com.example.contractmanagementsystem.exception.ResourceNotFoundException 如果指定ID的合同不存在。
     * @throws AccessDeniedException 如果当前用户无权定稿此合同。
     * @throws com.example.contractmanagementsystem.exception.BusinessLogicException 如果合同状态不适合进行定稿。
     */
    Contract getContractForFinalization(Long contractId, String username) throws AccessDeniedException;

    /**
     * 执行合同定稿操作。
     * 附件通过 attachmentServerFileNames 列表间接处理。
     * 如果在定稿时允许替换附件，并且新附件已通过分块上传，则 attachmentServerFileNames 应为新附件的服务器端文件名列表。
     *
     * @param contractId 要定稿的合同ID。
     * @param finalizationComments 用户在定稿时提交的意见或备注 (可选)。
     * @param attachmentServerFileNames 已上传的附件在服务器上的文件名列表 (可选, 如果有附件或替换附件)。
     * @param updatedContent 用户在定稿时修改后的合同主要内容 (可选)。
     * @param username 执行定稿操作的用户名。
     * @return 已定稿并更新状态后的合同实体。
     * @throws IOException 如果（例如在服务层实现中仍有其他）I/O错误。
     * @throws com.example.contractmanagementsystem.exception.ResourceNotFoundException 如果指定ID的合同或用户不存在。
     * @throws AccessDeniedException 如果当前用户无权定稿此合同。
     * @throws com.example.contractmanagementsystem.exception.BusinessLogicException 如果合同状态不适合定稿，或发生其他业务校验失败。
     */
    Contract finalizeContract(Long contractId, String finalizationComments, List<String> attachmentServerFileNames, String updatedContent, String username) throws IOException, AccessDeniedException;

    /**
     * 处理会签操作。
     * @param contractProcessId 会签流程记录ID。
     * @param comments 会签意见。
     * @param username 执行会签操作的用户名。
     * @param isApproved 会签是否通过 (true为通过，false为拒绝)。
     * @throws AccessDeniedException 如果用户无权执行此操作。
     */
    void processCountersign(Long contractProcessId, String comments, String username, boolean isApproved) throws AccessDeniedException;


    // --- 仪表盘统计信息的方法声明 ---
    /**
     * 统计当前系统中所有状态为 ACTIVE 的合同数量。
     *
     * @param username 当前登录用户的用户名。
     * @param isAdmin  当前用户是否是管理员。
     * @return 有效合同的总数。
     */
    long countActiveContracts(String username, boolean isAdmin);

    /**
     * 统计在未来指定天数内即将到期的有效合同数量。
     *
     * @param days     指定的天数（例如 30 天）。
     * @param username 当前登录用户的用户名。
     * @param isAdmin  当前用户是否是管理员。
     * @return 即将到期的有效合同数量。
     */
    long countContractsExpiringSoon(int days, String username, boolean isAdmin);

    /**
     * Counts the number of contracts currently pending assignment.
     * @return The count of contracts with status PENDING_ASSIGNMENT.
     */
    long countContractsPendingAssignment();

    /**
     * 统计当前系统中所有状态为 EXPIRED 的合同数量。
     *
     * @param username 当前登录用户的用户名。
     * @param isAdmin  当前用户是否是管理员。
     * @return 已过期合同的总数。
     */
    long countExpiredContracts(String username, boolean isAdmin);

    /**
     * 检查所有合同的到期日期，并更新已过期合同的状态。
     * 此方法通常由定时任务或在特定事件（如登录）时调用。
     *
     * @return 更新状态的合同数量。
     */
    int updateExpiredContractStatuses();


    /**
     * (可选) 获取指定合同的所有会签意见。
     * @param contractId 合同ID。
     * @return 与该合同关联的、类型为COUNTERSIGN的ContractProcess列表。
     */
    List<ContractProcess> getContractCountersignOpinions(Long contractId);
    /**
     * 获取当前用户待会签的合同列表。
     * @param username 当前用户的用户名。
     * @param contractNameSearch 合同名称搜索关键词（可选）。
     * @param pageable 分页参数。
     * @return 待会签合同流程的Page。
     */
    Page<ContractProcess> getPendingCountersignContracts(String username, String contractNameSearch, Pageable pageable);

    /**
     * 获取特定合同流程的详细信息。
     * @param contractId 合同ID。
     * @param username 操作用户。
     * @param type 流程类型。
     * @param state 流程状态。
     * @return 匹配的合同流程Optional，如果不存在则为empty。
     */
    Optional<ContractProcess> getContractProcessDetails(Long contractId, String username, ContractProcessType type, ContractProcessState state);


    /**
     * 获取指定合同和指定类型的所有合同流程记录。
     * 用于在详情页展示所有会签意见。
     * @param contract 合同实体。
     * @param type 流程类型（例如COUNTERSIGN）。
     * @return 该合同和类型的所有流程记录列表。
     */

    List<ContractProcess> getAllContractProcessesByContractAndType(Contract contract, ContractProcessType type);


    /**
     * 判断当前用户是否可以会签指定合同。
     * @param contractId 合同ID。
     * @param username 用户名。
     * @return 如果用户可以会签则为 true，否则为 false。
     */


    boolean canUserCountersignContract(Long contractId, String username);

    /**
     * 处理会签操作。
     * @param contractProcessId 会签流程记录ID。
     * @param comments 会签意见。
     * @param username 操作用户。
     * @param isApproved 是否批准（true为批准，false为拒绝）。
     * @throws AccessDeniedException 如果用户无权操作。
     * @throws com.example.contractmanagementsystem.exception.ResourceNotFoundException 如果流程记录不存在。
     * @throws com.example.contractmanagementsystem.exception.BusinessLogicException 如果业务逻辑校验失败。
     */

    /**
     * 获取合同的所有处理历史记录
     * @param contractId 合同ID
     * @return 合同处理历史记录列表
     */
    List<ContractProcess> getContractProcessHistory(Long contractId);

    /**
     * 统计所有流程中合同的数量。
     * 流程中合同包括：PENDING_ASSIGNMENT, PENDING_COUNTERSIGN, PENDING_APPROVAL, PENDING_SIGNING, PENDING_FINALIZATION。
     * 会根据用户权限（是否为管理员）进行过滤。
     * @param username 当前用户的用户名。
     * @param isAdmin 是否为管理员。
     * @return 流程中合同的数量。
     */
    long countInProcessContracts(String username, boolean isAdmin);
}
