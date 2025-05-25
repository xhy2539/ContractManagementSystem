package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.entity.AuditLog;
import com.example.contractmanagementsystem.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.supercsv.io.CsvBeanWriter;
import org.supercsv.prefs.CsvPreference;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/system/audit-logs")
// 类级别 @PreAuthorize 确保只有 ROLE_ADMIN 可以访问这些API
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository; // 注意：这里直接使用了Repository，通常建议通过Service层

    @Autowired
    public AuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    // 获取所有日志（分页，默认按时间戳降序排序）
    @GetMapping
    // 要求用户拥有 "查阅审计日志" 这个功能权限
    @PreAuthorize("hasAuthority('查阅审计日志')")
    public ResponseEntity<Page<AuditLog>> getAllLogs(
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AuditLog> logs = auditLogRepository.findAll(pageable);
        return ResponseEntity.ok(logs);
    }

    // 根据用户名查询日志（分页，按时间戳降序排序）
    @GetMapping(params = "username")
    // 要求用户拥有 "查阅审计日志" 这个功能权限
    @PreAuthorize("hasAuthority('查阅审计日志')")
    public ResponseEntity<Page<AuditLog>> getLogsByUsername(
            @RequestParam String username,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AuditLog> logs = auditLogRepository.findByUsernameOrderByTimestampDesc(username, pageable);
        return ResponseEntity.ok(logs);
    }

    // 根据操作类型查询日志（分页，按时间戳降序排序）
    @GetMapping(params = "action")
    // 要求用户拥有 "查阅审计日志" 这个功能权限
    @PreAuthorize("hasAuthority('查阅审计日志')")
    public ResponseEntity<Page<AuditLog>> getLogsByAction(
            @RequestParam String action,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AuditLog> logs = auditLogRepository.findByActionOrderByTimestampDesc(action, pageable);
        return ResponseEntity.ok(logs);
    }

    // 根据时间范围查询日志 (不分页，返回列表，按时间戳降序排序)
    @GetMapping(params = {"startDate", "endDate"})
    // 要求用户拥有 "查阅审计日志" 这个功能权限
    @PreAuthorize("hasAuthority('查阅审计日志')")
    public ResponseEntity<List<AuditLog>> getLogsByTimestampBetween(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        List<AuditLog> logs = auditLogRepository.findByTimestampBetweenOrderByTimestampDesc(startDate, endDate);
        return ResponseEntity.ok(logs);
    }

    // 导出日志到CSV文件
    @GetMapping("/export")
    // 要求用户拥有 "导出审计日志" 这个功能权限
    @PreAuthorize("hasAuthority('导出审计日志')")
    public void exportAuditLogsToCsv(HttpServletResponse response,
                                     @RequestParam(required = false) String username,
                                     @RequestParam(required = false) String action,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) throws IOException {

        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String currentDateTime = dateFormatter.format(new Date());
        String headerKey = "Content-Disposition";
        String headerValue = "attachment; filename=audit_logs_" + currentDateTime + ".csv";
        response.setHeader(headerKey, headerValue);

        List<AuditLog> listLogs;

        if (username != null && !username.isEmpty()) {
            listLogs = auditLogRepository.findByUsernameOrderByTimestampDesc(username, Pageable.unpaged()).getContent();
        } else if (action != null && !action.isEmpty()) {
            listLogs = auditLogRepository.findByActionOrderByTimestampDesc(action, Pageable.unpaged()).getContent();
        } else if (startDate != null && endDate != null) {
            listLogs = auditLogRepository.findByTimestampBetweenOrderByTimestampDesc(startDate, endDate);
        } else {
            listLogs = auditLogRepository.findAll(Sort.by(Sort.Direction.DESC, "timestamp"));
        }

        response.getWriter().write('\uFEFF'); // UTF-8 BOM

        try (CsvBeanWriter csvWriter = new CsvBeanWriter(response.getWriter(), CsvPreference.STANDARD_PREFERENCE)) {
            String[] csvHeader = {"日志ID", "操作用户", "操作类型", "操作详情", "时间戳"};
            String[] nameMapping = {"id", "username", "action", "details", "timestamp"};

            csvWriter.writeHeader(csvHeader);

            if (listLogs != null) {
                for (AuditLog log : listLogs) {
                    csvWriter.write(log, nameMapping);
                }
            }
        }
    }
}