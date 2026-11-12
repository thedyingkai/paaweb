package com.cwnupaa.backend.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cwnupaa.backend.dto.UserAggregatedStatsDTO;
import com.cwnupaa.backend.dto.UserPracticeStatsDTO;
import com.cwnupaa.backend.mapper.UserMapper;
import com.cwnupaa.backend.service.AdminDataPanelService;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminDataPanelServiceImpl implements AdminDataPanelService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private com.cwnupaa.backend.mapper.UniversityMapper universityMapper;

    @Autowired
    private com.cwnupaa.backend.mapper.SchoolMapper schoolMapper;

    @Autowired
    private com.cwnupaa.backend.mapper.MajorMapper majorMapper;

    @Override
    public Map<String, Object> getOverallStats(String keyword, Long majorId, Long schoolId, Long universityId, String className, String gradeYear, Integer isEnrolledStu, String eduBg) {
        UserAggregatedStatsDTO stats = userMapper.selectUserAggregatedStats(keyword, majorId, schoolId, universityId, className, gradeYear, isEnrolledStu, eduBg);
        
        Map<String, Object> result = new HashMap<>();
        
        if (stats != null) {
            result.put("userCount", stats.getUserCount() != null ? stats.getUserCount() : 0L);
            result.put("totalSolved", stats.getTotalSolved() != null ? stats.getTotalSolved() : 0L);
            result.put("last30DaysSolved", stats.getLast30DaysSolved() != null ? stats.getLast30DaysSolved() : 0L);
            result.put("last7DaysSolved", stats.getLast7DaysSolved() != null ? stats.getLast7DaysSolved() : 0L);
            
            long userCount = stats.getUserCount() != null ? stats.getUserCount() : 0;
            long totalSolved = stats.getTotalSolved() != null ? stats.getTotalSolved() : 0;
            double averageSolved = userCount > 0 ? (double) totalSolved / userCount : 0;
            result.put("averageSolved", String.format("%.2f", averageSolved));
        } else {
            result.put("userCount", 0L);
            result.put("totalSolved", 0L);
            result.put("last30DaysSolved", 0L);
            result.put("last7DaysSolved", 0L);
            result.put("averageSolved", "0.00");
        }
        
        return result;
    }

    @Override
    public IPage<UserPracticeStatsDTO> getUserStats(Integer page, Integer size, String keyword, Long majorId, Long schoolId, Long universityId, String className, String gradeYear, Integer isEnrolledStu, String eduBg) {
        Page<UserPracticeStatsDTO> p = new Page<>(page, size);
        return userMapper.selectUserPracticeStats(p, keyword, majorId, schoolId, universityId, className, gradeYear, isEnrolledStu, eduBg);
    }

    @Override
    public void exportUserStats(HttpServletResponse response, String keyword, Long majorId, Long schoolId, Long universityId, String className, String gradeYear, Integer isEnrolledStu, String eduBg) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            List<UserPracticeStatsDTO> list = userMapper.selectUserPracticeStatsList(keyword, majorId, schoolId, universityId, className, gradeYear, isEnrolledStu, eduBg);
            
            Sheet sheet = workbook.createSheet("User Stats");
            
            // Create header row
            Row headerRow = sheet.createRow(0);
            String[] headers = {"姓名", "学号", "学院", "专业", "年级", "学历", "班级", "在校状态", "洛谷", "牛客", "Codeforces", "AtCoder", "学校 OJ", "QOJ / UCup", "总刷题量"};
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            
            // Fill data
            int rowNum = 1;
            for (UserPracticeStatsDTO dto : list) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(dto.getRealName());
                row.createCell(1).setCellValue(dto.getNumberId());
                row.createCell(2).setCellValue(dto.getCollegeName());
                row.createCell(3).setCellValue(dto.getMajorName());
                row.createCell(4).setCellValue(dto.getGradeYear());
                
                String eduBgText = "";
                if ("undergraduate".equals(dto.getEduBg())) {
                    eduBgText = "本科生";
                } else if ("postgraduate".equals(dto.getEduBg())) {
                    eduBgText = "研究生";
                } else {
                    eduBgText = "其他";
                }
                row.createCell(5).setCellValue(eduBgText);
                
                row.createCell(6).setCellValue(dto.getClassName());
                row.createCell(7).setCellValue(dto.getIsEnrolledStu() != null && dto.getIsEnrolledStu() == 1 ? "在校" : "离校");
                row.createCell(8).setCellValue(dto.getLuoguSolved() != null ? dto.getLuoguSolved() : 0);
                row.createCell(9).setCellValue(dto.getNowcoderSolved() != null ? dto.getNowcoderSolved() : 0);
                row.createCell(10).setCellValue(dto.getCodeforcesSolved() != null ? dto.getCodeforcesSolved() : 0);
                row.createCell(11).setCellValue(dto.getAtcoderSolved() != null ? dto.getAtcoderSolved() : 0);
                row.createCell(12).setCellValue(dto.getCwnuSolved() != null ? dto.getCwnuSolved() : 0);
                row.createCell(13).setCellValue(dto.getQojSolved() != null ? dto.getQojSolved() : 0);
                row.createCell(14).setCellValue(dto.getTotalSolved() != null ? dto.getTotalSolved() : 0);
            }
            
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            
            StringBuilder filename = new StringBuilder();
            
            // Try to extract metadata from the first item if available
            String universityName = "";
            String schoolName = "";
            String majorName = "";
            
            // If the list is not empty, we can get names from the first record if they match the filters
            // Note: This is an approximation. If we filter by ID, all records should have that ID.
            if (!list.isEmpty()) {
                UserPracticeStatsDTO first = list.get(0);
                
                // We don't have university name in DTO directly, but we can infer context
                // When gradeYear/eduBg are selected, schoolId/majorId might be null in params, but DTO still has them
                // However, DTO items might belong to different schools if schoolId was not filtered.
                // But if schoolId IS filtered, we use it.
                // If schoolId is NOT filtered, but we want to show it in filename IF all records happen to be same school? 
                // No, the rule is "if filter is empty, don't include in name".
                // But the user says "选择了学历之后，前面的学校、学院、专业等导出的时候名称就显示不全"
                // This means when gradeYear/eduBg is selected, schoolId/majorId might NOT be selected in filter,
                // but the user expects them to be in filename? 
                // NO, the user likely means: they selected School/College/Major AND Grade/EduBg, but School/College disappeared.
                // Why would they disappear?
                // The current logic: 
                // if (schoolId != null && !schoolName.isEmpty()) { filename.append(schoolName); }
                // where schoolName = first.getCollegeName() (if list not empty) OR derived from schoolId.
                
                // The issue might be:
                // `if (schoolId != null) { schoolName = first.getCollegeName(); }`
                // This logic inside `if (!list.isEmpty())` block sets `schoolName` ONLY if `schoolId` param is NOT null.
                // This seems correct: if we filtered by school, we use the name.
                
                // Wait, look at line 122:
                // if (schoolId != null) { schoolName = first.getCollegeName(); }
                // And line 125:
                // if (majorId != null) { majorName = first.getMajorName(); }
                
                // This relies on `first` item having the data.
                // If `schoolId` is provided, `first.getCollegeName()` should be populated.
                // Why would it be missing?
                // Maybe `list` is empty? If list is empty, `schoolName` remains empty string.
                // If list is empty, we should still try to get the name from ID if provided.
                
                // The previous logic I added for University handled this:
                // `if (universityId != null) { ... selectById ... }`
                // But for School and Major, I relied on `list.get(0)` inside `if (!list.isEmpty())`.
                
                // FIX: We should fetch School and Major names from DB if IDs are present, regardless of list content.
                // Just like I did for University.
            }
            
            // Get University Name if ID is present
            if (universityId != null) {
                com.cwnupaa.backend.entity.University uni = universityMapper.selectById(universityId);
                if (uni != null) {
                    universityName = uni.getUniversityName();
                }
            }
            
            // Get School Name
            if (schoolId != null) {
                com.cwnupaa.backend.entity.School school = schoolMapper.selectById(schoolId);
                if (school != null) {
                    schoolName = school.getSchoolName();
                    // Also try to fill universityName if missing
                    if (universityName.isEmpty()) {
                         com.cwnupaa.backend.entity.University u = universityMapper.selectById(school.getUniversityId());
                         if (u != null) universityName = u.getUniversityName();
                    }
                }
            } else if (schoolName.isEmpty() && !list.isEmpty()) {
                // Fallback: extract from first item if not filtered by ID but list exists
                UserPracticeStatsDTO first = list.get(0);
                if (first.getCollegeName() != null) {
                    schoolName = first.getCollegeName();
                    // Try to infer university if possible, though DTO doesn't have it.
                    // We can try to fetch School by name to find University, but that's expensive/risky.
                    // Just leave University empty if not found, as School is the priority here.
                }
            }
            
            // Get Major Name
            if (majorId != null) {
                com.cwnupaa.backend.entity.Major major = majorMapper.selectById(majorId);
                if (major != null) {
                    majorName = major.getMajorName();
                    // Also try to fill schoolName and universityName if missing
                    if (schoolName.isEmpty()) {
                        com.cwnupaa.backend.entity.School s = schoolMapper.selectById(major.getSchoolId());
                        if (s != null) {
                            schoolName = s.getSchoolName();
                            if (universityName.isEmpty()) {
                                com.cwnupaa.backend.entity.University u = universityMapper.selectById(s.getUniversityId());
                                if (u != null) universityName = u.getUniversityName();
                            }
                        }
                    }
                }
            } else if (majorName.isEmpty() && !list.isEmpty()) {
                // Fallback: extract from first item
                if (list.get(0).getMajorName() != null) {
                    majorName = list.get(0).getMajorName();
                }
            }

            // Construct filename parts
            // 1. School (University)
            if (universityId != null && !universityName.isEmpty()) {
                filename.append(universityName);
            } else if (!schoolName.isEmpty()) {
                // If University ID is not filtered, but School (College) is, try to find University from College
                // But schoolName is just a string here.
                // We can't easily get University from just schoolName string without more queries.
                // However, we can use the list.
                if (!list.isEmpty()) {
                    // Try to get university name from the first item if possible.
                    // But DTO does not have University Name.
                    // We can query School to get University ID, then University Name.
                    // But we only have schoolName string from DTO (if schoolId was null but derived from DTO).
                    // Wait, if schoolId is not null, we have schoolName.
                    // If schoolId IS null, but list is not empty, we got schoolName from list.get(0).
                    // In that case, we can try to fetch School entity by name (risky) or just leave it.
                    
                    // Actually, let's try to get School ID from DTO if we can? No DTO has names.
                    // Let's rely on the injected mappers if we have IDs.
                    
                    if (schoolId != null) {
                         com.cwnupaa.backend.entity.School s = schoolMapper.selectById(schoolId);
                         if (s != null) {
                             com.cwnupaa.backend.entity.University u = universityMapper.selectById(s.getUniversityId());
                             if (u != null) {
                                 filename.append(u.getUniversityName());
                             }
                         }
                    }
                }
            }

            // 2. College (School)
            if (schoolId != null && !schoolName.isEmpty()) {
                filename.append(schoolName);
            }
            
            // 3. Major
            if (majorId != null && !majorName.isEmpty()) {
                filename.append(majorName);
            }
            
            // 4. Grade Year
            if (gradeYear != null && !gradeYear.isEmpty()) {
                filename.append(gradeYear).append("级");
            }
            
            // 5. EduBg
            if (eduBg != null && !eduBg.isEmpty()) {
                if ("undergraduate".equals(eduBg)) {
                    filename.append("本科生");
                } else if ("postgraduate".equals(eduBg)) {
                    filename.append("研究生");
                }
            }
            
            // 6. Class
            if (className != null && !className.isEmpty()) {
                filename.append(className);
            }
            
            // 7. Enrolled Status
            if (isEnrolledStu != null) {
                filename.append(isEnrolledStu == 1 ? "在校生" : "非在校生");
            }
            
            // 8. Suffix
            filename.append("刷题数据");
            
            String finalFileName = filename.toString();
            // If only "刷题数据" is present, it means all filters were empty
            if (finalFileName.equals("刷题数据")) {
                finalFileName = "全站tracker刷题数据";
            }
            
            // Encode filename for HTTP header
            String encodedFileName = java.net.URLEncoder.encode(finalFileName + ".xlsx", "UTF-8").replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFileName);
            
            workbook.write(response.getOutputStream());
        } catch (IOException e) {
            throw new RuntimeException("Export failed", e);
        }
    }
}
