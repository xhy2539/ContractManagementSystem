package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.entity.AuditLog;
import com.example.contractmanagementsystem.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletResponse; // 确保导入 HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort; // 确保导入 Sort
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.supercsv.io.CsvBeanWriter; // 导入 CsvBeanWriter
import org.supercsv.prefs.CsvPreference; // 导入 CsvPreference

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date; // 导入 Date
import java.util.List;

@RestController
@RequestMapping("/api/system/audit-logs")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    @Autowired
    public AuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    // 获取所有日志（分页，默认按时间戳降序排序）
    @GetMapping
    public ResponseEntity<Page<AuditLog>> getAllLogs(
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AuditLog> logs = auditLogRepository.findAll(pageable);
        return ResponseEntity.ok(logs);
    }

    // 根据用户名查询日志（分页，按时间戳降序排序）
    @GetMapping(params = "username")
    public ResponseEntity<Page<AuditLog>> getLogsByUsername(
            @RequestParam String username,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AuditLog> logs = auditLogRepository.findByUsernameOrderByTimestampDesc(username, pageable);
        return ResponseEntity.ok(logs);
    }

    // 根据操作类型查询日志（分页，按时间戳降序排序）
    @GetMapping(params = "action")
    public ResponseEntity<Page<AuditLog>> getLogsByAction(
            @RequestParam String action,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AuditLog> logs = auditLogRepository.findByActionOrderByTimestampDesc(action, pageable);
        return ResponseEntity.ok(logs);
    }

    // 根据时间范围查询日志 (不分页，返回列表，按时间戳降序排序)
    @GetMapping(params = {"startDate", "endDate"})
    public ResponseEntity<List<AuditLog>> getLogsByTimestampBetween(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        List<AuditLog> logs = auditLogRepository.findByTimestampBetweenOrderByTimestampDesc(startDate, endDate);
        return ResponseEntity.ok(logs);
    }

    // 新增：导出日志到CSV文件
    @GetMapping("/export")
    public void exportAuditLogsToCsv(HttpServletResponse response,
                                     @RequestParam(required = false) String username,
                                     @RequestParam(required = false) String action,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) throws IOException {

        response.setContentType("text/csv; charset=UTF-8"); // 指定CSV类型和UTF-8编码
        response.setCharacterEncoding("UTF-8"); // 确保响应编码为UTF-8，处理中文
        DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String currentDateTime = dateFormatter.format(new Date());
        String headerKey = "Content-Disposition";
        String headerValue = "attachment; filename=audit_logs_" + currentDateTime + ".csv";
        response.setHeader(headerKey, headerValue);

        // 为了确保中文文件名在某些浏览器中正确显示，可以对文件名进行URL编码
        // String encodedFileName = URLEncoder.encode("审计日志_" + currentDateTime + ".csv", StandardCharsets.UTF_8.toString());
        // String headerValue = "attachment; filename*=UTF-8''" + encodedFileName;
        // response.setHeader(headerKey, headerValue);


        List<AuditLog> listLogs;

        // 根据传入的参数决定查询逻辑
        // 注意：对于导出大量数据，直接findAll()可能导致内存问题。
        // 实际应用中，导出功能通常也应该有某种形式的限制或基于更细致的筛选。
        // 这里我们简化处理，允许根据参数筛选，若无参数则导出所有（需谨慎）。
        if (username != null) {
            // 导出用户相关的日志时，不进行分页，获取所有匹配的记录
            listLogs = auditLogRepository.findByUsernameOrderByTimestampDesc(username, Pageable.unpaged()).getContent();
        } else if (action != null) {
            listLogs = auditLogRepository.findByActionOrderByTimestampDesc(action, Pageable.unpaged()).getContent();
        } else if (startDate != null && endDate != null) {
            listLogs = auditLogRepository.findByTimestampBetweenOrderByTimestampDesc(startDate, endDate);
        } else {
            // 导出所有日志，不分页。警告：如果日志非常多，这可能会非常耗时且消耗大量内存。
            // 在生产环境中，通常会建议或强制用户提供筛选条件。
            listLogs = auditLogRepository.findAll(Sort.by(Sort.Direction.DESC, "timestamp"));
        }

        // 写入UTF-8 BOM，帮助Excel正确识别UTF-8编码的CSV文件，避免中文乱码
        response.getWriter().write('\uFEFF');


        try (CsvBeanWriter csvWriter = new CsvBeanWriter(response.getWriter(), CsvPreference.STANDARD_PREFERENCE)) {
            String[] csvHeader = {"日志ID", "操作用户", "操作类型", "操作详情", "时间戳"};
            // 这些是 AuditLog 实体类中对应的字段名 (getter方法名去掉get/is并首字母小写)
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