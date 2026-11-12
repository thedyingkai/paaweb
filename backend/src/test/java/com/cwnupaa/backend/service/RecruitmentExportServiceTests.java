package com.cwnupaa.backend.service;

import org.junit.jupiter.api.Test;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import static org.assertj.core.api.Assertions.*;

class RecruitmentExportServiceTests {
    Map<String,Object> application() {
        var row=new HashMap<String,Object>();row.put("id",42L);row.put("real_name","测试/同学");row.put("student_number","001234");
        row.put("gender","男");row.put("political_status","共青团员");row.put("email","test@example.com");row.put("campus_rank","秋季校赛第 3 名");
        row.put("contact","=1+1");row.put("experience","2026 校赛\n二等奖");row.put("learning_content","动态规划");row.put("student_work","无");
        row.put("motivation","每周训练三次");row.put("platform_accounts","洛谷：tester / 100 题");row.put("status","pending");return row;
    }
    @Test void templateFieldsAndMergedLayoutArePreservedAndLongAnswersExpand() throws Exception {
        try(var doc=new XWPFDocument(new ByteArrayInputStream(RecruitmentExportService.document(application())))) {
            assertThat(doc.getParagraphs().getFirst().getText()).isEqualTo("ACM竞赛选拔报名表");
            var table=doc.getTables().getFirst();assertThat(table.getRows()).hasSize(9);
            assertThat(table.getRow(0).getCell(1).getText()).isEqualTo("测试/同学");
            assertThat(table.getRow(1).getCell(1).getText()).isEqualTo("001234");
            assertThat(table.getRow(2).getCell(3).getText()).isEqualTo("秋季校赛第 3 名");
            assertThat(table.getRow(4).getCell(1).getText()).isEqualTo("2026 校赛\n二等奖");
            assertThat(table.getRow(6).getCell(1).getText()).isEqualTo("无");
            assertThat(table.getRow(8).getCell(1).getText()).isEqualTo("洛谷：tester / 100 题");
            assertThat(table.getRow(0).getCell(4).getCTTc().getTcPr().getVMerge().getVal().toString()).isEqualTo("restart");
            for(var row:table.getRows())assertThat(row.getHeightRule()).isEqualTo(TableRowHeightRule.AT_LEAST);
        }
    }
    @Test void excelPreservesLeadingZerosAndTreatsUserInputAsText() throws Exception {
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(RecruitmentExportService.workbook(List.of(application()),"秋季选拔")))) {
            var sheet=book.getSheetAt(0);assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("001234");
            assertThat(sheet.getRow(1).getCell(8).getCellType()).isEqualTo(CellType.STRING);
            assertThat(sheet.getRow(1).getCell(8).getStringCellValue()).isEqualTo("=1+1");
            assertThat(sheet.getRow(1).getCell(14).getStringCellValue()).isEqualTo("待审核");
        }
    }
    @Test void archiveHasOneWordPerPersonAndAnExcelSummaryWithSafeUniqueNames() throws Exception {
        var first=application();var second=new HashMap<>(first);second.put("id",43L);
        try(var zip=new ZipInputStream(new ByteArrayInputStream(RecruitmentExportService.documents(List.of(first,second),"测试")))) {
            var names=new ArrayList<String>();ZipEntry entry;
            while((entry=zip.getNextEntry())!=null){names.add(entry.getName());assertThat(zip.readAllBytes()).isNotEmpty();}
            assertThat(names).containsExactly("报名汇总.xlsx","42_001234_测试_同学.docx","43_001234_测试_同学.docx");
        }
    }
}
