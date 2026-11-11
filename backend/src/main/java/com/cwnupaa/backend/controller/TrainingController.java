package com.cwnupaa.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.dto.TrainingDTO;
import com.cwnupaa.backend.entity.*;
import com.cwnupaa.backend.mapper.*;
import com.cwnupaa.backend.service.EmailService;
import com.cwnupaa.backend.service.FileAccessPolicy;
import com.cwnupaa.backend.utils.SecurityUtils;
import com.cwnupaa.backend.utils.StudentNumberValidator;
import com.cwnupaa.backend.vo.TrainingSignupVO;
import com.cwnupaa.backend.vo.TrainingTeacherVO;
import com.cwnupaa.backend.vo.TrainingVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/training")
public class TrainingController {
    private static final String DEFAULT_PUBLISHER_NAME = "BESTI ACM";

    @Autowired
    private TrainingMapper trainingMapper;
    @Autowired
    private TrainingTeacherMapper trainingTeacherMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UniversityMapper universityMapper;
    @Autowired
    private TermMapper termMapper;
    @Autowired
    private TrainingSignupMapper trainingSignupMapper;
    @Autowired
    private EmailService emailService;
    @Autowired
    private FileRelationMapper fileRelationMapper;
    @Autowired
    private FileSystemMapper fileMapper;
    @Autowired
    private FileAccessPolicy fileAccessPolicy;

    @GetMapping("/list")
    public Result<List<TrainingVO>> list() {
        List<Training> trainings = trainingMapper.selectList(new QueryWrapper<Training>().orderByDesc("signup_start"));
        List<TrainingVO> voList = trainings.stream().map(t -> {
            TrainingVO vo = new TrainingVO();
            BeanUtils.copyProperties(t, vo);
            populateTeachers(t, vo);
            Term term = termMapper.selectById(t.getTermId());
            if (term != null) vo.setTermName(term.getTermName());
            populatePublisherName(t, vo);
            Long count = trainingSignupMapper.countApproved(t.getTrainId());
            vo.setSignupCount(count);
            
            // 计算业务状态
            LocalDateTime now = LocalDateTime.now();
            if (now.isBefore(t.getSignupStart())) {
                vo.setState("pending"); // 报名未开始
            } else if (now.isBefore(t.getSignupEnd())) {
                vo.setState("registering"); // 报名进行中
            } else {
                vo.setState("ended"); // 报名已结束
            }
            
            return vo;
        }).collect(Collectors.toList());
        return Result.success(voList);
    }

    @GetMapping("/detail/{id}")
    public Result<TrainingVO> detail(@PathVariable Long id) {
        Training training = trainingMapper.selectById(id);
        if (training == null) return Result.error("培训不存在");

        TrainingVO vo = new TrainingVO();
        BeanUtils.copyProperties(training, vo);
        populateTeachers(training, vo);
        Term term = termMapper.selectById(training.getTermId());
        if (term != null) vo.setTermName(term.getTermName());
        populatePublisherName(training, vo);

        vo.setSignupCount(trainingSignupMapper.countApproved(id));

        // 计算业务状态
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(training.getSignupStart())) {
            vo.setState("pending"); // 报名未开始
        } else if (now.isBefore(training.getSignupEnd())) {
            vo.setState("registering"); // 报名进行中
        } else {
            vo.setState("ended"); // 报名已结束
        }

        List<FileRelation> relations = fileRelationMapper.selectList(new QueryWrapper<FileRelation>().eq("biz_type", "training").eq("business_id", id));
        if (!relations.isEmpty()) {
            List<Long> fileIds = relations.stream().map(FileRelation::getFileId).collect(Collectors.toList());
            List<FileSystem> files = fileMapper.selectBatchIds(fileIds);
            vo.setAttachments(files.stream().filter(f -> fileAccessPolicy.canAccess(f, null)).toList());
        }

        return Result.success(vo);
    }

    @PostMapping("/publish")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> publish(@RequestBody TrainingDTO dto, HttpServletRequest request) {
        Long currentUserId = SecurityUtils.currentUserId(request);
        if (currentUserId == null) return Result.error("未登录或会话已失效");
        if (dto == null) return Result.error("参数不能为空");
        dto.setPublisherId(currentUserId);
        Result<String> validation = validateTrainingDTO(dto, false);
        if (validation != null) return validation;
        if (!checkPublishPermission(currentUserId)) return Result.error("无权发布培训");
        String fileError = fileAccessPolicy.validatePublicFiles(dto.getAttachmentIds(), SecurityUtils.currentUser(request));
        if (fileError == null) fileError = fileAccessPolicy.validatePublicContent(dto.getDescription(), SecurityUtils.currentUser(request));
        if (fileError != null) return Result.error(fileError);

        Training training = new Training();
        BeanUtils.copyProperties(dto, training);
        trainingMapper.insert(training);
        replaceTrainingTeachers(training.getTrainId(), dto.getTeacherIds());

        if (dto.getAttachmentIds() != null && !dto.getAttachmentIds().isEmpty()) {
            for (Long fileId : dto.getAttachmentIds()) {
                if (fileId == null) continue;
                FileRelation fr = new FileRelation();
                fr.setFileId(fileId);
                fr.setBizType("training");
                fr.setBusinessId(training.getTrainId());
                fileRelationMapper.insert(fr);
            }
        }

        dto.getTeacherIds().forEach(teacherId -> sendNotificationToTeacher(teacherId, training, "new"));
        return Result.success("发布成功");
    }

    @PostMapping("/update")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> update(@RequestBody TrainingDTO dto, HttpServletRequest request) {
        if (dto == null || dto.getTrainId() == null) return Result.error("ID不能为空");
        Long currentUserId = SecurityUtils.currentUserId(request);
        if (currentUserId == null) return Result.error("未登录或会话已失效");

        Result<String> validation = validateTrainingDTO(dto, true);
        if (validation != null) return validation;
        Training oldTraining = trainingMapper.selectById(dto.getTrainId());
        if (oldTraining == null) return Result.error("培训不存在");

        if (!checkManagePermission(currentUserId, oldTraining)) {
            return Result.error("无权管理此培训");
        }

        dto.setPublisherId(oldTraining.getPublisherId());
        String fileError = fileAccessPolicy.validatePublicFiles(dto.getAttachmentIds(), SecurityUtils.currentUser(request));
        if (fileError == null) fileError = fileAccessPolicy.validatePublicContent(dto.getDescription(), SecurityUtils.currentUser(request));
        if (fileError != null) return Result.error(fileError);
        List<Long> oldTeacherIds = getTeacherIds(oldTraining);
        List<Long> newTeacherIds = dto.getTeacherIds();
        Training oldTrainingState = new Training();
        BeanUtils.copyProperties(oldTraining, oldTrainingState);

        BeanUtils.copyProperties(dto, oldTraining);
        trainingMapper.updateById(oldTraining);
        replaceTrainingTeachers(oldTraining.getTrainId(), newTeacherIds);

        List<Long> newFileIds = dto.getAttachmentIds() == null ? new ArrayList<>() : dto.getAttachmentIds();

        fileRelationMapper.delete(new QueryWrapper<FileRelation>().eq("biz_type", "training").eq("business_id", dto.getTrainId()));

        for (Long fileId : newFileIds) {
            if (fileId == null) continue;
            FileRelation fr = new FileRelation();
            fr.setFileId(fileId);
            fr.setBizType("training");
            fr.setBusinessId(oldTraining.getTrainId());
            fileRelationMapper.insert(fr);
        }

        Set<Long> oldTeacherSet = new LinkedHashSet<>(oldTeacherIds);
        Set<Long> newTeacherSet = new LinkedHashSet<>(newTeacherIds);
        oldTeacherSet.stream().filter(teacherId -> !newTeacherSet.contains(teacherId))
                .forEach(teacherId -> sendNotificationToTeacher(teacherId, oldTraining, "remove"));
        newTeacherSet.stream().filter(teacherId -> !oldTeacherSet.contains(teacherId))
                .forEach(teacherId -> sendNotificationToTeacher(teacherId, oldTraining, "new"));
        newTeacherSet.stream().filter(oldTeacherSet::contains)
                .forEach(teacherId -> sendNotificationToTeacher(teacherId, oldTraining, "update"));

        if (Boolean.TRUE.equals(dto.getNotifyParticipants())) {
            new Thread(() -> checkAndSendTrainingTimeChangeNotification(oldTrainingState, oldTraining)).start();
        }

        return Result.success("更新成功");
    }

    @PostMapping("/delete")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> delete(@RequestBody TrainingDTO dto, HttpServletRequest request) {
        Long trainId = dto.getTrainId();
        Long userId = SecurityUtils.currentUserId(request);
        if (userId == null) return Result.error("未登录或会话已失效");

        Training training = trainingMapper.selectById(trainId);
        if (training == null) return Result.error("培训不存在");

        if (!checkManagePermission(userId, training)) {
            return Result.error("无权删除");
        }

        fileRelationMapper.delete(new QueryWrapper<FileRelation>().eq("biz_type", "training").eq("business_id", trainId));
        trainingTeacherMapper.delete(new QueryWrapper<TrainingTeacher>().eq("train_id", trainId));

        trainingMapper.deleteById(trainId);
        return Result.success("删除成功");
    }

    @GetMapping("/signup-status")
    public Result<String> getSignupStatus(@RequestParam Long trainId, HttpServletRequest request) {
        Long currentUserId = SecurityUtils.currentUserId(request);
        if (currentUserId == null) return Result.error("未登录或会话已失效");
        Long userId = currentUserId;
        TrainingSignup signup = trainingSignupMapper.selectOne(new QueryWrapper<TrainingSignup>().eq("train_id", trainId).eq("user_id", userId));
        return Result.success(signup == null ? "none" : signup.getStatus());
    }

    @PostMapping("/signup")
    @Transactional(rollbackFor = Exception.class)
    public synchronized Result<String> signup(@RequestBody Map<String, Long> params, HttpServletRequest request) {
        Long trainId = params.get("trainId");
        Long userId = SecurityUtils.currentUserId(request);
        if (userId == null) return Result.error("未登录或会话已失效");

        Users user = userMapper.selectById(userId);
        if (user == null) return Result.error("用户不存在");

        if (!"normal".equals(user.getAccountStatus())) {
            return Result.error("您的账号状态异常，无法报名培训");
        }

        Training training = trainingMapper.selectById(trainId);
        if (training == null) return Result.error("培训不存在");

        if (LocalDateTime.now().isBefore(training.getSignupStart())) return Result.error("报名未开始");
        if (!LocalDateTime.now().isBefore(training.getSignupEnd())) return Result.error("报名已截止");

        Long count = trainingSignupMapper.countApproved(trainId);
        if (count >= training.getMaxCount()) return Result.error("报名人数已满");

        TrainingSignup signup = trainingSignupMapper.selectOne(new QueryWrapper<TrainingSignup>().eq("train_id", trainId).eq("user_id", userId));

        if (signup == null) {
            signup = new TrainingSignup();
            signup.setTrainId(trainId);
            signup.setUserId(userId);
            signup.setStatus("approved");
            signup.setCreateTime(LocalDateTime.now());
            trainingSignupMapper.insert(signup);
        } else {
            if ("approved".equals(signup.getStatus()) || "pending".equals(signup.getStatus())) {
                return Result.error("您已报名");
            }
            // if ("rejected".equals(signup.getStatus())) {
            //     return Result.error("您的报名已被拒绝，无法再次报名");
            // }
            signup.setStatus("approved");
            signup.setCreateTime(LocalDateTime.now());
            trainingSignupMapper.updateById(signup);
        }

        emailService.sendTrainingSignupStatusEmail(user.getEmail(), user.getRealName(), training.getName(), "approved", null);
        return Result.success("报名成功");
    }

    @PostMapping("/cancel")
    public Result<String> cancelSignup(@RequestBody Map<String, Long> params, HttpServletRequest request) {
        Long trainId = params.get("trainId");
        Long userId = SecurityUtils.currentUserId(request);
        if (userId == null) return Result.error("未登录或会话已失效");

        Training training = trainingMapper.selectById(trainId);
        if (training == null) return Result.error("培训不存在");
        
        if (!LocalDateTime.now().isBefore(training.getSignupEnd())) {
            return Result.error("报名已结束，无法取消报名");
        }

        TrainingSignup signup = trainingSignupMapper.selectOne(new QueryWrapper<TrainingSignup>().eq("train_id", trainId).eq("user_id", userId));

        if (signup == null || "cancelled".equals(signup.getStatus())) {
            return Result.error("未找到有效的报名记录");
        }

        signup.setStatus("cancelled");
        trainingSignupMapper.updateById(signup);

        Users user = userMapper.selectById(userId);
        if (user != null && training != null) {
            emailService.sendTrainingSignupStatusEmail(user.getEmail(), user.getRealName(), training.getName(), "cancelled", null);
        }

        return Result.success("已取消报名");
    }

    @GetMapping("/admin/signups")
    public Result<Page<TrainingSignupVO>> listSignups(@RequestParam Long trainId, @RequestParam(defaultValue = "1") Integer current, @RequestParam(defaultValue = "10") Integer size, @RequestParam(required = false) String keyword, @RequestParam(required = false) String status) {

        Page<TrainingSignupVO> page = new Page<>(SecurityUtils.normalizePage(current), SecurityUtils.clampPageSize(size));
        return Result.success(trainingSignupMapper.selectPageVo(page, trainId, keyword, status));
    }

    @PostMapping("/admin/update-status")
    public Result<String> updateStatus(@RequestBody Map<String, Object> params) {
        if (params.get("signupId") == null || params.get("status") == null) {
            return Result.error("参数不完整");
        }
        Long signupId = Long.valueOf(params.get("signupId").toString());
        String status = params.get("status").toString();
        String reason = params.get("reason") != null ? params.get("reason").toString() : null;

        TrainingSignup signup = trainingSignupMapper.selectById(signupId);
        if (signup == null) return Result.error("记录不存在");

        Training training = trainingMapper.selectById(signup.getTrainId());
        if (!isValidAdminSignupStatus(status)) return Result.error("状态不合法");

        signup.setStatus(status);
        trainingSignupMapper.updateById(signup);

        Users user = userMapper.selectById(signup.getUserId());
        if (user != null && training != null) {
            emailService.sendTrainingSignupStatusEmail(user.getEmail(), user.getRealName(), training.getName(), status, reason);
        }

        return Result.success("状态已更新");
    }

    @PostMapping("/admin/batch-update-status")
    public Result<String> batchUpdateStatus(@RequestBody Map<String, Object> params) {
        List<Long> ids = parseLongIdList(params.get("signupIds"));
        if (params.get("status") == null) return Result.error("状态不能为空");
        String status = params.get("status").toString();
        String reason = params.get("reason") != null ? params.get("reason").toString() : null;

        if (ids == null || ids.isEmpty()) {
            return Result.error("未选择任何记录");
        }
        if (!isValidAdminSignupStatus(status)) return Result.error("状态不合法");

        for (Long id : ids) {
            TrainingSignup signup = trainingSignupMapper.selectById(id);
            if (signup != null) {
                signup.setStatus(status);
                trainingSignupMapper.updateById(signup);
                
                Users user = userMapper.selectById(signup.getUserId());
                Training training = trainingMapper.selectById(signup.getTrainId());
                if (user != null && training != null) {
                    emailService.sendTrainingSignupStatusEmail(user.getEmail(), user.getRealName(), training.getName(), status, reason);
                }
            }
        }
        return Result.success("批量操作成功");
    }

    @PostMapping("/admin/batch-import")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> batchImport(@RequestBody Map<String, Object> params) {
        if (params.get("trainId") == null || params.get("content") == null) {
            return Result.error("参数不完整");
        }
        Long trainId = Long.valueOf(params.get("trainId").toString());
        String tsvContent = params.get("content").toString();
        boolean preview = Boolean.parseBoolean(String.valueOf(params.getOrDefault("preview", false)));

        Training training = trainingMapper.selectById(trainId);
        if (training == null) return Result.error("培训不存在");

        String[] lines = tsvContent.split("\\r?\\n");
        int successCount = 0;
        int failCount = 0;
        List<Map<String, String>> failedList = new ArrayList<>();
        List<Map<String, String>> successList = new ArrayList<>();

        for (String line : lines) {
            String originalLine = line;
            line = line.trim();
            if (StringUtils.isEmpty(line)) continue;

            String[] parts = parseImportLine(line);
            if (parts.length < 3) {
                failCount++;
                Map<String, String> failMap = new HashMap<>();
                failMap.put("content", originalLine);
                failMap.put("reason", "格式错误(需包含学校、学号和姓名)");
                failedList.add(failMap);
                continue;
            }

            String universityName = parts[0].trim();
            String numberId = parts[1].trim();
            String realName = parts[2].trim();

            Users user = StudentNumberValidator.findByUniversityNameAndNumber(
                    userMapper, universityMapper, universityName, numberId);
            if (user == null || !user.getRealName().equals(realName)) {
                failCount++;
                Map<String, String> failMap = new HashMap<>();
                failMap.put("schoolName", universityName);
                failMap.put("numberId", numberId);
                failMap.put("realName", realName);
                failMap.put("reason", user == null ? "学校与学号对应的用户不存在" : "姓名不匹配");
                failedList.add(failMap);
                continue;
            }

            TrainingSignup signup = trainingSignupMapper.selectOne(new QueryWrapper<TrainingSignup>().eq("train_id", trainId).eq("user_id", user.getUserId()));

            if (!preview) {
                if (signup == null) {
                    signup = new TrainingSignup();
                    signup.setTrainId(trainId);
                    signup.setUserId(user.getUserId());
                    signup.setStatus("approved");
                    signup.setCreateTime(LocalDateTime.now());
                    trainingSignupMapper.insert(signup);
                } else {
                    signup.setStatus("approved");
                    trainingSignupMapper.updateById(signup);
                }
                emailService.sendTrainingSignupStatusEmail(user.getEmail(), user.getRealName(), training.getName(), "approved", null);
            }
            Map<String, String> successMap = new HashMap<>();
            successMap.put("schoolName", universityName);
            successMap.put("numberId", numberId);
            successMap.put("realName", realName);
            successMap.put("reason", signup == null ? "将新增报名" : "将更新为已通过");
            successList.add(successMap);
            successCount++;
        }

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("success", successCount);
        resultMap.put("failure", failCount);
        resultMap.put("failedList", failedList);
        resultMap.put("successList", successList);

        return Result.success(resultMap);
    }

    private boolean isValidAdminSignupStatus(String status) {
        return "approved".equals(status)
                || "rejected".equals(status)
                || "cancelled".equals(status)
                || "pending".equals(status);
    }

    private Result<String> validateTrainingDTO(TrainingDTO dto, boolean updating) {
        if (dto == null) return Result.error("参数不能为空");
        if (!StringUtils.hasText(dto.getName()) || dto.getName().trim().length() > 100) return Result.error("培训名称不能为空且不能超过100字");
        dto.setName(dto.getName().trim());
        if (dto.getTermId() == null) return Result.error("学期不能为空");
        List<Long> teacherIds = dto.getTeacherIds();
        if ((teacherIds == null || teacherIds.isEmpty()) && dto.getTeacherId() != null) {
            teacherIds = List.of(dto.getTeacherId());
        }
        if (teacherIds == null) teacherIds = new ArrayList<>();
        teacherIds = teacherIds.stream().filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (teacherIds.isEmpty()) return Result.error("授课教师不能为空");
        if (teacherIds.size() > 20) return Result.error("授课教师不能超过20人");
        for (Long teacherId : teacherIds) {
            if (userMapper.selectById(teacherId) == null) return Result.error("指定的授课教师不存在");
        }
        dto.setTeacherIds(teacherIds);
        dto.setTeacherId(teacherIds.get(0));
        String publisherName = StringUtils.hasText(dto.getPublisherName()) ? dto.getPublisherName().trim() : DEFAULT_PUBLISHER_NAME;
        if (publisherName.length() > 100) return Result.error("发布者不能超过100字");
        dto.setPublisherName(publisherName);
        if (dto.getSignupStart() == null || dto.getSignupEnd() == null || dto.getTrainStart() == null || dto.getTrainEnd() == null) {
            return Result.error("时间信息不能为空");
        }
        if (!dto.getSignupStart().isBefore(dto.getSignupEnd())) return Result.error("报名开始时间必须早于报名结束时间");
        if (!dto.getTrainStart().isBefore(dto.getTrainEnd())) return Result.error("培训开始时间必须早于培训结束时间");
        if (dto.getMaxCount() == null || dto.getMaxCount() < 1 || dto.getMaxCount() > 10000) return Result.error("报名人数上限不合法");
        if (dto.getDescription() != null && dto.getDescription().length() > 10000) return Result.error("培训介绍不能超过10000字");
        return null;
    }

    private List<Long> parseLongIdList(Object rawIds) {
        if (!(rawIds instanceof List<?> rawList)) return new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        for (Object rawId : rawList) {
            if (rawId == null) continue;
            try {
                ids.add(Long.valueOf(rawId.toString()));
            } catch (NumberFormatException ignored) {
            }
        }
        return ids;
    }

    private String[] parseImportLine(String line) {
        if (line.contains("\t")) return line.split("\t");
        return line.split("[,，\\s]+");
    }

    @GetMapping("/admin/export")
    public void exportSignups(@RequestParam Long trainId, HttpServletResponse response) {
        try {
            Training training = trainingMapper.selectById(trainId);
            List<TrainingSignupVO> list = trainingSignupMapper.selectExportList(trainId);

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("报名名单");

            Row headerRow = sheet.createRow(0);
            String[] headers = {"学校", "姓名", "学号/工号", "学院", "专业", "行政班级", "报名时间", "报名状态"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            Map<String, String> statusMap = Map.of("approved", "已报名", "cancelled", "已取消", "rejected", "已拒绝", "pending", "待审核");

            int rowNum = 1;
            for (TrainingSignupVO vo : list) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(vo.getUniversityName());
                row.createCell(1).setCellValue(vo.getRealName());
                row.createCell(2).setCellValue(vo.getNumberId());
                row.createCell(3).setCellValue(vo.getSchoolName());
                row.createCell(4).setCellValue(vo.getMajorName());
                row.createCell(5).setCellValue(vo.getClassName());
                row.createCell(6).setCellValue(vo.getCreateTime().format(dtf));
                row.createCell(7).setCellValue(statusMap.getOrDefault(vo.getStatus(), vo.getStatus()));
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            String fileName = URLEncoder.encode(training.getName() + "_报名名单.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + fileName);

            workbook.write(response.getOutputStream());
            workbook.close();
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(TrainingController.class).warn("操作失败，请检查请求参数和服务配置");
        }
    }

    private boolean checkPublishPermission(Long userId) {
        com.cwnupaa.backend.vo.UserProfileVO actor = new com.cwnupaa.backend.vo.UserProfileVO();
        actor.setUserId(userId);
        return fileAccessPolicy.hasPermission(actor, "manage_training");
    }

    private boolean checkManagePermission(Long currentUserId, Training training) {
        com.cwnupaa.backend.vo.UserProfileVO actor = new com.cwnupaa.backend.vo.UserProfileVO();
        actor.setUserId(currentUserId);
        if (!fileAccessPolicy.hasPermission(actor, "manage_training")) return false;
        return fileAccessPolicy.isRoot(actor) || currentUserId.equals(training.getPublisherId())
                || getTeacherIds(training).contains(currentUserId);
    }

    private List<Long> getTeacherIds(Training training) {
        List<Long> teacherIds = trainingTeacherMapper.selectList(new QueryWrapper<TrainingTeacher>()
                        .eq("train_id", training.getTrainId())
                        .orderByAsc("sort_order", "training_teacher_id"))
                .stream()
                .map(TrainingTeacher::getTeacherId)
                .distinct()
                .collect(Collectors.toList());
        if (teacherIds.isEmpty() && training.getTeacherId() != null) {
            teacherIds.add(training.getTeacherId());
        }
        return teacherIds;
    }

    private void replaceTrainingTeachers(Long trainId, List<Long> teacherIds) {
        trainingTeacherMapper.delete(new QueryWrapper<TrainingTeacher>().eq("train_id", trainId));
        for (int i = 0; i < teacherIds.size(); i++) {
            TrainingTeacher relation = new TrainingTeacher();
            relation.setTrainId(trainId);
            relation.setTeacherId(teacherIds.get(i));
            relation.setSortOrder(i);
            trainingTeacherMapper.insert(relation);
        }
    }

    private void populateTeachers(Training training, TrainingVO vo) {
        List<TrainingTeacherVO> teachers = new ArrayList<>();
        for (Long teacherId : getTeacherIds(training)) {
            Users teacher = userMapper.selectById(teacherId);
            if (teacher == null) continue;
            TrainingTeacherVO teacherVO = new TrainingTeacherVO();
            teacherVO.setTeacherId(teacherId);
            teacherVO.setTeacherName(teacher.getRealName());
            teacherVO.setTeacherAccount(teacher.getUserAccount());
            teachers.add(teacherVO);
        }
        vo.setTeachers(teachers);
        vo.setTeacherName(teachers.stream().map(TrainingTeacherVO::getTeacherName).collect(Collectors.joining("、")));
        if (!teachers.isEmpty()) vo.setTeacherAccount(teachers.get(0).getTeacherAccount());
    }

    private void populatePublisherName(Training training, TrainingVO vo) {
        if (StringUtils.hasText(training.getPublisherName())) {
            vo.setPublisherName(training.getPublisherName());
            return;
        }
        Users publisher = training.getPublisherId() == null ? null : userMapper.selectById(training.getPublisherId());
        vo.setPublisherName(publisher != null && StringUtils.hasText(publisher.getRealName())
                ? publisher.getRealName()
                : DEFAULT_PUBLISHER_NAME);
    }

    private void sendNotificationToTeacher(Long teacherId, Training training, String type) {
        Users teacher = userMapper.selectById(teacherId);
        if (teacher == null) return;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("MM-dd HH:mm");
        String timeRange = training.getTrainStart().format(dtf) + " 至 " + training.getTrainEnd().format(dtf);
        emailService.sendTrainingTeacherNotification(teacher.getEmail(), teacher.getRealName(), training.getName(), timeRange, type);
    }

    private void checkAndSendTrainingTimeChangeNotification(Training oldTraining, Training newTraining) {
        boolean timeChanged = isTimeChanged(oldTraining.getTrainStart(), newTraining.getTrainStart()) || isTimeChanged(oldTraining.getTrainEnd(), newTraining.getTrainEnd());
        boolean signupTimeChanged = isTimeChanged(oldTraining.getSignupStart(), newTraining.getSignupStart()) || isTimeChanged(oldTraining.getSignupEnd(), newTraining.getSignupEnd());
        if (!timeChanged && !signupTimeChanged) return;

        Map<String, String> targets = getTrainingParticipants(newTraining.getTrainId());
        if (targets.isEmpty()) return;

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        StringBuilder sb = new StringBuilder();
        if (timeChanged) {
            sb.append("课程时间调整为：")
                    .append(newTraining.getTrainStart() != null ? newTraining.getTrainStart().format(dtf) : "待定")
                    .append(" 至 ")
                    .append(newTraining.getTrainEnd() != null ? newTraining.getTrainEnd().format(dtf) : "待定")
                    .append("。");
        }
        if (signupTimeChanged) {
            if (sb.length() > 0) sb.append("<br/>");
            sb.append("报名时间调整为：")
                    .append(newTraining.getSignupStart() != null ? newTraining.getSignupStart().format(dtf) : "待定")
                    .append(" 至 ")
                    .append(newTraining.getSignupEnd() != null ? newTraining.getSignupEnd().format(dtf) : "待定")
                    .append("。");
        }

        emailService.sendTrainingTimeChangeEmail(targets, newTraining.getName(), sb.toString());
    }

    private boolean isTimeChanged(LocalDateTime t1, LocalDateTime t2) {
        if (t1 == null && t2 == null) return false;
        if (t1 == null || t2 == null) return true;
        return !t1.isEqual(t2);
    }

    private Map<String, String> getTrainingParticipants(Long trainId) {
        Map<String, String> targets = new HashMap<>();
        List<TrainingSignup> list = trainingSignupMapper.selectList(new QueryWrapper<TrainingSignup>().eq("train_id", trainId).eq("status", "approved"));
        for (TrainingSignup signup : list) {
            Users user = userMapper.selectById(signup.getUserId());
            if (user != null && StringUtils.hasText(user.getEmail())) {
                targets.put(user.getEmail(), user.getRealName());
            }
        }
        return targets;
    }
}
