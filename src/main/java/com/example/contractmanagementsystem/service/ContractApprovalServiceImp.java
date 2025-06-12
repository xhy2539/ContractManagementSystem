package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.entity.ContractProcess;
import com.example.contractmanagementsystem.entity.ContractProcessState;
import com.example.contractmanagementsystem.entity.ContractProcessType;
import com.example.contractmanagementsystem.entity.ContractStatus;
import com.example.contractmanagementsystem.entity.User;
import com.example.contractmanagementsystem.exception.BusinessLogicException;
import com.example.contractmanagementsystem.exception.ResourceNotFoundException;
import com.example.contractmanagementsystem.repository.ContractRepository;
import com.example.contractmanagementsystem.repository.ContractProcessRepository;
import com.example.contractmanagementsystem.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ContractApprovalServiceImp implements ContractApprovalService {

    private final ContractRepository contractRepository;
    private final ContractProcessRepository contractProcessRepository;
    private final UserRepository userRepository;

    @Autowired
    public ContractApprovalServiceImp(
            ContractRepository contractRepository,
            ContractProcessRepository contractProcessRepository,
            UserRepository userRepository) {
        this.contractRepository = contractRepository;
        this.contractProcessRepository = contractProcessRepository;
        this.userRepository = userRepository;
    }

    @Override
    public Optional<Contract> getContractById(Long contractId) {
        // ⭐ 重点修改：现在调用新的 findByIdWithCustomerAndDrafter 方法 ⭐
        return contractRepository.findByIdWithCustomerAndDrafter(contractId);
    }

    @Override
    @Transactional
    public void processApproval(Long contractId, String username, boolean isApproved, String comments)
            throws ResourceNotFoundException, BusinessLogicException, AccessDeniedException {

        // 在事务内重新获取合同，确保关联数据加载
        Contract contract = contractRepository.findByIdWithCustomerAndDrafter(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("合同未找到，ID: " + contractId));

        // 获取审批人
        User approver = userRepository.findByUsernameWithRolesAndFunctionalities(username)
                .orElseThrow(() -> new ResourceNotFoundException("审批人用户未找到，用户名: " + username));

        // 业务逻辑验证：确保合同处于待审批状态
        if (contract.getStatus() != ContractStatus.PENDING_APPROVAL) {
            throw new BusinessLogicException("合同状态不是待审批 (" + contract.getStatus().getDescription() + ")，无法进行审批。");
        }

        LocalDateTime now = LocalDateTime.now();

        // 获取所有待审批的流程记录
        List<ContractProcess> allPendingApprovals = contractProcessRepository.findByContractAndTypeAndState(
                contract, ContractProcessType.APPROVAL, ContractProcessState.PENDING);

        // 获取当前用户的审批流程
        ContractProcess currentUserProcess = allPendingApprovals.stream()
                .filter(process -> process.getOperatorUsername().equals(username))
                .findFirst()
                .orElseThrow(() -> new BusinessLogicException("未找到您的审批任务"));

        // 创建并保存当前用户的 ContractProcess 记录
        currentUserProcess.setProcessedAt(now);
        currentUserProcess.setCompletedAt(now);
        currentUserProcess.setOperator(approver);
        currentUserProcess.setOperatorUsername(approver.getUsername());
        currentUserProcess.setComments(comments);

        // 如果是拒绝，直接设置合同状态为拒绝
        if (!isApproved) {
            contract.setStatus(ContractStatus.REJECTED);
            currentUserProcess.setState(ContractProcessState.REJECTED);
            contractRepository.save(contract);
            contractProcessRepository.save(currentUserProcess);
            return;
        }

        // 如果是通过，更新当前审批人的状态
        currentUserProcess.setState(ContractProcessState.APPROVED);
        contractProcessRepository.save(currentUserProcess);

        // 检查是否所有审批人都已审批通过
        List<ContractProcess> allApprovalProcesses = contractProcessRepository.findByContractAndType(
                contract, ContractProcessType.APPROVAL);
        
        boolean allApproved = allApprovalProcesses.stream()
                .filter(process -> process.getState() != ContractProcessState.PENDING)
                .allMatch(process -> process.getState() == ContractProcessState.APPROVED);
        
        boolean allProcessed = allApprovalProcesses.stream()
                .noneMatch(process -> process.getState() == ContractProcessState.PENDING);

        // 只有当所有人都审批通过时，才更新合同状态为待签订
        if (allApproved && allProcessed) {
            contract.setStatus(ContractStatus.PENDING_SIGNING);
            contractRepository.save(contract);
        }
    }

    @Override
    public boolean canUserApproveContract(String username, Long contractId) {
        // ⭐ 同样，这里也使用新的急切加载方法 ⭐
        Optional<Contract> contractOptional = contractRepository.findByIdWithCustomerAndDrafter(contractId);
        if (contractOptional.isEmpty()) {
            return false;
        }
        Contract contract = contractOptional.get();

        if (contract.getStatus() != ContractStatus.PENDING_APPROVAL) {
            return false;
        }

        Optional<User> userOptional = userRepository.findByUsername(username);
        if (userOptional.isEmpty()) {
            return false;
        }
        User currentUser = userOptional.get();

        // TODO: 此处是示例逻辑，你需要根据你的实际业务规则来判断用户是否有权审批。
        return true;
    }
}