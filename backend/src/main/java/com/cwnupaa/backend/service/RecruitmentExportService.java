package com.cwnupaa.backend.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.*;

/** Uses the supplied selection form unchanged as the Word layout template. */
public final class RecruitmentExportService {
    private RecruitmentExportService() {}
    private static final String TEMPLATE="/templates/acm-recruitment.docx";
    private static final Map<String,String> STATUSES=Map.of("pending","待审核","approved","已通过","rejected","未通过","withdrawn","已撤回");
    private static String value(Map<String,Object> row,String key) {
        Object value=row.get(key);
        if(value instanceof Timestamp ts) return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai")).format(ts.toInstant());
        if(value instanceof OffsetDateTime dt) return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(dt.atZoneSameInstant(ZoneId.of("Asia/Shanghai")));
        return value==null?"":value.toString();
    }
    public static byte[] workbook(List<Map<String,Object>> rows,String batch) throws IOException {
        String[] keys={"id","real_name","gender","student_number","political_status","email","campus_rank","contact","experience","learning_content","student_work","motivation","platform_accounts","status","review_note","created_at","updated_at"};
        String[] labels={"申请编号","姓名","性别","学号","政治面貌","邮箱","校赛名次","联系方式","主要竞赛经历","近期竞赛学习内容","学生工作","训练计划及竞赛目标","各平台账户昵称及刷题量","审核状态","审核意见","首次提交时间（北京时间）","最后更新时间（北京时间）"};
        try(var book=new XSSFWorkbook();var out=new ByteArrayOutputStream()) {
            var sheet=book.createSheet("报名汇总");
            var headerStyle=book.createCellStyle();var font=book.createFont();font.setBold(true);headerStyle.setFont(font);
            var contentStyle=book.createCellStyle();contentStyle.setWrapText(true);contentStyle.setVerticalAlignment(VerticalAlignment.TOP);
            var header=sheet.createRow(0);header.createCell(0).setCellValue("申请批次");header.getCell(0).setCellStyle(headerStyle);
            for(int i=0;i<labels.length;i++){var cell=header.createCell(i+1);cell.setCellValue(labels[i]);cell.setCellStyle(headerStyle);}
            int index=1;
            for(var data:rows) {
                var row=sheet.createRow(index++);row.createCell(0,CellType.STRING).setCellValue(batch);
                for(int i=0;i<keys.length;i++) {
                    String text=value(data,keys[i]);if("status".equals(keys[i]))text=STATUSES.getOrDefault(text,text);
                    // Explicit string cells preserve leading-zero student numbers and never execute formulas.
                    var cell=row.createCell(i+1,CellType.STRING);cell.setCellValue(text);cell.setCellStyle(contentStyle);
                }
            }
            for(int i=0;i<=keys.length;i++)sheet.setColumnWidth(i,(i>=9 && i<=13?48:24)*256);
            sheet.createFreezePane(2,1);sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0,rows.size(),0,keys.length));
            book.write(out);return out.toByteArray();
        }
    }
    private static void fill(XWPFTable table,int row,int column,String text) {
        var cell=table.getRow(row).getCell(column);
        var paragraph=cell.getParagraphs().isEmpty()?cell.addParagraph():cell.getParagraphs().getFirst();
        while(cell.getParagraphs().size()>1)cell.removeParagraph(1);
        while(!paragraph.getRuns().isEmpty())paragraph.removeRun(0);
        var run=paragraph.createRun();run.setFontFamily("宋体");run.setFontSize(11);
        String[] lines=text.split("\\R",-1);
        for(int i=0;i<lines.length;i++){if(i>0)run.addBreak();run.setText(lines[i]);}
    }
    public static byte[] document(Map<String,Object> application) throws IOException {
        try(var source=RecruitmentExportService.class.getResourceAsStream(TEMPLATE)) {
            if(source==null)throw new IOException("报名表模板缺失");
            try(var doc=new XWPFDocument(source);var out=new ByteArrayOutputStream()) {
                var table=doc.getTables().getFirst();
                fill(table,0,1,value(application,"real_name"));fill(table,0,3,value(application,"gender"));
                fill(table,1,1,value(application,"student_number"));fill(table,1,3,value(application,"political_status"));
                fill(table,2,1,value(application,"email"));fill(table,2,3,value(application,"campus_rank"));
                fill(table,3,1,value(application,"contact"));fill(table,4,1,value(application,"experience"));
                fill(table,5,1,value(application,"learning_content"));fill(table,6,1,value(application,"student_work"));
                fill(table,7,1,value(application,"motivation"));fill(table,8,1,value(application,"platform_accounts"));
                // Retain the template's minimum row heights, but allow long answers to flow to further pages.
                for(var row:table.getRows())row.setHeightRule(TableRowHeightRule.AT_LEAST);
                doc.write(out);return out.toByteArray();
            }
        }
    }
    private static String safe(String text) {
        String result=text.replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]","_");
        return result.substring(0,Math.min(result.length(),70));
    }
    public static byte[] documents(List<Map<String,Object>> rows,String batch) throws IOException {
        try(var out=new ByteArrayOutputStream();var zip=new ZipOutputStream(out,StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("报名汇总.xlsx"));zip.write(workbook(rows,batch));zip.closeEntry();
            for(var row:rows) {
                String name=safe(value(row,"id")+"_"+value(row,"student_number")+"_"+value(row,"real_name"))+".docx";
                zip.putNextEntry(new ZipEntry(name));zip.write(document(row));zip.closeEntry();
            }
            zip.finish();return out.toByteArray();
        }
    }
}
