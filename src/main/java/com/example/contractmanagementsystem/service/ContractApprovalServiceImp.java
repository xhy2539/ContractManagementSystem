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
        // ⭐ 推荐这里也使用新的急切加载方法，以确保事务内的对象是完整的，避免潜在的N+1问题 ⭐
        Contract contract = contractRepository.findByIdWithCustomerAndDrafter(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("合同未找到，ID: " + contractId));

        // 2. 获取审批人
        User approver = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("审批人用户未找到，用户名: " + username));

        // 3. 业务逻辑验证：确保合同处于待审批状态
        if (contract.getStatus() != ContractStatus.PENDING_APPROVAL) {
            throw new BusinessLogicException("合同状态不是待审批 (" + contract.getStatus().getDescription() + ")，无法进行审批。");
        }

        LocalDateTime now = LocalDateTime.now();

        // 4. 创建并保存 ContractProcess 记录
        ContractProcess contractProcess = new ContractProcess();
        contractProcess.setContract(contract);
        contractProcess.setContractNumber(contract.getContractNumber());
        contractProcess.setType(ContractProcessType.APPROVAL);

        contractProcess.setOperator(approver);
        contractProcess.setOperatorUsername(approver.getUsername());
        contractProcess.setComments(comments);
        contractProcess.setProcessedAt(now);
        contractProcess.setCompletedAt(now);

        // 5. 根据审批决定更新合同状态和 ContractProcessState
        if (isApproved) {
            contract.setStatus(ContractStatus.PENDING_SIGNING);
            contractProcess.setState(ContractProcessState.APPROVED);
        } else {
            contract.setStatus(ContractStatus.REJECTED);
            contractProcess.setState(ContractProcessState.REJECTED);
        }

        // 6. 保存更新后的合同状态和新的流程记录
        contractRepository.save(contract);
        contractProcessRepository.save(contractProcess);
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