package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.dto.ContractDraftRequest;
import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.entity.ContractProcess;
import com.example.contractmanagementsystem.entity.ContractProcessState;
import com.example.contractmanagementsystem.entity.ContractProcessType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * 合同管理业务接口。
 * 定义合同起草、查询、状态统计等核心业务操作。
 */
public interface ContractService {

    /**
     * 起草新合同。
     *
     * @param request 合同起草请求数据（包含合同名称、客户信息、日期、内容等）。
     * @param attachment 合同附件文件（可选）。
     * @param username 当前起草人的用户名。
     * @return 新创建并保存的合同实体。
     * @throws IOException 如果附件处理过程中发生I/O错误。
     */
    Contract draftContract(ContractDraftRequest request, MultipartFile attachment, String username) throws IOException;

    /**
     * 获取合同状态统计数据。
     * 统计不同状态（如草稿、待审批、已完成等）的合同数量。
     *
     * @return 一个Map，键为合同状态的字符串表示，值为对应状态的合同数量。
     */
    Map<String, Long> getContractStatusStatistics();

    /**
     * 根据多种条件搜索和分页查询合同。
     *
     * @param contractName 合同名称的模糊搜索关键字（可选）。
     * @param contractNumber 合同编号的模糊搜索关键字（可选）。
     * @param status 合同状态的精确匹配（可选，应为ContractStatus枚举的字符串表示）。
     * @param pageable 分页和排序信息。
     * @return 包含合同列表的分页结果。
     */
    Page<Contract> searchContracts(String contractName, String contractNumber, String status, Pageable pageable);

    /**
     * 查询指定用户、指定类型和指定状态的合同流程（如待会签、待审批等），并支持按合同名称搜索和分页。
     *
     * @param username 当前登录用户的用户名。
     * @param type 流程类型（如 COUNTERSIGN, APPROVAL, SIGNING）。
     * @param state 流程状态（如 PENDING, COMPLETED, REJECTED）。
     * @param contractNameSearch 合同名称搜索关键字（可选）。
     * @param pageable 分页信息。
     * @return 包含合同流程的 Page 对象。
     */
    Page<ContractProcess> getPendingProcessesForUser(String username, ContractProcessType type,
                                                     ContractProcessState state, String contractNameSearch,
                                                     Pageable pageable);

    /**
     * 获取指定附件文件的完整路径。
     * 主要用于文件下载操作。
     *
     * @param filename 附件的文件名（不包含路径）。
     * @return 附件文件的绝对路径。
     * @throws com.example.contractmanagementsystem.exception.BusinessLogicException 如果文件不存在或无法访问，或文件名格式不正确。
     */
    Path getAttachmentPath(String filename);

    /**
     * 根据ID获取合同
     * @param id 合同ID
     * @return 合同对象
     */
    Contract getContractById(Long id);

    /**
     * 检查用户是否有权限审批指定合同
     * @param username 用户名
     * @param contractId 合同ID
     * @return 是否有权限
     */
    boolean canUserApproveContract(String username, Long contractId);

    /**
     * 处理合同审批
     * @param contractId 合同ID
     * @param username 审批人用户名
     * @param approved 是否通过
     * @param comments 审批意见
     */
    void processApproval(Long contractId, String username, boolean approved, String comments);
}