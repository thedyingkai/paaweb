package com.cwnupaa.backend.controller;

import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.service.EventRosterService;
import com.cwnupaa.backend.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.io.ByteArrayOutputStream;
import java.util.List;

@RestController
@RequestMapping("/event/admin/roster")
public class EventRosterController {
    private final EventRosterService roster;
    public EventRosterController(EventRosterService roster) { this.roster = roster; }
    public record ImportRequest(Long eventId, List<EventRosterService.Entry> entries) {}
    private boolean allowed(HttpServletRequest request) { return SecurityUtils.hasPermission(SecurityUtils.currentUser(request),"manage_contest"); }

    @GetMapping
    public Result<List<EventRosterService.Row>> list(@RequestParam Long eventId,HttpServletRequest request) {
        if (!allowed(request)) return Result.error(403,"无赛事管理权限");
        return Result.success(roster.rows(eventId));
    }
    @PostMapping("/import")
    public Result<List<EventRosterService.Row>> importRoster(@RequestBody ImportRequest body,HttpServletRequest request) {
        if (!allowed(request)) return Result.error(403,"无赛事管理权限");
        if (body.eventId() == null) return Result.error("请选择赛事");
        try { return Result.success(roster.importRoster(body.eventId(),body.entries())); }
        catch (IllegalArgumentException error) { return Result.error(error.getMessage()); }
    }
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam Long eventId,HttpServletRequest request) throws Exception {
        if (!allowed(request)) return ResponseEntity.status(403).build();
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("确定参赛名单");
            String[] headers = {"学校","学号","姓名","分组","座位","参与排名","账号关联"};
            Row header = sheet.createRow(0);
            for (int i=0;i<headers.length;i++) {header.createCell(i).setCellValue(headers[i]);sheet.setColumnWidth(i,24*256);}
            int index=1;
            for (var entry:roster.rows(eventId)) {
                Row row=sheet.createRow(index++);
                String[] values={entry.universityName(),entry.numberId(),entry.realName(),entry.groupLabel(),entry.seatNumber(),entry.isRanking()==1?"是":"否（打星）",entry.userId()==null?"待注册或实名核验":"已关联实名账号"};
                for(int i=0;i<values.length;i++) row.createCell(i,CellType.STRING).setCellValue(values[i]);
            }
            workbook.write(out);
            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=event-"+eventId+"-roster.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(out.toByteArray());
        }
    }
}
