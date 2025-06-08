// File: xhy2539/contractmanagementsystem/ContractManagementSystem-xhy/src/main/java/com/example/contractmanagementsystem/repository/AuditLogRepository.java
package com.example.contractmanagementsystem.repository;

import com.example.contractmanagementsystem.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor; // 确保导入和继承
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> { // 添加 JpaSpecificationExecutor

    // 这些方法可以保留，但 searchLogs 会更通用
    Page<AuditLog> findByUsernameOrderByTimestampDesc(String username, Pageable pageable);
    Page<AuditLog> findByActionOrderByTimestampDesc(String action, Pageable pageable);
    List<AuditLog> findByTimestampBetweenOrderByTimestampDesc(LocalDateTime startTime, LocalDateTime endTime);
}