package com.cwnupaa.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.entity.*;
import com.cwnupaa.backend.mapper.*;
import com.cwnupaa.backend.utils.StudentNumberValidator;
import com.cwnupaa.backend.vo.CertificateGrantRecordVO;
import com.cwnupaa.backend.vo.CertificateGrantVO;
import com.cwnupaa.backend.vo.UserProfileVO;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDVariableText;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CertificateGrantService {
    @Autowired private HistoricalAwardService historicalAwardService;
    private final CertificatePdfCache legacyPdfCache = new CertificatePdfCache();

    @Autowired
    private CertificateTypeMapper typeMapper;
    @Autowired
    private CertificateGrantMapper grantMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private MajorMapper majorMapper;
    @Autowired
    private SchoolMapper schoolMapper;
    @Autowired
    private UniversityMapper universityMapper;
    @Autowired
    private FileSystemMapper fileMapper;
    @Autowired
    private CertificateLevelMapper levelMapper;
    @Autowired
    private TermMapper termMapper;
    @Autowired
    private EmailService emailService; // 注入邮件服务
    @Autowired
    private CertificateIssueMapper issueMapper;
    @Autowired
    private CertificateIssueMemberMapper issueMemberMapper;
    @Autowired
    private CertificateTemplateRenderService templateRenderService;

    @Autowired private CertificateIdentityService certificateIdentityService;
    @Autowired private SecurityAuditService securityAuditService;
    @Autowired private CertificateMemberMapper certificateMemberMapper;
    @Autowired private CertificateVerifyMapper certificateVerifyMapper;

    public Result<String> createTemplate(CertificateType type) {
        if (type.getUrlId() == null) return Result.error("请上传证书模板(.pdf)");
        checkFile(type.getUrlId());
        prepareTemplateLevel(type, null);
        if (type.getRatingEnabled() == null) {
            type.setRatingEnabled(true);
        }
        type.setTemplateSchema(templateRenderService.normalizeAndValidateSchema(type.getTemplateSchema()));
        if (type.getFontSize() == null || type.getFontSize() <= 0) {
            type.setFontSize(28);
        }
        typeMapper.insert(type);
        return Result.success("模板创建成功");
    }

    public Result<String> updateTemplate(CertificateType type) {
        if (type.getCertificateTypeId() == null) return Result.error("ID不能为空");
        CertificateType old = typeMapper.selectById(type.getCertificateTypeId());
        if (old == null) return Result.error("模板不存在");

        if (type.getUrlId() != null) {
            checkFile(type.getUrlId());
        }
        prepareTemplateLevel(type, old);
        if (type.getRatingEnabled() == null) {
            type.setRatingEnabled(!Boolean.FALSE.equals(old.getRatingEnabled()));
        }
        if (StringUtils.hasText(type.getTemplateSchema())) {
            type.setTemplateSchema(templateRenderService.normalizeAndValidateSchema(type.getTemplateSchema()));
        } else {
            type.setTemplateSchema(old.getTemplateSchema());
        }
        typeMapper.updateById(type);
        return Result.success("更新成功");
    }

    public Result<Page<CertificateType>> getTemplateList(Integer current, Integer size, Long termId, String keyword) {
        Page<CertificateType> page = new Page<>(current, size);
        QueryWrapper<CertificateType> wrapper = new QueryWrapper<>();

        if (termId != null) wrapper.eq("term", termId);
        if (StringUtils.hasText(keyword)) wrapper.like("type_name", keyword);

        wrapper.orderByDesc("certificate_type_id");
        typeMapper.selectPage(page, wrapper);

        if (!page.getRecords().isEmpty()) {
            List<Long> fileIds = page.getRecords().stream()
                    .map(CertificateType::getUrlId)
                    .filter(java.util.Objects::nonNull) // Add null filter
                    .collect(Collectors.toList());
            if (!fileIds.isEmpty()) {
                List<FileSystem> files = fileMapper.selectBatchIds(fileIds);
                Map<Long, FileSystem> fileMap = files.stream().collect(Collectors.toMap(FileSystem::getFileId, f -> f));
                for (CertificateType ct : page.getRecords()) {
                    FileSystem fs = fileMap.get(ct.getUrlId());
                    if (fs != null) {
                        ct.setTemplateFileName(fs.getName());
                        ct.setTemplateFileUrl(fs.getUrl());
                    }
                }
            }
        }
        hydrateLevelScope(page.getRecords());
        return Result.success(page);
    }

    private void checkFile(Long fileId) {
        FileSystem file = fileMapper.selectById(fileId);
        if (file == null || !file.getName().toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("文件不存在或不是PDF格式");
        }
        templateRenderService.validateSinglePagePdf(fileId);
    }

    private void prepareTemplateLevel(CertificateType type, CertificateType old) {
        if (!StringUtils.hasText(type.getLevelScope())) {
            if (type.getTypeId() != null) {
                CertificateLevel level = levelMapper.selectById(type.getTypeId());
                if (level != null) type.setLevelScope(level.getLevelType());
            } else if (old != null && StringUtils.hasText(old.getLevelScope())) {
                type.setLevelScope(old.getLevelScope());
            } else if (old != null && old.getTypeId() != null) {
                CertificateLevel level = levelMapper.selectById(old.getTypeId());
                if (level != null) type.setLevelScope(level.getLevelType());
            }
        }
        if (!StringUtils.hasText(type.getLevelScope())) {
            throw new IllegalArgumentException("请选择证书对应的大类等级");
        }
        Long representativeLevelId = findRepresentativeLevelId(type.getLevelScope());
        if (representativeLevelId == null) {
            throw new IllegalArgumentException("未找到等级“" + type.getLevelScope() + "”对应的证书等级配置");
        }
        type.setTypeId(representativeLevelId);
    }

    private Long findRepresentativeLevelId(String levelScope) {
        CertificateLevel level = levelMapper.selectOne(new QueryWrapper<CertificateLevel>()
                .eq("level_type", levelScope)
                .orderByAsc("base_rating")
                .last("LIMIT 1"));
        return level == null ? null : level.getLevelId();
    }

    private void hydrateLevelScope(List<CertificateType> types) {
        for (CertificateType type : types) {
            if (type == null || StringUtils.hasText(type.getLevelScope()) || type.getTypeId() == null) continue;
            CertificateLevel level = levelMapper.selectById(type.getTypeId());
            if (level != null) type.setLevelScope(level.getLevelType());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Object> grantCertificates(Long typeId, List<String> studentIds) {
        return grantCertificates(typeId, studentIds, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Object> grantCertificates(Long typeId, List<String> studentIds, boolean preview) {
        if (studentIds == null || studentIds.isEmpty()) return Result.error("名单不能为空");

        CertificateType template = typeMapper.selectById(typeId);
        if (template == null) return Result.error("模板不存在");
        if (StringUtils.hasText(template.getTemplateSchema())) {
            return Result.error("新版占位符模板请使用 Excel 导入发放，以便写入证书编号并支持真伪查验");
        }
        String certName = template.getTypeName();

        int successCount = 0;
        List<Map<String, String>> failedRecords = new ArrayList<>();
        List<Map<String, String>> successList = new ArrayList<>();

        // 1. Parse all lines first
        List<TeamEntry> parsedTeams = new ArrayList<>();
        // 学号允许跨校重复，批次内去重也必须使用“学校 + 学号”。
        Map<String, List<TeamEntry>> identityToTeamsMap = new java.util.HashMap<>();

        for (String line : studentIds) {
            if (!StringUtils.hasText(line)) continue;

            // Try splitting by Tab/Space
            String[] parts = line.split("[\\t\\s\\u3000]+");
            parts = java.util.Arrays.stream(parts).map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);

            if (parts.length < 3 || parts.length % 3 != 0) {
                 addFailure(failedRecords, line, "格式错误: 每位获奖人需按 姓名 学号 学校 三列填写");
                 continue;
            }

            TeamEntry team = new TeamEntry();
            team.originalLine = line;
            team.groupId = java.util.UUID.randomUUID().toString();
            boolean formatError = false;

            for (int i = 0; i < parts.length; i += 3) {
                String name = parts[i];
                String id = parts[i + 1];
                String school = parts[i + 2];

                if (!StringUtils.hasText(name) || !StringUtils.hasText(id) || !StringUtils.hasText(school)) {
                    formatError = true;
                    break;
                }

                team.members.add(new TeamMember(name, id, school));
            }

            if (formatError) {
                addFailure(failedRecords, line, "格式错误: 姓名、学号和学校均不能为空");
                continue;
            }

            parsedTeams.add(team);

            for (TeamMember m : team.members) {
                identityToTeamsMap.computeIfAbsent(m.identityKey(), k -> new ArrayList<>()).add(team);
            }
        }

        // 2. Check duplicates within batch
        Set<TeamEntry> invalidTeams = new java.util.HashSet<>();

        for (Map.Entry<String, List<TeamEntry>> entry : identityToTeamsMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                TeamMember duplicate = entry.getValue().get(0).members.stream()
                        .filter(member -> member.identityKey().equals(entry.getKey()))
                        .findFirst().orElse(null);
                String duplicateLabel = duplicate == null ? entry.getKey() : duplicate.school + "+" + duplicate.id;
                for (TeamEntry t : entry.getValue()) {
                    invalidTeams.add(t);
                    if (!t.isFailed) {
                        addFailure(failedRecords, t.originalLine, "冲突: 学校+学号 " + duplicateLabel + " 在本批次中多次出现");
                        t.isFailed = true;
                    }
                }
            }
        }

        // 3. Check duplicates in DB
        Map<String, Users> userMap = new java.util.HashMap<>();
        if (!identityToTeamsMap.isEmpty()) {
            Set<String> allIds = parsedTeams.stream()
                    .flatMap(team -> team.members.stream())
                    .map(member -> member.id)
                    .collect(Collectors.toSet());

            QueryWrapper<Users> userQuery = new QueryWrapper<>();
            userQuery.in("number_id", allIds).eq("account_status", "normal");
            List<Users> users = userMapper.selectList(userQuery);
            for (Users user : users) {
                String school = resolveUserSchoolName(user);
                if ("normal".equals(user.getAccountStatus()) && StringUtils.hasText(school) && StringUtils.hasText(user.getNumberId())) {
                    userMap.putIfAbsent(schoolNumberKey(school, user.getNumberId()), user);
                }
            }

            List<String> accountsToCheck = new ArrayList<>(allIds);
            List<Long> userIdsToCheck = users.stream().map(Users::getUserId).collect(Collectors.toList());

            QueryWrapper<CertificateGrant> dbCheck = new QueryWrapper<>();
            dbCheck.eq("certificate_type_id", typeId)
                   .and(w -> {
                       if (!accountsToCheck.isEmpty()) w.in("user_account", accountsToCheck);
                       if (!userIdsToCheck.isEmpty()) w.or().in("user_id", userIdsToCheck);
                   })
                   .select("user_id", "user_account", "school_name");

            List<CertificateGrant> existingGrants = grantMapper.selectList(dbCheck);

            Set<String> existingIdentities = new java.util.HashSet<>();
            Set<String> legacyAccounts = new java.util.HashSet<>();
            Set<Long> existingUserIds = new java.util.HashSet<>();

            for (CertificateGrant g : existingGrants) {
                if (StringUtils.hasText(g.getUserAccount())) {
                    if (StringUtils.hasText(g.getSchoolName())) {
                        existingIdentities.add(schoolNumberKey(g.getSchoolName(), g.getUserAccount()));
                    } else {
                        // 旧数据没有学校，继续保守地阻止相同学号重复发放。
                        legacyAccounts.add(g.getUserAccount());
                    }
                }
                if (g.getUserId() != null) existingUserIds.add(g.getUserId());
            }

            for (TeamEntry team : parsedTeams) {
                if (team.isFailed) continue;

                for (TeamMember m : team.members) {
                    Users u = userMap.get(m.identityKey());
                    boolean exists = legacyAccounts.contains(m.id)
                            || existingIdentities.contains(m.identityKey())
                            || (u != null && existingUserIds.contains(u.getUserId()));

                    if (exists) {
                        invalidTeams.add(team);
                        if (!team.isFailed) {
                            addFailure(failedRecords, team.originalLine,
                                    "冲突: 学校+学号 " + m.school + "+" + m.id + " 已获得该证书");
                            team.isFailed = true;
                        }
                        break;
                    }
                }
            }
        }

        // 4. Insert valid teams
        for (TeamEntry team : parsedTeams) {
            if (invalidTeams.contains(team)) continue;

            String fullTeamName = team.members.stream().map(m -> m.name).collect(Collectors.joining("、"));

            for (TeamMember m : team.members) {
                Users u = userMap.get(m.identityKey());
                if (u != null && !CertificateIdentityService.same(u.getRealName(), m.name)) u = null;

                CertificateGrant grant = new CertificateGrant();
                grant.setCertificateTypeId(typeId);
                grant.setCreateTime(LocalDateTime.now());
                grant.setCustomName(fullTeamName);
                grant.setUserAccount(m.id);
                grant.setRealName(m.name);
                grant.setSchoolName(m.school);
                grant.setGroupId(team.groupId);

                if (u != null) {
                    grant.setUserId(u.getUserId());
                }

                if (!preview) grantMapper.insert(grant);
                successCount++;

                if (!preview && u != null && StringUtils.hasText(u.getEmail())) {
                    emailService.sendCertificateGrantEmail(u.getEmail(), m.name, certName);
                }
            }
            Map<String, String> successRecord = new java.util.HashMap<>();
            successRecord.put("content", team.originalLine);
            successRecord.put("reason", "可发放 " + team.members.size() + " 人次");
            successList.add(successRecord);
        }

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("successCount", successCount);
        result.put("failCount", failedRecords.size());
        result.put("failedRecords", failedRecords);
        result.put("success", successList.size());
        result.put("failure", failedRecords.size());
        result.put("successList", successList);
        result.put("failedList", failedRecords.stream().map(item -> Map.of(
                "content", item.getOrDefault("line", ""),
                "reason", item.getOrDefault("reason", ""))).collect(Collectors.toList()));
        result.put("message", preview
                ? String.format("校验完成：%d 行可发放，共 %d 人次；%d 行不可导入。", successList.size(), successCount, failedRecords.size())
                : String.format("发放完成：成功发放 %d 人次，失败 %d 行。", successCount, failedRecords.size()));
        
        return Result.success(result);
    }

    private void addFailure(List<Map<String, String>> list, String line, String reason) {
        Map<String, String> map = new java.util.HashMap<>();
        map.put("line", line);
        map.put("reason", reason);
        list.add(map);
    }

    private static class TeamEntry {
        String originalLine;
        String groupId;
        List<TeamMember> members = new ArrayList<>();
        boolean isFailed = false;
    }
    
    private static class TeamMember {
        String name;
        String id;
        String school;

        public TeamMember(String name, String id, String school) {
            this.name = name;
            this.id = id;
            this.school = school;
        }

        String identityKey() {
            return schoolNumberKey(school, id);
        }
    }

    private static String schoolNumberKey(String school, String numberId) {
        return (school == null ? "" : school.trim()) + "\u0001" + (numberId == null ? "" : numberId.trim());
    }

    public Result<List<CertificateGrantRecordVO>> getGrantRecordList(Long typeId) {
        List<CertificateGrant> grants = grantMapper.selectList(new QueryWrapper<CertificateGrant>().eq("certificate_type_id", typeId).orderByDesc("create_time"));

        if (grants.isEmpty()) return Result.success(List.of());
        CertificateType currentTemplate = typeMapper.selectById(typeId);

        List<Long> issueIds = grants.stream().map(CertificateGrant::getIssueId).filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, CertificateIssue> issueMap = new java.util.HashMap<>();
        if (!issueIds.isEmpty()) {
            issueMapper.selectBatchIds(issueIds).forEach(issue -> issueMap.put(issue.getIssueId(), issue));
        }

        List<CertificateGrantRecordVO> list = new ArrayList<>();
        Map<String, List<CertificateGrant>> grouped = new java.util.HashMap<>();
        List<CertificateGrant> singles = new ArrayList<>();

        for (CertificateGrant g : grants) {
            if (StringUtils.hasText(g.getGroupId())) {
                grouped.computeIfAbsent(g.getGroupId(), k -> new ArrayList<>()).add(g);
            } else {
                singles.add(g);
            }
        }

        for (CertificateGrant g : singles) {
            CertificateGrantRecordVO vo = new CertificateGrantRecordVO();
            vo.setCertificateDownloadId(g.getCertificateDownloadId());
            vo.setCreateTime(g.getCreateTime());
            vo.setCustomName(g.getCustomName());
            applyIssueFields(vo, issueMap.get(g.getIssueId()), currentTemplate);

            vo.setUserAccount(g.getUserAccount() != null ? g.getUserAccount() : "未知用户");
            vo.setRealName(g.getRealName() != null ? g.getRealName() : "未知");
            list.add(vo);
        }

        for (Map.Entry<String, List<CertificateGrant>> entry : grouped.entrySet()) {
            List<CertificateGrant> group = entry.getValue();
            if (group.isEmpty()) continue;

            // Sort by ID ASC to ensure account order matches customName order
            group.sort(java.util.Comparator.comparing(CertificateGrant::getCertificateDownloadId));

            CertificateGrant first = group.get(0);
            CertificateGrantRecordVO vo = new CertificateGrantRecordVO();
            vo.setCertificateDownloadId(first.getCertificateDownloadId());
            vo.setCreateTime(first.getCreateTime());
            vo.setCustomName(first.getCustomName());
            vo.setRealName(first.getCustomName());
            applyIssueFields(vo, issueMap.get(first.getIssueId()), currentTemplate);

            String accountStr = group.stream().map(CertificateGrant::getUserAccount)
                    .filter(StringUtils::hasText).collect(Collectors.joining("、"));

            vo.setUserAccount(accountStr);
            list.add(vo);
        }
        
        list.sort((a, b) -> b.getCreateTime().compareTo(a.getCreateTime()));

        return Result.success(list);
    }

    private void applyIssueFields(CertificateGrantRecordVO vo, CertificateIssue issue, CertificateType currentTemplate) {
        if (issue == null) return;
        vo.setIssueId(issue.getIssueId());
        vo.setCertificateNo(issue.getCertificateNo());
        vo.setLevelScope(currentTemplate != null && StringUtils.hasText(currentTemplate.getLevelScope()) ? currentTemplate.getLevelScope() : issue.getLevelScope());
        vo.setAwardName(issue.getAwardName());
        vo.setTeamName(issue.getTeamName());
        vo.setIssueDate(currentTemplate != null && currentTemplate.getTime() != null ? currentTemplate.getTime() : issue.getIssueDate());
        vo.setStatus(issue.getStatus());
    }

    public Result<String> revokeGrant(Long grantId) {
        CertificateGrant grant = grantMapper.selectById(grantId);
        if (grant == null) return Result.error("记录不存在");

        if (grant.getIssueId() != null) {
            revokeModernIssue(grant.getIssueId());
            return Result.success("证书已撤销，编号将显示为无效");
        }
        
        // Revoke entire group if it exists
        if (StringUtils.hasText(grant.getGroupId())) {
            List<CertificateGrant> groupGrants = grantMapper.selectList(new QueryWrapper<CertificateGrant>().eq("group_id", grant.getGroupId()));
            for (CertificateGrant g : groupGrants) {
                notifyUserRevocation(g);
            }
            int rows = grantMapper.delete(new QueryWrapper<CertificateGrant>().eq("group_id", grant.getGroupId()));
            return rows > 0 ? Result.success("成功撤回团队证书 (" + rows + " 人)") : Result.error("操作失败");
        } else {
            notifyUserRevocation(grant);
            int rows = grantMapper.deleteById(grantId);
            return rows > 0 ? Result.success("撤回成功") : Result.error("操作失败");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<String> batchRevokeGrant(List<Long> grantIds) {
        if (grantIds == null || grantIds.isEmpty()) return Result.error("未选择记录");

        List<CertificateGrant> grants = grantMapper.selectBatchIds(grantIds);
        Set<Long> issueIds = grants.stream().map(CertificateGrant::getIssueId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<String> groupIds = grants.stream().filter(g -> g.getIssueId() == null).map(CertificateGrant::getGroupId)
                .filter(StringUtils::hasText).collect(Collectors.toSet());
        Set<Long> singleIds = grants.stream().filter(g -> g.getIssueId() == null && !StringUtils.hasText(g.getGroupId()))
                .map(CertificateGrant::getCertificateDownloadId).collect(Collectors.toSet());

        for (Long issueId : issueIds) revokeModernIssue(issueId);
        for (String groupId : groupIds) {
            List<CertificateGrant> group = grantMapper.selectList(new QueryWrapper<CertificateGrant>().eq("group_id", groupId));
            group.forEach(this::notifyUserRevocation);
            grantMapper.delete(new QueryWrapper<CertificateGrant>().eq("group_id", groupId));
        }
        if (!singleIds.isEmpty()) {
            grantMapper.selectBatchIds(singleIds).forEach(this::notifyUserRevocation);
            grantMapper.deleteBatchIds(singleIds);
        }
        int certificateCount = issueIds.size() + groupIds.size() + singleIds.size();
        return Result.success("成功撤回 " + certificateCount + " 张证书");
    }

    private void revokeModernIssue(Long issueId) {
        CertificateIssue issue = issueMapper.selectById(issueId);
        if (issue == null || "revoked".equals(issue.getStatus())) return;
        List<CertificateGrant> issueGrants = grantMapper.selectList(new QueryWrapper<CertificateGrant>().eq("issue_id", issueId));
        issueGrants.forEach(this::notifyUserRevocation);
        issue.setStatus("revoked");
        issue.setRevokedAt(LocalDateTime.now());
        issueMapper.updateById(issue);
    }

    private void notifyUserRevocation(CertificateGrant grant) {
        try {
            Users user = userMapper.selectById(grant.getUserId());
            CertificateType type = typeMapper.selectById(grant.getCertificateTypeId());

            if (user != null && StringUtils.hasText(user.getEmail()) && type != null) {
                String displayName = StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUserName();
                emailService.sendCertificateRevokeEmail(user.getEmail(), displayName, type.getTypeName());
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(CertificateGrantService.class).warn("操作失败，请检查请求参数和服务配置");
        }
    }

    public Result<List<CertificateGrantVO>> getMyCertificates(Long userId) {
        // 登录用户的证书列表只按已绑定的 user_id 查询。不能以学号作为兜底，
        // 否则可通过修改 URL 或伪造相同学号查看未归属给自己的证书。
        List<CertificateGrant> grants = grantMapper.selectList(
                new QueryWrapper<CertificateGrant>().eq("user_id", userId));
        Users owner = certificateIdentityService.verifiedUser(userId);
        List<CertificateGrantVO> list = grants.stream()
                .filter(g -> certificateIdentityService.owns(owner, g))
                .map(this::toGrantVO)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        return Result.success(list);
    }

    /** 未登录证书查询：姓名、学校和学号必须同时精确匹配。 */
    public Result<List<CertificateGrantVO>> guestList(String school, String numberId, String realName) {
        if (!StringUtils.hasText(school) || !StringUtils.hasText(numberId) || !StringUtils.hasText(realName)) {
            return Result.error("姓名、学校和学号不能为空");
        }
        List<CertificateGrant> grants = grantMapper.selectList(new QueryWrapper<CertificateGrant>()
                .eq("user_account", numberId.trim())
                .eq("school_name", school.trim())
                .eq("real_name", realName.trim())
                .orderByDesc("create_time"));
        List<CertificateGrantVO> list = grants.stream()
                .map(this::toGrantVO)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        return Result.success(list);
    }

    /** Called after real-name approval; all identity comes from the verified database record. */
    @Transactional(rollbackFor = Exception.class)
    public void linkCertificatesToUser(Long userId, String ignoredNumberId, String ignoredUniversityName) {
        Users user = certificateIdentityService.verifiedUser(userId);
        if (user == null) return;
        String school = certificateIdentityService.schoolName(user);
        if (!StringUtils.hasText(school) || !StringUtils.hasText(user.getNumberId()) || !StringUtils.hasText(user.getRealName())) return;
        for (CertificateGrant grant : grantMapper.selectList(new QueryWrapper<CertificateGrant>()
                .eq("user_account", user.getNumberId()).eq("school_name", school).eq("real_name", user.getRealName()).isNull("user_id"))) {
            if (!certificateIdentityService.matches(user, grant.getSchoolName(), grant.getUserAccount(), grant.getRealName())) continue;
            grant.setUserId(userId);
            grantMapper.updateById(grant);
        }
        for (CertificateIssueMember member : issueMemberMapper.selectList(new QueryWrapper<CertificateIssueMember>()
                .eq("student_id", user.getNumberId()).eq("school_name", school).eq("member_name", user.getRealName()).isNull("user_id"))) {
            if (!certificateIdentityService.matches(user, member.getSchoolName(), member.getStudentId(), member.getMemberName())) continue;
            member.setUserId(userId);
            issueMemberMapper.updateById(member);
        }
        historicalAwardService.linkVerifiedUser(userId);
    }

    public record IdentityReviewEntry(String recordKey, String userAccount, String realName, String schoolName, Long userId, String context) {}

    public List<IdentityReviewEntry> identityReviewList(Long typeId) {
        List<IdentityReviewEntry> result = new ArrayList<>();
        for (CertificateGrant grant : grantMapper.selectList(new QueryWrapper<CertificateGrant>().eq(typeId != null, "certificate_type_id", typeId).orderByDesc("create_time"))) {
            if (certificateIdentityService.owns(certificateIdentityService.verifiedUser(grant.getUserId()), grant)) continue;
            result.add(new IdentityReviewEntry("grant:" + grant.getCertificateDownloadId(), grant.getUserAccount(), grant.getRealName(), grant.getSchoolName(), grant.getUserId(), "官方证书"));
        }
        for (CertificateMember member : certificateMemberMapper.selectList(new QueryWrapper<CertificateMember>()
                .inSql("certificate_verify_id", "SELECT certificate_verify_id FROM certificate_verify WHERE status = 'approved'"))) {
            CertificateVerify verification = certificateVerifyMapper.selectById(member.getCertificateVerifyId());
            if (verification == null || !"approved".equals(verification.getStatus()) || java.util.Objects.equals(member.getUserId(), verification.getUserId())) continue;
            if (certificateIdentityService.ownsMember(certificateIdentityService.verifiedUser(member.getUserId()), member)) continue;
            result.add(new IdentityReviewEntry("member:" + member.getMemberId(), member.getStudentId(), member.getName(), member.getSchoolName(), member.getUserId(), "已审核申请材料：" + verification.getEventName()));
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<String> confirmMemberIdentity(Long memberId, Long userId, String school, String number, String name, Long actorId) {
        Users user = certificateIdentityService.verifiedUser(userId);
        if (!certificateIdentityService.matches(user, school, number, name)) return Result.error("目标账号须已实名审核通过，学校、学号和姓名须与实名资料完全一致");
        CertificateMember member = certificateMemberMapper.selectOne(new QueryWrapper<CertificateMember>().eq("member_id", memberId).last("FOR UPDATE"));
        if (member == null) return Result.error("获奖成员不存在");
        CertificateVerify verification = certificateVerifyMapper.selectOne(new QueryWrapper<CertificateVerify>().eq("certificate_verify_id", member.getCertificateVerifyId()).last("FOR UPDATE"));
        if (verification == null || !"approved".equals(verification.getStatus())) return Result.error("仅已审核通过的申请材料可确认归属");
        if (!CertificateIdentityService.same(member.getStudentId(), number) || !CertificateIdentityService.same(member.getName(), name)
                || (StringUtils.hasText(member.getSchoolName()) && !CertificateIdentityService.same(member.getSchoolName(), school))) return Result.error("原始获奖人资料与确认资料不一致，请先核实材料");
        member.setSchoolName(school.trim()); member.setUserId(userId); certificateMemberMapper.updateById(member);
        securityAuditService.record(actorId, userId, "certificate_member_identity_confirm:" + memberId, List.of("school_name", "user_id"));
        return Result.success("获奖成员归属已确认");
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<String> confirmIdentity(Long grantId, Long userId, String school, String number, String name, Long actorId) {
        Users user = certificateIdentityService.verifiedUser(userId);
        if (!certificateIdentityService.matches(user, school, number, name)) return Result.error("目标账号须已实名审核通过，学校、学号和姓名须与实名资料完全一致");
        CertificateGrant grant = grantMapper.selectOne(new QueryWrapper<CertificateGrant>().eq("certificate_download_id", grantId).last("FOR UPDATE"));
        if (grant == null) return Result.error("证书不存在");
        if (!CertificateIdentityService.same(grant.getUserAccount(), number) || !CertificateIdentityService.same(grant.getRealName(), name)
                || (StringUtils.hasText(grant.getSchoolName()) && !CertificateIdentityService.same(grant.getSchoolName(), school))) {
            return Result.error("证书原始姓名、学号或学校与确认资料不一致，请先核实原始材料");
        }
        grant.setSchoolName(school.trim());
        grant.setUserId(userId);
        grantMapper.updateById(grant);
        if (grant.getIssueId() != null) {
            for (CertificateIssueMember member : issueMemberMapper.selectList(new QueryWrapper<CertificateIssueMember>()
                    .eq("issue_id", grant.getIssueId()).eq("student_id", number).eq("member_name", name))) {
                if (StringUtils.hasText(member.getSchoolName()) && !CertificateIdentityService.same(member.getSchoolName(), school)) continue;
                member.setSchoolName(school.trim()); member.setUserId(userId); issueMemberMapper.updateById(member);
            }
        }
        securityAuditService.record(actorId, userId, "certificate_identity_confirm:" + grantId, List.of("school_name", "user_id"));
        return Result.success("证书归属已确认");
    }

    private String resolveUserSchoolName(Users user) {
        if (user == null || user.getMajorId() == null) return null;
        return StudentNumberValidator.resolveUniversityName(majorMapper, schoolMapper, universityMapper, user.getMajorId());
    }

    private CertificateGrantVO toGrantVO(CertificateGrant g) {
        CertificateType type = typeMapper.selectById(g.getCertificateTypeId());
        if (type == null) return null;
        CertificateGrantVO vo = new CertificateGrantVO();
        BeanUtils.copyProperties(type, vo);
        vo.setCertificateDownloadId(g.getCertificateDownloadId());
        CertificateLevel level = levelMapper.selectById(type.getTypeId());
        if (level != null) {
            vo.setLevelName(level.getName());
            vo.setLevelType(level.getLevelType());
        }
        Term term = termMapper.selectById(type.getTerm());
        if (term != null) vo.setTermName(term.getTermName());
        Long displayTemplateFileId = type.getUrlId();
        if (g.getIssueId() != null) {
            CertificateIssue issue = issueMapper.selectById(g.getIssueId());
            if (issue != null) {
                if (!"active".equals(issue.getStatus())) return null;
                vo.setCertificateNo(issue.getCertificateNo());
                vo.setAwardName(issue.getAwardName());
                vo.setTeamName(issue.getTeamName());
                if (type.getTime() != null || issue.getIssueDate() != null) {
                    vo.setTime(type.getTime() != null ? type.getTime() : issue.getIssueDate());
                }
                if (!StringUtils.hasText(type.getLevelScope()) && StringUtils.hasText(issue.getLevelScope())) vo.setLevelScope(issue.getLevelScope());
            }
        }
        if (displayTemplateFileId != null) {
            FileSystem file = fileMapper.selectById(displayTemplateFileId);
            if (file != null) vo.setTemplateImageUrl("/api" + file.getUrl());
        }
        return vo;
    }

    public byte[] generatePdf(Long grantId, UserProfileVO currentUser) throws Exception {
        return generatePdf(grantId, grant -> {
            if (currentUser == null) return false;

            // 普通用户只能访问明确绑定到当前 user_id 的证书，不能以学号匹配兜底。
            // 这样即使篡改 grantId，也无法预览或下载其他人的证书。
            return certificateIdentityService.owns(certificateIdentityService.verifiedUser(currentUser.getUserId()), grant)
                    || canManageCertificateGrants(currentUser);
        });
    }

    private boolean canManageCertificateGrants(UserProfileVO currentUser) {
        return com.cwnupaa.backend.utils.SecurityUtils.hasPermission(currentUser, "manage_cert_grant");
    }

    /** 未登录证书下载：姓名、学校和学号均须与查询时授权的证书记录精确匹配。 */
    public byte[] generatePdfForGuest(Long grantId, String school, String numberId, String realName) throws Exception {
        return generatePdf(grantId, grant -> {
            if (!StringUtils.hasText(school) || !StringUtils.hasText(numberId) || !StringUtils.hasText(realName)) return false;
            if (grant.getUserAccount() == null || !grant.getUserAccount().trim().equals(numberId.trim())) return false;
            return StringUtils.hasText(grant.getSchoolName())
                    && grant.getSchoolName().trim().equals(school.trim())
                    && StringUtils.hasText(grant.getRealName())
                    && grant.getRealName().trim().equals(realName.trim());
        });
    }

    public byte[] generatePdf(Long grantId, java.util.function.Predicate<CertificateGrant> ownerCheck) throws Exception {
        try {
            CertificateGrant grant = grantMapper.selectById(grantId);
            if (grant == null) throw new RuntimeException("证书记录不存在 (ID: " + grantId + ")");

            // 权限校验
            if (!ownerCheck.test(grant)) {
                throw new CertificateAccessDeniedException();
            }

            CertificateType template = typeMapper.selectById(grant.getCertificateTypeId());
            if (template == null) throw new RuntimeException("证书模板不存在 (TypeID: " + grant.getCertificateTypeId() + ")");

            CertificateIssue issuedCertificate = grant.getIssueId() == null ? null : issueMapper.selectById(grant.getIssueId());
            if (grant.getIssueId() != null && issuedCertificate == null) throw new RuntimeException("证书签发记录不存在");
            if (issuedCertificate != null && !"active".equals(issuedCertificate.getStatus())) {
                throw new RuntimeException("证书已撤销，不能下载");
            }
            Long templateFileId = template.getUrlId();
            if (templateFileId == null) throw new RuntimeException("模板未绑定PDF文件 (FileID为空)");
            FileSystem fileInfo = fileMapper.selectById(templateFileId);
            if (fileInfo == null) throw new RuntimeException("模板文件记录丢失 (FileID: " + templateFileId + ")");

            File pdfTemplateFile = templateRenderService.resolvePdfFile(templateFileId);

            if (grant.getIssueId() != null) {
                List<CertificateIssueMember> issueMembers = issueMemberMapper.selectList(
                        new QueryWrapper<CertificateIssueMember>().eq("issue_id", issuedCertificate.getIssueId()).orderByAsc("sort_order", "issue_member_id"));
                if (!StringUtils.hasText(template.getTemplateSchema())) throw new RuntimeException("当前证书模板未配置占位符");
                if (template.getTime() != null) issuedCertificate.setIssueDate(template.getTime());
                return templateRenderService.renderIssuedCertificate(pdfTemplateFile, template.getTemplateSchema(), issuedCertificate, issueMembers);
            }

            // Authorization, record existence and revocation checks above always run,
            // including when preview and download reuse the same generated bytes.
            String cacheKey = CertificatePdfCache.key(pdfTemplateFile,
                    String.valueOf(template.getFontSize()), grant.getCustomName(), grant.getRealName());
            return legacyPdfCache.get(cacheKey, () -> {
            try (PDDocument pdfDoc = PDDocument.load(pdfTemplateFile)) {
                PDAcroForm acroForm = pdfDoc.getDocumentCatalog().getAcroForm();
                if (acroForm == null) throw new RuntimeException("PDF模板无表单域 (AcroForm is null)");

                TrueTypeCollection ttcToClose = null;
                try (InputStream fontStream = getClass().getClassLoader().getResourceAsStream("fonts/NotoSansSC.ttf")) {
                    if (fontStream == null) throw new IllegalStateException("缺少开源中文字体资源");
                    PDType0Font font = PDType0Font.load(pdfDoc, fontStream, true);

                    String realName = null;
                    // Use custom name if available (for team certificates)
                    if (StringUtils.hasText(grant.getCustomName())) {
                        realName = grant.getCustomName();
                    } else if (StringUtils.hasText(grant.getRealName())) {
                        realName = grant.getRealName();
                    }

                    if (realName == null) realName = "同学";
                    String displayText = realName + " 同学：";
                    
                    // Sanitize text to avoid 500 error if glyphs are missing in the specific font file
                    if (!canDisplay(font, displayText)) {
                        org.slf4j.LoggerFactory.getLogger(CertificateGrantService.class).warn("操作失败，请检查请求参数和服务配置");
                        displayText = sanitizeText(font, displayText);
                    } else {

                    }

                    // Try multiple field names
                    PDField field = acroForm.getField("Name");
                    if (field == null) field = acroForm.getField("name");
                    if (field == null) field = acroForm.getField("姓名");
                    
                    // Reject templates that do not expose a supported name field
                    if (field == null) {
                        throw new IllegalArgumentException("PDF 模板缺少姓名字段 Name、name 或 姓名");
                    }

                    if (field != null) {
                        PDResources resources = acroForm.getDefaultResources();
                        if (resources == null) {
                            resources = new PDResources();
                            acroForm.setDefaultResources(resources);
                        }
                        COSName fontName = resources.add(font);
                        float fontSize = (template.getFontSize() != null && template.getFontSize() > 0) ? template.getFontSize() : 24f;
                        
                        // New Logic: Draw text directly using ContentStream to avoid form issues
                        // 1. Get position from the first widget
                        List<PDAnnotationWidget> widgets = field.getWidgets();
                        if (widgets != null && !widgets.isEmpty()) {
                            PDAnnotationWidget widget = widgets.get(0);
                            PDRectangle rect = widget.getRectangle();
                            PDPage page = widget.getPage(); 
                            if (page == null) {
                                // Fallback: try to find page 0
                                page = pdfDoc.getPage(0); 
                            }
                            
                            // Calculate centered position (vertical)
                            // Note: PDFBox Y coordinates start from bottom-left
                            // Approximate text height based on font size
                            float textHeight = fontSize * 0.7f; 
                            float y = rect.getLowerLeftY() + (rect.getHeight() - textHeight) / 2;
                            float x = rect.getLowerLeftX() + 2; // small padding
                            
                            
                            try (PDPageContentStream contentStream = new PDPageContentStream(pdfDoc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                                contentStream.beginText();
                                contentStream.setFont(font, fontSize);
                                contentStream.setNonStrokingColor(0, 0, 0); // Black
                                contentStream.newLineAtOffset(x, y);
                                
                                try {
                                    contentStream.showText(displayText);
                                } catch (Exception e) {
                                    org.slf4j.LoggerFactory.getLogger(CertificateGrantService.class).warn("操作失败，请检查请求参数和服务配置");
                                    try {
                                        String safeText = sanitizeText(font, displayText);

                                        contentStream.showText(safeText);
                                    } catch (Exception ex2) {
                                        org.slf4j.LoggerFactory.getLogger(CertificateGrantService.class).warn("操作失败，请检查请求参数和服务配置");
                                        try {
                                             contentStream.showText(" ");
                                        } catch (Exception ignored) {}
                                    }
                                }
                                contentStream.endText();
                            }
                            
                            // Remove the widget annotation so the empty form field doesn't cover our text
                            // Or simply set it to ReadOnly and Flatten (flatten usually merges form content, but if form is empty it might be fine)
                            // A safer way is to remove the annotation from the page
                            try {
                                List<org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation> annotations = page.getAnnotations();
                                annotations.remove(widget);
                                // Also remove the field from AcroForm to be clean
                                // acroForm.getFields().remove(field); // Optional
                            } catch (Exception e) {
                                org.slf4j.LoggerFactory.getLogger(CertificateGrantService.class).warn("操作失败，请检查请求参数和服务配置");
                            }
                        } else {
                            // Fallback to old method if no widgets found (unlikely for a visible field)
                            org.slf4j.LoggerFactory.getLogger(CertificateGrantService.class).warn("操作失败，请检查请求参数和服务配置");
                            if (field instanceof PDVariableText) {
                                ((PDVariableText) field).setDefaultAppearance("/" + fontName.getName() + " " + fontSize + " Tf 0 0 0 rg");
                            }
                            try {
                                field.setValue(displayText);
                            } catch (Exception e) {
                                 // ... error handling similar to above ...
                            }
                        }
                        
                        field.setReadOnly(true);
                    }
                    
                    removeFormFields(pdfDoc);
                    return templateRenderService.renderImageOnlyPdf(pdfDoc);
                } finally {
                    if (ttcToClose != null) {
                        try { ttcToClose.close(); } catch (Exception ignored) {}
                    }
                }
            }
            });
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(CertificateGrantService.class).warn("操作失败，请检查请求参数和服务配置");
            org.slf4j.LoggerFactory.getLogger(CertificateGrantService.class).warn("操作失败，请检查请求参数和服务配置");
            throw e; // Re-throw to be handled by controller (resulting in 500 but with logs)
        }
    }

    /** 旧版模板使用表单域定位文字；签发后必须移除所有可编辑控件。 */
    private void removeFormFields(PDDocument document) throws IOException {
        for (PDPage page : document.getPages()) {
            page.getAnnotations().removeIf(annotation -> annotation instanceof PDAnnotationWidget);
        }
        document.getDocumentCatalog().setAcroForm(null);
    }

    private boolean canDisplay(PDFont font, String text) {
        try {
            font.encode(text);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String sanitizeText(PDFont font, String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            try {
                font.encode(String.valueOf(c));
                sb.append(c);
            } catch (Exception e) {
                // Replace unsupported char with standard replacement or space
                sb.append("?"); 

            }
        }
        return sb.toString();
    }

    public static class CertificateAccessDeniedException extends RuntimeException {
        public CertificateAccessDeniedException() {
            super("无权查看此证书");
        }
    }
}
