package com.cwnupaa.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cwnupaa.backend.entity.CertificateGrant;
import com.cwnupaa.backend.entity.CertificateIssue;
import com.cwnupaa.backend.entity.CertificateIssueMember;
import com.cwnupaa.backend.entity.CertificateLevel;
import com.cwnupaa.backend.entity.CertificateType;
import com.cwnupaa.backend.entity.Major;
import com.cwnupaa.backend.entity.School;
import com.cwnupaa.backend.entity.University;
import com.cwnupaa.backend.entity.Users;
import com.cwnupaa.backend.mapper.CertificateGrantMapper;
import com.cwnupaa.backend.mapper.CertificateIssueMapper;
import com.cwnupaa.backend.mapper.CertificateIssueMemberMapper;
import com.cwnupaa.backend.mapper.CertificateLevelMapper;
import com.cwnupaa.backend.mapper.CertificateTypeMapper;
import com.cwnupaa.backend.mapper.MajorMapper;
import com.cwnupaa.backend.mapper.SchoolMapper;
import com.cwnupaa.backend.mapper.UniversityMapper;
import com.cwnupaa.backend.mapper.UserMapper;
import com.cwnupaa.backend.vo.CertificateImportErrorVO;
import com.cwnupaa.backend.vo.CertificateImportMemberVO;
import com.cwnupaa.backend.vo.CertificateImportPreviewVO;
import com.cwnupaa.backend.vo.CertificateImportResultVO;
import com.cwnupaa.backend.vo.CertificateImportRowVO;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CertificateExcelImportService {
    private static final int MAX_DATA_ROWS = 1000;
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    @Autowired
    private CertificateTypeMapper typeMapper;
    @Autowired
    private CertificateIssueMapper issueMapper;
    @Autowired
    private CertificateIssueMemberMapper issueMemberMapper;
    @Autowired
    private CertificateGrantMapper grantMapper;
    @Autowired
    private CertificateLevelMapper levelMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private MajorMapper majorMapper;
    @Autowired
    private SchoolMapper schoolMapper;
    @Autowired
    private UniversityMapper universityMapper;
    @Autowired
    private EmailService emailService;

    public CertificateImportPreviewVO preview(Long typeId, MultipartFile file) {
        CertificateType type = requireType(typeId);
        validateFile(file);

        CertificateImportPreviewVO preview = new CertificateImportPreviewVO();
        List<CertificateImportRowVO> rows = new ArrayList<>();
        List<CertificateImportErrorVO> errors = new ArrayList<>();

        try (InputStream input = file.getInputStream(); Workbook workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() == 0) {
                errors.add(new CertificateImportErrorVO(1, "表格", "工作簿中没有工作表"));
                return finishPreview(preview, rows, errors);
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                errors.add(new CertificateImportErrorVO(1, "表头", "第一行必须是中文表头"));
                return finishPreview(preview, rows, errors);
            }

            DataFormatter formatter = new DataFormatter(Locale.CHINA, true);
            List<String> headers = readHeaders(headerRow, formatter);
            int memberPairCount = validateHeaders(headers, isTeam(type), errors);
            if (!errors.isEmpty()) return finishPreview(preview, rows, errors);

            int nonEmptyRows = 0;
            Set<String> numbersInWorkbook = new HashSet<>();
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (isBlankRow(row, headers.size(), formatter)) continue;
                nonEmptyRows++;
                if (nonEmptyRows > MAX_DATA_ROWS) {
                    errors.add(new CertificateImportErrorVO(rowIndex + 1, "表格", "单次最多导入 " + MAX_DATA_ROWS + " 张证书"));
                    break;
                }
                if (containsFormula(row, headers.size())) {
                    errors.add(new CertificateImportErrorVO(rowIndex + 1, "表格", "不允许使用公式，请粘贴为纯文本后再导入"));
                    continue;
                }

                CertificateImportRowVO parsed = isTeam(type)
                        ? parseTeamRow(row, rowIndex + 1, memberPairCount, formatter, errors)
                        : parsePersonalRow(row, rowIndex + 1, formatter, errors);
                if (parsed == null) continue;

                if (!numbersInWorkbook.add(parsed.getCertificateNo())) {
                    errors.add(new CertificateImportErrorVO(rowIndex + 1, "编号", "编号在本工作簿中重复"));
                }
                rows.add(parsed);
            }

            if (rows.isEmpty() && errors.isEmpty()) {
                errors.add(new CertificateImportErrorVO(2, "数据", "表格中没有可导入的数据行"));
            }
            appendExistingNumberErrors(rows, errors);
            appendDuplicateAwardErrors(type, rows, errors);
        } catch (Exception e) {
            errors.add(new CertificateImportErrorVO(1, "文件", "无法读取 Excel 文件：" + safeMessage(e)));
        }
        return finishPreview(preview, rows, errors);
    }

    @Transactional(rollbackFor = Exception.class)
    public CertificateImportResultVO issue(Long typeId, MultipartFile file, Long operatorId) {
        if (typeId == null) throw new IllegalArgumentException("证书模板不能为空");
        CertificateType type = typeMapper.selectForIssueUpdate(typeId);
        if (type == null) throw new IllegalArgumentException("证书模板不存在");
        validateModernTemplate(type);
        CertificateImportPreviewVO preview = preview(typeId, file);
        if (!preview.isValid()) {
            String firstError = preview.getErrors().isEmpty() ? "表格校验失败" : preview.getErrors().get(0).getMessage();
            throw new IllegalArgumentException("导入未执行：" + firstError);
        }

        Set<String> studentIds = preview.getRows().stream()
                .flatMap(row -> row.getMembers().stream())
                .map(CertificateImportMemberVO::getStudentId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        // 用户匹配：导入数据严格按“学校（大学）+ 学号”精确匹配。
        Map<String, Users> usersBySchoolAndNumber = new HashMap<>();
        if (!studentIds.isEmpty()) {
            List<Users> matchedUsers = userMapper.selectList(new QueryWrapper<Users>().in("number_id", studentIds).eq("account_status", "normal"));
            Set<Long> majorIds = matchedUsers.stream().map(Users::getMajorId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
            Map<Long, Major> majorMap = majorIds.isEmpty() ? Map.of()
                    : majorMapper.selectBatchIds(majorIds).stream().collect(Collectors.toMap(Major::getMajorId, m -> m));
            Set<Long> schoolIds = majorMap.values().stream().map(Major::getSchoolId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
            Map<Long, School> schoolMap = schoolIds.isEmpty() ? Map.of()
                    : schoolMapper.selectBatchIds(schoolIds).stream().collect(Collectors.toMap(School::getSchoolId, s -> s));
            Set<Long> universityIds = schoolMap.values().stream().map(School::getUniversityId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
            Map<Long, University> universityMap = universityIds.isEmpty() ? Map.of()
                    : universityMapper.selectBatchIds(universityIds).stream().collect(Collectors.toMap(University::getUniversityId, u -> u));

            for (Users u : matchedUsers) {
                if (!"normal".equals(u.getAccountStatus()) || !StringUtils.hasText(u.getNumberId())) continue;
                Major major = u.getMajorId() == null ? null : majorMap.get(u.getMajorId());
                School school = major == null || major.getSchoolId() == null ? null : schoolMap.get(major.getSchoolId());
                University uni = school == null || school.getUniversityId() == null ? null : universityMap.get(school.getUniversityId());
                if (uni != null && StringUtils.hasText(uni.getUniversityName())) {
                    usersBySchoolAndNumber.putIfAbsent(schoolNumberKey(uni.getUniversityName(), u.getNumberId()), u);
                }
            }
        }

        int recipientCount = 0;
        LocalDateTime now = LocalDateTime.now();
        String levelScope = resolveLevelScope(type);
        for (CertificateImportRowVO row : preview.getRows()) {
            CertificateIssue issue = new CertificateIssue();
            issue.setCertificateTypeId(typeId);
            issue.setCertificateNo(row.getCertificateNo());
            issue.setCertificateKind(isTeam(type) ? 1 : 0);
            issue.setLevelScope(levelScope);
            issue.setTeamName(isTeam(type) ? row.getTeamName() : null);
            issue.setAwardName(row.getAwardName());
            issue.setIssueDate(type.getTime());
            issue.setStatus("active");
            issue.setCreatedBy(operatorId);
            issue.setCreatedAt(now);
            issueMapper.insert(issue);

            String groupId = UUID.randomUUID().toString();
            String memberNames = row.getMembers().stream().map(CertificateImportMemberVO::getName).collect(Collectors.joining("、"));
            for (int index = 0; index < row.getMembers().size(); index++) {
                CertificateImportMemberVO member = row.getMembers().get(index);
                Users user = usersBySchoolAndNumber.get(schoolNumberKey(member.getSchool(), member.getStudentId()));
                if (user != null && !CertificateIdentityService.same(user.getRealName(), member.getName())) user = null;

                CertificateIssueMember snapshot = new CertificateIssueMember();
                snapshot.setIssueId(issue.getIssueId());
                snapshot.setMemberName(member.getName());
                snapshot.setStudentId(member.getStudentId());
                snapshot.setSchoolName(member.getSchool());
                snapshot.setUserId(user == null ? null : user.getUserId());
                snapshot.setSortOrder(index);
                issueMemberMapper.insert(snapshot);

                CertificateGrant grant = new CertificateGrant();
                grant.setCertificateTypeId(typeId);
                grant.setUserId(user == null ? null : user.getUserId());
                grant.setCreateTime(now);
                grant.setCustomName(isTeam(type) ? row.getTeamName() : member.getName());
                grant.setUserAccount(member.getStudentId());
                grant.setRealName(member.getName());
                grant.setSchoolName(member.getSchool());
                grant.setGroupId(groupId);
                grant.setIssueId(issue.getIssueId());
                grantMapper.insert(grant);
                recipientCount++;

                if (user != null && StringUtils.hasText(user.getEmail())) {
                    emailService.sendCertificateGrantEmail(user.getEmail(), member.getName(), type.getTypeName());
                }
            }
        }

        CertificateImportResultVO result = new CertificateImportResultVO();
        result.setCertificateCount(preview.getRows().size());
        result.setRecipientCount(recipientCount);
        result.setMessage(String.format("成功签发 %d 张证书，共关联 %d 名获奖人", preview.getRows().size(), recipientCount));
        return result;
    }

    private CertificateType requireType(Long typeId) {
        if (typeId == null) throw new IllegalArgumentException("证书模板不能为空");
        CertificateType type = typeMapper.selectById(typeId);
        if (type == null) throw new IllegalArgumentException("证书模板不存在");
        return type;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择 Excel 文件");
        if (file.getSize() > MAX_FILE_SIZE) throw new IllegalArgumentException("Excel 文件不能超过 10MB");
        String name = file.getOriginalFilename();
        if (name == null || !(name.toLowerCase(Locale.ROOT).endsWith(".xlsx") || name.toLowerCase(Locale.ROOT).endsWith(".xls"))) {
            throw new IllegalArgumentException("仅支持 .xlsx 或 .xls 文件");
        }
    }

    private void validateModernTemplate(CertificateType type) {
        if (type.getTime() == null) {
            throw new IllegalArgumentException("证书模板必须设置落款日期");
        }
        if (!StringUtils.hasText(resolveLevelScope(type))) {
            throw new IllegalArgumentException("证书模板必须设置国家级、省部级或校级");
        }
        String schema = type.getTemplateSchema();
        if (!StringUtils.hasText(schema)) {
            throw new IllegalArgumentException("该模板是旧版 PDF 表单模板，请先在模板编辑器中配置占位符");
        }
    }

    private String resolveLevelScope(CertificateType type) {
        if (StringUtils.hasText(type.getLevelScope())) return type.getLevelScope();
        if (type.getTypeId() == null || levelMapper == null) return null;
        CertificateLevel level = levelMapper.selectById(type.getTypeId());
        return level == null ? null : level.getLevelType();
    }

    private List<String> readHeaders(Row row, DataFormatter formatter) {
        int last = row.getLastCellNum();
        List<String> values = new ArrayList<>();
        for (int i = 0; i < Math.max(last, 0); i++) values.add(text(row.getCell(i), formatter));
        while (!values.isEmpty() && values.get(values.size() - 1).isEmpty()) values.remove(values.size() - 1);
        return values;
    }

    private int validateHeaders(List<String> headers, boolean team, List<CertificateImportErrorVO> errors) {
        if (!team) {
            List<String> expected = List.of("姓名", "学号", "学校", "奖项", "编号");
            if (!headers.equals(expected)) {
                errors.add(new CertificateImportErrorVO(1, "表头", "个人证书表头必须依次为：姓名、学号、学校、奖项、编号"));
            }
            return 1;
        }
        if (headers.size() < 6 || (headers.size() - 3) % 3 != 0) {
            errors.add(new CertificateImportErrorVO(1, "表头", "团队证书表头应为队伍名称、连续的队员姓名/学号/学校三列、奖项、编号"));
            return 0;
        }
        int memberPairs = (headers.size() - 3) / 3;
        if (memberPairs < 1 || memberPairs > 10) {
            errors.add(new CertificateImportErrorVO(1, "表头", "团队证书必须包含 1 至 10 组队员姓名、学号和学校列"));
            return memberPairs;
        }
        if (!"队伍名称".equals(headers.get(0))) {
            errors.add(new CertificateImportErrorVO(1, "表头", "第一列必须是“队伍名称”"));
        }
        for (int i = 1; i <= memberPairs; i++) {
            int nameIndex = 1 + (i - 1) * 3;
            if (!("队员" + i + "姓名").equals(headers.get(nameIndex))
                    || !("队员" + i + "学号").equals(headers.get(nameIndex + 1))
                    || !("队员" + i + "学校").equals(headers.get(nameIndex + 2))) {
                errors.add(new CertificateImportErrorVO(1, "表头", "队员" + i + "的姓名、学号、学校必须是相邻三列"));
            }
        }
        if (!"奖项".equals(headers.get(headers.size() - 2)) || !"编号".equals(headers.get(headers.size() - 1))) {
            errors.add(new CertificateImportErrorVO(1, "表头", "最后两列必须依次为“奖项、编号”"));
        }
        return memberPairs;
    }

    private CertificateImportRowVO parsePersonalRow(Row row, int rowNumber, DataFormatter formatter, List<CertificateImportErrorVO> errors) {
        int before = errors.size();
        String name = requiredText(row, 0, rowNumber, "姓名", 100, formatter, errors);
        String studentId = requiredText(row, 1, rowNumber, "学号", 50, formatter, errors);
        String school = requiredText(row, 2, rowNumber, "学校", 100, formatter, errors);
        String award = requiredText(row, 3, rowNumber, "奖项", 200, formatter, errors);
        String number = normalizedNumber(requiredText(row, 4, rowNumber, "编号", 100, formatter, errors));
        if (errors.size() > before) return null;
        CertificateImportRowVO result = baseRow(rowNumber, null, award, number);
        result.getMembers().add(new CertificateImportMemberVO(name, studentId, school));
        return result;
    }

    private CertificateImportRowVO parseTeamRow(Row row, int rowNumber, int memberPairs, DataFormatter formatter, List<CertificateImportErrorVO> errors) {
        int before = errors.size();
        String teamName = requiredText(row, 0, rowNumber, "队伍名称", 200, formatter, errors);
        List<CertificateImportMemberVO> members = new ArrayList<>();
        Set<String> memberIds = new HashSet<>();
        boolean emptyPairSeen = false;
        for (int i = 1; i <= memberPairs; i++) {
            int nameIndex = 1 + (i - 1) * 3;
            String name = limitedText(row, nameIndex, rowNumber, "队员" + i + "姓名", 100, formatter, errors);
            String studentId = limitedText(row, nameIndex + 1, rowNumber, "队员" + i + "学号", 50, formatter, errors);
            String school = limitedText(row, nameIndex + 2, rowNumber, "队员" + i + "学校", 100, formatter, errors);
            if (name.isEmpty() && studentId.isEmpty() && school.isEmpty()) {
                emptyPairSeen = true;
                continue;
            }
            if (name.isEmpty() || studentId.isEmpty() || school.isEmpty()) {
                errors.add(new CertificateImportErrorVO(rowNumber, "队员" + i, "姓名、学号和学校必须同时填写"));
                continue;
            }
            if (emptyPairSeen) {
                errors.add(new CertificateImportErrorVO(rowNumber, "队员" + i, "队员必须从队员1开始连续填写，中间不能留空"));
                continue;
            }
            if (!memberIds.add(schoolNumberKey(school, studentId))) {
                errors.add(new CertificateImportErrorVO(rowNumber, "队员" + i + "学号", "同一张证书中学校与学号不能重复"));
                continue;
            }
            members.add(new CertificateImportMemberVO(name, studentId, school));
        }
        if (members.isEmpty()) errors.add(new CertificateImportErrorVO(rowNumber, "队员", "每张团队证书至少需要 1 名队员"));
        int awardIndex = 1 + memberPairs * 3;
        String award = requiredText(row, awardIndex, rowNumber, "奖项", 200, formatter, errors);
        String number = normalizedNumber(requiredText(row, awardIndex + 1, rowNumber, "编号", 100, formatter, errors));
        if (errors.size() > before) return null;
        CertificateImportRowVO result = baseRow(rowNumber, teamName, award, number);
        result.setMembers(members);
        return result;
    }

    private CertificateImportRowVO baseRow(int rowNumber, String teamName, String award, String number) {
        CertificateImportRowVO result = new CertificateImportRowVO();
        result.setRowNumber(rowNumber);
        result.setTeamName(teamName);
        result.setAwardName(award);
        result.setCertificateNo(number);
        return result;
    }

    private String requiredText(Row row, int index, int rowNumber, String header, int maxLength, DataFormatter formatter, List<CertificateImportErrorVO> errors) {
        String value = limitedText(row, index, rowNumber, header, maxLength, formatter, errors);
        if (value.isEmpty()) errors.add(new CertificateImportErrorVO(rowNumber, header, "不能为空"));
        return value;
    }

    private String limitedText(Row row, int index, int rowNumber, String header, int maxLength, DataFormatter formatter, List<CertificateImportErrorVO> errors) {
        String value = text(row == null ? null : row.getCell(index), formatter);
        if (value.length() > maxLength) errors.add(new CertificateImportErrorVO(rowNumber, header, "长度不能超过 " + maxLength + " 个字符"));
        return value;
    }

    private String text(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";
        return formatter.formatCellValue(cell).replace('\u00a0', ' ').trim();
    }

    private String normalizedNumber(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String schoolNumberKey(String school, String numberId) {
        return (school == null ? "" : school.trim()) + "\u0001" + (numberId == null ? "" : numberId.trim());
    }

    private boolean isBlankRow(Row row, int columns, DataFormatter formatter) {
        if (row == null) return true;
        for (int i = 0; i < columns; i++) if (!text(row.getCell(i), formatter).isEmpty()) return false;
        return true;
    }

    private boolean containsFormula(Row row, int columns) {
        if (row == null) return false;
        for (int i = 0; i < columns; i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() == CellType.FORMULA) return true;
        }
        return false;
    }

    private void appendExistingNumberErrors(List<CertificateImportRowVO> rows, List<CertificateImportErrorVO> errors) {
        Set<String> numbers = rows.stream().map(CertificateImportRowVO::getCertificateNo).filter(StringUtils::hasText).collect(Collectors.toSet());
        if (numbers.isEmpty()) return;
        Set<String> existing = issueMapper.selectList(new QueryWrapper<CertificateIssue>().in("certificate_no", numbers).select("certificate_no"))
                .stream().map(CertificateIssue::getCertificateNo).collect(Collectors.toSet());
        for (CertificateImportRowVO row : rows) {
            if (existing.contains(row.getCertificateNo())) {
                errors.add(new CertificateImportErrorVO(row.getRowNumber(), "编号", "该证书编号已存在，不能重复签发"));
            }
        }
    }

    private record RecipientIdentity(String school, String studentId) {
        private RecipientIdentity {
            school = school == null ? "" : school.trim();
            studentId = studentId == null ? "" : studentId.trim();
        }
    }

    private record RecipientAward(int kind, Set<RecipientIdentity> recipients, String award) {
        private RecipientAward {
            recipients = Set.copyOf(recipients);
            award = award == null ? "" : award.trim();
        }
    }

    private void appendDuplicateAwardErrors(CertificateType type, List<CertificateImportRowVO> rows,
                                           List<CertificateImportErrorVO> errors) {
        if (rows.isEmpty()) return;
        int kind = isTeam(type) ? 1 : 0;
        String recipientLabel = isTeam(type) ? "队伍" : "个人";
        Map<RecipientAward, Integer> workbookAwards = new HashMap<>();
        Map<Integer, RecipientAward> rowAwards = new HashMap<>();
        for (CertificateImportRowVO row : rows) {
            Set<RecipientIdentity> recipients = row.getMembers().stream()
                    .map(member -> new RecipientIdentity(member.getSchool(), member.getStudentId()))
                    .collect(Collectors.toSet());
            RecipientAward award = new RecipientAward(kind, recipients, row.getAwardName());
            rowAwards.put(row.getRowNumber(), award);
            Integer firstRow = workbookAwards.putIfAbsent(award, row.getRowNumber());
            if (firstRow != null) {
                errors.add(new CertificateImportErrorVO(row.getRowNumber(), "奖项",
                        "与第 " + firstRow + " 行重复：同一" + recipientLabel + "在当前证书模板下的同一奖项只能有一条记录"));
            }
        }

        Set<String> awards = rows.stream().map(CertificateImportRowVO::getAwardName).collect(Collectors.toSet());
        List<CertificateIssue> existingIssues = issueMapper.selectList(new QueryWrapper<CertificateIssue>()
                .eq("certificate_type_id", type.getCertificateTypeId())
                .eq("certificate_kind", kind)
                .in("award_name", awards));
        if (existingIssues.isEmpty()) return;

        List<Long> issueIds = existingIssues.stream().map(CertificateIssue::getIssueId).toList();
        Map<Long, Set<RecipientIdentity>> issueRecipients = issueMemberMapper.selectList(new QueryWrapper<CertificateIssueMember>()
                        .in("issue_id", issueIds).select("issue_id", "school_name", "student_id"))
                .stream().collect(Collectors.groupingBy(CertificateIssueMember::getIssueId,
                        Collectors.mapping(member -> new RecipientIdentity(member.getSchoolName(), member.getStudentId()),
                                Collectors.toSet())));
        Map<RecipientAward, CertificateIssue> existingAwards = new HashMap<>();
        for (CertificateIssue issue : existingIssues) {
            Set<RecipientIdentity> recipients = issueRecipients.get(issue.getIssueId());
            if (recipients != null && !recipients.isEmpty()) {
                existingAwards.putIfAbsent(new RecipientAward(kind, recipients, issue.getAwardName()), issue);
            }
        }
        for (CertificateImportRowVO row : rows) {
            CertificateIssue existing = existingAwards.get(rowAwards.get(row.getRowNumber()));
            if (existing != null) {
                errors.add(new CertificateImportErrorVO(row.getRowNumber(), "奖项",
                        "该" + recipientLabel + "在当前证书模板下已存在“" + row.getAwardName()
                                + "”记录（编号：" + existing.getCertificateNo() + "），同一奖项只能有一条记录"));
            }
        }
    }

    private CertificateImportPreviewVO finishPreview(CertificateImportPreviewVO preview, List<CertificateImportRowVO> rows, List<CertificateImportErrorVO> errors) {
        preview.setRows(rows);
        preview.setErrors(errors);
        preview.setCertificateCount(rows.size());
        preview.setRecipientCount(rows.stream().mapToInt(row -> row.getMembers().size()).sum());
        preview.setValid(errors.isEmpty() && !rows.isEmpty());
        return preview;
    }

    private boolean isTeam(CertificateType type) {
        return Integer.valueOf(1).equals(type.getIsTeam());
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (!StringUtils.hasText(message)) return e.getClass().getSimpleName();
        return message.length() > 160 ? message.substring(0, 160) : message;
    }
}
