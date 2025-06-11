package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.entity.ContractReminder;
import com.example.contractmanagementsystem.entity.ContractReminder.ReminderType;
import com.example.contractmanagementsystem.entity.ContractReminder.ReminderStatus;
import com.example.contractmanagementsystem.entity.ContractStatus;
import com.example.contractmanagementsystem.entity.User;
import com.example.contractmanagementsystem.exception.ResourceNotFoundException;
import com.example.contractmanagementsystem.repository.ContractReminderRepository;
import com.example.contractmanagementsystem.repository.ContractRepository;
import com.example.contractmanagementsystem.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 合同提醒服务实现类
 */
@Service
@Transactional
public class ContractReminderServiceImpl implements ContractReminderService {

    private static final Logger logger = LoggerFactory.getLogger(ContractReminderServiceImpl.class);

    @Autowired
    private ContractReminderRepository reminderRepository;

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Override
    public List<ContractReminder> createExpirationReminders(Long contractId, Integer daysBefore) {
        logger.info("创建合同到期提醒，合同ID: {}, 提前天数: {}", contractId, daysBefore);
        
        Contract contract = contractRepository.findById(contractId)
            .orElseThrow(() -> new ResourceNotFoundException("合同不存在: " + contractId));

        // 检查是否已经存在相同的提醒
        LocalDate targetDate = contract.getEndDate();
        LocalDate reminderDate = targetDate.minusDays(daysBefore);
        
        List<ContractReminder> existingReminders = reminderRepository.findByContractAndReminderType(
            contract, ReminderType.CONTRACT_EXPIRING);
        
        // 检查是否已有相同日期的提醒
        boolean alreadyExists = existingReminders.stream()
            .anyMatch(r -> r.getReminderDate().equals(reminderDate));
            
        if (alreadyExists) {
            logger.info("合同到期提醒已存在，跳过创建");
            return new ArrayList<>();
        }

        List<ContractReminder> reminders = new ArrayList<>();
        
        // 为合同相关人员创建提醒
        List<User> usersToNotify = getContractRelatedUsers(contract);
        
        for (User user : usersToNotify) {
            ContractReminder reminder = new ContractReminder();
            reminder.setContract(contract);
            reminder.setUser(user);
            reminder.setReminderType(ReminderType.CONTRACT_EXPIRING);
            reminder.setReminderDate(reminderDate);
            reminder.setTargetDate(targetDate);
            reminder.setDaysBefore(daysBefore);
            reminder.setTitle(String.format("合同即将到期提醒 - %s", contract.getContractName()));
            reminder.setMessage(String.format(
                "您好，合同《%s》将于 %s 到期（还有%d天），请注意及时处理。",
                contract.getContractName(), 
                targetDate.toString(), 
                daysBefore
            ));
            reminder.setStatus(ReminderStatus.PENDING);
            
            reminders.add(reminderRepository.save(reminder));
        }
        
        logger.info("成功创建{}个到期提醒", reminders.size());
        return reminders;
    }

    @Override
    public List<ContractReminder> createRenewalReminders(Long contractId, Integer daysBefore) {
        logger.info("创建合同续签提醒，合同ID: {}, 提前天数: {}", contractId, daysBefore);
        
        Contract contract = contractRepository.findById(contractId)
            .orElseThrow(() -> new ResourceNotFoundException("合同不存在: " + contractId));

        LocalDate targetDate = contract.getEndDate();
        LocalDate reminderDate = targetDate.minusDays(daysBefore);
        
        List<ContractReminder> reminders = new ArrayList<>();
        List<User> usersToNotify = getContractRelatedUsers(contract);
        
        for (User user : usersToNotify) {
            ContractReminder reminder = new ContractReminder();
            reminder.setContract(contract);
            reminder.setUser(user);
            reminder.setReminderType(ReminderType.RENEWAL_DUE);
            reminder.setReminderDate(reminderDate);
            reminder.setTargetDate(targetDate);
            reminder.setDaysBefore(daysBefore);
            reminder.setTitle(String.format("合同续签提醒 - %s", contract.getContractName()));
            reminder.setMessage(String.format(
                "您好，合同《%s》将于 %s 到期，请考虑续签事宜。",
                contract.getContractName(), 
                targetDate.toString()
            ));
            reminder.setStatus(ReminderStatus.PENDING);
            
            reminders.add(reminderRepository.save(reminder));
        }
        
        return reminders;
    }

    @Override
    public int scanAndCreateExpirationReminders(Integer daysBefore) {
        logger.info("批量扫描并创建{}天后到期的合同提醒", daysBefore);
        
        LocalDate targetDate = LocalDate.now().plusDays(daysBefore);
        
        List<Contract> contractsToRemind = reminderRepository.findContractsNeedingReminders(
            targetDate, targetDate, ReminderType.CONTRACT_EXPIRING);
            
        int createdCount = 0;
        for (Contract contract : contractsToRemind) {
            try {
                List<ContractReminder> reminders = createExpirationReminders(contract.getId(), daysBefore);
                createdCount += reminders.size();
            } catch (Exception e) {
                logger.error("创建合同提醒失败，合同ID: {}", contract.getId(), e);
            }
        }
        
        logger.info("批量创建提醒完成，共创建{}个提醒", createdCount);
        return createdCount;
    }

    @Override
    public int sendPendingReminders() {
        logger.info("开始发送待发送的提醒");
        
        LocalDate today = LocalDate.now();
        List<ContractReminder> pendingReminders = reminderRepository.findPendingRemindersForToday(today);
        
        int sentCount = 0;
        List<Long> sentReminderIds = new ArrayList<>();
        
        for (ContractReminder reminder : pendingReminders) {
            try {
                sendReminderEmail(reminder);
                sentReminderIds.add(reminder.getId());
                sentCount++;
            } catch (Exception e) {
                logger.error("发送提醒失败，提醒ID: {}", reminder.getId(), e);
            }
        }
        
        // 批量更新发送状态
        if (!sentReminderIds.isEmpty()) {
            reminderRepository.markAsSent(sentReminderIds, LocalDateTime.now());
        }
        
        logger.info("提醒发送完成，成功发送{}个提醒", sentCount);
        return sentCount;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractReminder> getUnreadReminders(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("用户不存在: " + userId));
            
        return reminderRepository.findByUserAndIsReadFalseOrderByCreatedAtDesc(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ContractReminder> getUserReminders(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("用户不存在: " + userId));
            
        return reminderRepository.findByUserOrderByCreatedAtDesc(user, pageable);
    }

    @Override
    public int markRemindersAsRead(Long userId, List<Long> reminderIds) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("用户不存在: " + userId));
            
        return reminderRepository.markAsRead(user, reminderIds, LocalDateTime.now());
    }

    @Override
    public int cleanupExpiredReminders(int daysOld) {
        logger.info("清理{}天前的过期提醒", daysOld);
        
        LocalDate cutoffDate = LocalDate.now().minusDays(daysOld);
        List<ContractReminder> expiredReminders = reminderRepository.findExpiredReminders(cutoffDate);
        
        int deletedCount = 0;
        for (ContractReminder reminder : expiredReminders) {
            try {
                reminderRepository.delete(reminder);
                deletedCount++;
            } catch (Exception e) {
                logger.error("删除过期提醒失败，提醒ID: {}", reminder.getId(), e);
            }
        }
        
        logger.info("清理过期提醒完成，删除了{}个提醒", deletedCount);
        return deletedCount;
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadReminderCount(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("用户不存在: " + userId));
            
        return reminderRepository.countByUserAndIsReadFalse(user);
    }

    @Override
    public void cancelContractReminders(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
            .orElseThrow(() -> new ResourceNotFoundException("合同不存在: " + contractId));
            
        reminderRepository.deleteByContract(contract);
        logger.info("已取消合同ID: {} 的所有提醒", contractId);
    }

    // 私有方法：获取合同相关人员
    private List<User> getContractRelatedUsers(Contract contract) {
        List<User> users = new ArrayList<>();
        
        // 添加起草人
        if (contract.getDrafter() != null) {
            users.add(contract.getDrafter());
        }
        
        // 可以添加其他相关人员，如审批人、签订人等
        // 这里可以根据业务需求扩展
        
        return users;
    }

    // 私有方法：发送提醒邮件
    private void sendReminderEmail(ContractReminder reminder) {
        try {
            User user = reminder.getUser();
            if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
                logger.warn("用户{}没有邮箱地址，跳过邮件发送", user.getUsername());
                return;
            }
            
            Map<String, Object> context = new HashMap<>();
            context.put("recipientName", user.getRealName() != null ? user.getRealName() : user.getUsername());
            context.put("taskType", reminder.getReminderType().getDescription());
            context.put("contractName", reminder.getContract().getContractName());
            context.put("reminderMessage", reminder.getMessage());
            context.put("targetDate", reminder.getTargetDate());
            
            // 构建合同详情链接
            String actionUrl = String.format("http://localhost:8080/contracts/%d/detail", 
                reminder.getContract().getId());
            context.put("actionUrl", actionUrl);
            
            emailService.sendHtmlMessage(
                user.getEmail(),
                reminder.getTitle(),
                "email/reminder-notification-email",
                context
            );
            
            logger.info("成功发送提醒邮件到: {}", user.getEmail());
            
        } catch (Exception e) {
            logger.error("发送提醒邮件失败，用户: {}", reminder.getUser().getUsername(), e);
            throw e;
        }
    }
} 