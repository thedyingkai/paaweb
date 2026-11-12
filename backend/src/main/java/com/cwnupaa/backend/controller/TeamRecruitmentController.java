package com.cwnupaa.backend.controller;

import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.service.TeamMembershipService;
import com.cwnupaa.backend.service.RecruitmentExportService;
import com.cwnupaa.backend.service.RecruitmentPrefillService;
import com.cwnupaa.backend.utils.BestiRecordPolicy;
import com.cwnupaa.backend.utils.SecurityUtils;
import com.cwnupaa.backend.vo.UserProfileVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/team-recruitment")
public class TeamRecruitmentController {
    private final JdbcTemplate db;
    private final TeamMembershipService membership;
    public TeamRecruitmentController(JdbcTemplate db, TeamMembershipService membership) { this.db=db; this.membership=membership; }
    public record WindowInput(Long id,Integer version,String title,String description,OffsetDateTime opensAt,OffsetDateTime closesAt,
                              String term,LocalDate memberStart,LocalDate memberEnd,boolean enabled) {}
    public record ApplicationInput(Long windowId,Integer version,String motivation,String experience,String contact,
                                   String gender,String politicalStatus,String email,String campusRank,
                                   String learningContent,String studentWork,String platformAccounts) {}
    public record ExportInput(Long windowId,String status,List<Long> ids,String format) {}
    public record ReviewInput(Integer version,boolean approved,String note,LocalDate memberStart,LocalDate memberEnd) {}
    public record VersionInput(Integer version) {}
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result<String>> handleStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Result.error(e.getStatusCode().value(),e.getReason()));
    }
    private static ResponseStatusException error(HttpStatus status,String message) { return new ResponseStatusException(status,message); }
    private UserProfileVO login(HttpServletRequest request) {
        var user=SecurityUtils.currentUser(request);
        if(user==null) throw error(HttpStatus.UNAUTHORIZED,"请先登录");
        return user;
    }
    private UserProfileVO admin(HttpServletRequest request) {
        var user=login(request);
        if(!SecurityUtils.hasPermission(user,"manage_sys")) throw error(HttpStatus.FORBIDDEN,"需要校队成员管理权限");
        return user;
    }
    private Map<String,Object> one(String sql,Object... args) {
        var rows=db.queryForList(sql,args);
        if(rows.isEmpty()) throw error(HttpStatus.NOT_FOUND,"记录不存在");
        return rows.getFirst();
    }
    private static long number(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
    private static LocalDate date(Object value) { return value==null?null:LocalDate.parse(value.toString()); }
    private static Instant instant(Object value) {
        if(value instanceof Timestamp ts) return ts.toInstant();
        if(value instanceof OffsetDateTime dt) return dt.toInstant();
        return Instant.parse(value.toString());
    }
    static String windowStatus(boolean enabled,Instant open,Instant close,Instant now) {
        if(!enabled) return "paused";
        if(now.isBefore(open)) return "upcoming";
        return now.isBefore(close)?"open":"closed";
    }
    private String windowStatus(Map<String,Object> row) {
        return windowStatus(Boolean.TRUE.equals(row.get("enabled")),instant(row.get("opens_at")),instant(row.get("closes_at")),Instant.now());
    }
    private Map<String,Object> display(Map<String,Object> row) {
        var result=new LinkedHashMap<>(row);
        row.forEach((key,value)->{if(value instanceof Timestamp || value instanceof OffsetDateTime) result.put(key,instant(value).toString());});
        if(row.containsKey("opens_at")) result.put("window_status",windowStatus(row));
        return result;
    }
    private static String text(String value,int max,boolean required) {
        String result=value==null?"":value.trim();
        if(result.length()>max || required && result.isEmpty()) throw error(HttpStatus.BAD_REQUEST,"请完整填写申请表，内容不能超过长度限制");
        return result;
    }
    private Map<String,Object> identity(long userId,boolean lock) {
        return one("SELECT u.user_id,u.account_status,u.real_name,u.number_id,u.email,u.gender,u.phone,un.university_name AS school_name FROM users u "
                +"LEFT JOIN major m ON m.major_id=u.major_id LEFT JOIN school s ON s.school_id=m.school_id "
                +"LEFT JOIN university un ON un.university_id=s.university_id WHERE u.user_id=?"+(lock?" FOR UPDATE OF u":""),userId);
    }
    private void requireIdentity(Map<String,Object> identity) {
        if(!"normal".equals(identity.get("account_status")) || !"北京电子科技学院".equals(identity.get("school_name"))
                || identity.get("real_name")==null || identity.get("number_id")==null
                || identity.get("real_name").toString().isBlank() || identity.get("number_id").toString().isBlank())
            throw error(HttpStatus.FORBIDDEN,"请先完成北京电子科技学院学生身份认证");
    }
    private void version(Map<String,Object> row,Integer version) {
        if(version==null || number(row,"version")!=version) throw error(HttpStatus.CONFLICT,"内容已更新，请刷新后重试");
    }

    @GetMapping("/windows")
    public Result<?> windows() {
        return Result.success(db.queryForList("SELECT id,title,description,opens_at,closes_at,term,member_start,member_end,enabled FROM besti_recruitment_window WHERE enabled=true ORDER BY opens_at DESC LIMIT 30").stream().map(this::display).toList());
    }
    @GetMapping("/me")
    public Result<?> me(HttpServletRequest request) {
        var user=login(request);
        var identity=identity(user.getUserId(),false);
        var rows=db.queryForList("SELECT a.*,w.title,w.opens_at,w.closes_at,w.enabled,w.term,w.member_start,w.member_end FROM besti_team_application a JOIN besti_recruitment_window w ON w.id=a.window_id WHERE a.user_id=? ORDER BY a.updated_at DESC",user.getUserId());
        return Result.success(Map.of("identity",identity,"currentMember",membership.isCurrentMember(user.getUserId()),"applications",rows.stream().map(this::display).toList(),
                "prefill","normal".equals(identity.get("account_status"))?RecruitmentPrefillService.load(db,user.getUserId()):Map.of()));
    }
    @PostMapping("/applications")
    @Transactional
    public Result<?> submit(@RequestBody ApplicationInput body,HttpServletRequest request) {
        var user=login(request);
        if(body==null || body.windowId()==null) throw error(HttpStatus.BAD_REQUEST,"请选择申请批次");
        String motivation=text(body.motivation(),2000,true),experience=text(body.experience(),4000,true),contact=text(body.contact(),160,true);
        String political=text(body.politicalStatus(),50,true),rank=text(body.campusRank(),160,true);
        String learning=text(body.learningContent(),4000,true),studentWork=text(body.studentWork(),2000,true),platforms=text(body.platformAccounts(),4000,true);
        var window=one("SELECT * FROM besti_recruitment_window WHERE id=? FOR SHARE",body.windowId());
        if(!"open".equals(windowStatus(window))) throw error(HttpStatus.CONFLICT,"当前不在申请开放时间内，无法提交或修改");
        var identity=identity(user.getUserId(),true); requireIdentity(identity);
        String savedGender=Objects.toString(identity.get("gender"),"");
        String gender=text("1".equals(savedGender)?"男":"2".equals(savedGender)?"女":body.gender(),30,true);
        String savedEmail=Objects.toString(identity.get("email"),"");
        String email=text(savedEmail.isBlank()?body.email():savedEmail,254,true);
        if(!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw error(HttpStatus.BAD_REQUEST,"请填写有效的邮箱地址");
        var existing=db.queryForList("SELECT * FROM besti_team_application WHERE window_id=? AND user_id=? FOR UPDATE",body.windowId(),user.getUserId());
        if(!"open".equals(windowStatus(window))) throw error(HttpStatus.CONFLICT,"申请已截止，无法提交或修改");
        if(membership.isCurrentMember(user.getUserId())) throw error(HttpStatus.CONFLICT,"你已是任期内的校队成员，无需重复申请");
        if(existing.isEmpty()) {
            db.update("INSERT INTO besti_team_application(window_id,user_id,real_name,student_number,school_name,motivation,experience,contact,gender,political_status,email,campus_rank,learning_content,student_work,platform_accounts) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    body.windowId(),user.getUserId(),identity.get("real_name"),identity.get("number_id"),identity.get("school_name"),motivation,experience,contact,gender,political,email,rank,learning,studentWork,platforms);
        } else {
            var row=existing.getFirst(); version(row,body.version());
            if("approved".equals(row.get("status"))) throw error(HttpStatus.CONFLICT,"本批次申请已通过，不能重复提交");
            db.update("UPDATE besti_team_application SET real_name=?,student_number=?,school_name=?,motivation=?,experience=?,contact=?,gender=?,political_status=?,email=?,campus_rank=?,learning_content=?,student_work=?,platform_accounts=?,status='pending',review_note='',reviewed_by=NULL,reviewed_at=NULL,version=version+1,updated_at=now() WHERE id=?",
                    identity.get("real_name"),identity.get("number_id"),identity.get("school_name"),motivation,experience,contact,gender,political,email,rank,learning,studentWork,platforms,row.get("id"));
        }
        return Result.success("申请已提交，请等待审核");
    }
    @PostMapping("/applications/{id}/withdraw")
    @Transactional
    public Result<?> withdraw(@PathVariable long id,@RequestBody VersionInput body,HttpServletRequest request) {
        var user=login(request); var row=one("SELECT * FROM besti_team_application WHERE id=? FOR UPDATE",id);
        if(number(row,"user_id")!=user.getUserId()) throw error(HttpStatus.FORBIDDEN,"只能撤回本人的申请");
        version(row,body.version());
        if(!"pending".equals(row.get("status"))) throw error(HttpStatus.CONFLICT,"仅待审核申请可以撤回");
        db.update("UPDATE besti_team_application SET status='withdrawn',version=version+1,updated_at=now() WHERE id=?",id);
        return Result.success("申请已撤回");
    }
    @GetMapping("/admin/windows")
    public Result<?> adminWindows(HttpServletRequest request) {
        admin(request); return Result.success(db.queryForList("SELECT * FROM besti_recruitment_window ORDER BY opens_at DESC").stream().map(this::display).toList());
    }
    @PostMapping("/admin/windows")
    @Transactional
    public Result<?> saveWindow(@RequestBody WindowInput body,HttpServletRequest request) {
        var user=admin(request);
        if(body==null || body.opensAt()==null || body.closesAt()==null || !body.closesAt().isAfter(body.opensAt())
                || body.memberStart()==null || body.memberEnd()==null || body.memberEnd().isBefore(body.memberStart()))
            throw error(HttpStatus.BAD_REQUEST,"请设置有效的申请起止时间和队员任期");
        String title=text(body.title(),200,true),description=text(body.description(),4000,false),term=text(body.term(),50,true);
        if(BestiRecordPolicy.termDates(term)[0]==null) throw error(HttpStatus.BAD_REQUEST,"学年请填写 YYYY-YYYY学年，例如 2026-2027学年");
        var open=Timestamp.from(body.opensAt().toInstant()); var close=Timestamp.from(body.closesAt().toInstant());
        if(body.id()==null) db.update("INSERT INTO besti_recruitment_window(title,description,opens_at,closes_at,term,member_start,member_end,enabled,created_by) VALUES (?,?,?,?,?,?,?,?,?)",
                title,description,open,close,term,body.memberStart(),body.memberEnd(),body.enabled(),user.getUserId());
        else {
            var existing=one("SELECT * FROM besti_recruitment_window WHERE id=? FOR UPDATE",body.id()); version(existing,body.version());
            db.update("UPDATE besti_recruitment_window SET title=?,description=?,opens_at=?,closes_at=?,term=?,member_start=?,member_end=?,enabled=?,version=version+1,updated_at=now() WHERE id=?",
                    title,description,open,close,term,body.memberStart(),body.memberEnd(),body.enabled(),body.id());
        }
        return Result.success("申请批次已保存");
    }
    @GetMapping("/admin/applications")
    public Result<?> applications(@RequestParam Long windowId,@RequestParam(defaultValue="pending") String status,
                                 @RequestParam(defaultValue="1") int page,HttpServletRequest request) {
        admin(request);
        if(!Set.of("all","pending","approved","rejected","withdrawn").contains(status)) throw error(HttpStatus.BAD_REQUEST,"无效状态");
        String where="a.window_id=?"+("all".equals(status)?"":" AND a.status=?");
        var args=new ArrayList<Object>(); args.add(windowId); if(!"all".equals(status)) args.add(status);
        var total=db.queryForObject("SELECT count(*) FROM besti_team_application a WHERE "+where,Long.class,args.toArray());
        args.add((Math.max(1,Math.min(page,10000))-1)*50);
        var rows=db.queryForList("SELECT a.*,u.user_name FROM besti_team_application a JOIN users u ON u.user_id=a.user_id WHERE "+where+" ORDER BY a.created_at,a.id LIMIT 50 OFFSET ?",args.toArray());
        return Result.success(Map.of("items",rows.stream().map(this::display).toList(),"total",total));
    }
    @PostMapping("/admin/export")
    public void export(@RequestBody ExportInput body,HttpServletRequest request,HttpServletResponse response) throws IOException {
        admin(request);
        if(body==null || body.windowId()==null || body.status()==null || !Set.of("all","pending","approved","rejected","withdrawn").contains(body.status())
                || body.format()==null || !Set.of("xlsx","docx").contains(body.format())) throw error(HttpStatus.BAD_REQUEST,"请选择有效批次、状态和导出格式");
        var window=one("SELECT * FROM besti_recruitment_window WHERE id=?",body.windowId());
        String where="a.window_id=?"+("all".equals(body.status())?"":" AND a.status=?");
        var args=new ArrayList<Object>();args.add(body.windowId());if(!"all".equals(body.status()))args.add(body.status());
        Set<Long> ids=null;
        if(body.ids()!=null) {
            if(body.ids().isEmpty() || body.ids().size()>1000 || body.ids().stream().anyMatch(Objects::isNull)) throw error(HttpStatus.BAD_REQUEST,"请勾选 1 至 1000 份申请");
            ids=new LinkedHashSet<>(body.ids());
            where+=" AND a.id IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")";args.addAll(ids);
        }
        var rows=db.queryForList("SELECT a.* FROM besti_team_application a WHERE "+where+" ORDER BY a.created_at,a.id LIMIT 1001",args.toArray());
        if(rows.size()>1000) throw error(HttpStatus.BAD_REQUEST,"单次最多导出 1000 份，请勾选后分批导出");
        if(ids!=null && rows.size()!=ids.size()) throw error(HttpStatus.CONFLICT,"勾选的申请已变化或不属于当前筛选，请刷新后重选");
        if(rows.isEmpty()) throw error(HttpStatus.BAD_REQUEST,"当前筛选没有可导出的申请");
        byte[] data="xlsx".equals(body.format())?RecruitmentExportService.workbook(rows,window.get("title").toString()):RecruitmentExportService.documents(rows,window.get("title").toString());
        response.setContentType("xlsx".equals(body.format())?"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":"application/zip");
        response.setHeader("Content-Disposition","attachment; filename=\"acm-applications-"+body.windowId()+("xlsx".equals(body.format())?".xlsx":".zip")+"\"");
        response.setHeader("Cache-Control","no-store");response.setContentLength(data.length);response.getOutputStream().write(data);
    }
    @PostMapping("/admin/applications/{id}/review")
    @Transactional
    public Result<?> review(@PathVariable long id,@RequestBody ReviewInput body,HttpServletRequest request) {
        var reviewer=admin(request);
        var snapshot=one("SELECT * FROM besti_team_application WHERE id=?",id);
        var identity=identity(number(snapshot,"user_id"),true);
        var application=one("SELECT * FROM besti_team_application WHERE id=? FOR UPDATE",id); version(application,body.version());
        if(!"pending".equals(application.get("status"))) throw error(HttpStatus.CONFLICT,"申请已处理，请刷新列表");
        if(number(application,"user_id")==reviewer.getUserId()) throw error(HttpStatus.FORBIDDEN,"不能审核自己的申请");
        String note=text(body.note(),2000,!body.approved());
        Long memberId=null;
        if(body.approved()) {
            requireIdentity(identity);
            if(!Objects.equals(identity.get("real_name"),application.get("real_name"))
                    || !Objects.equals(identity.get("number_id"),application.get("student_number"))
                    || !Objects.equals(identity.get("school_name"),application.get("school_name")))
                throw error(HttpStatus.CONFLICT,"申请人的身份信息已变化，请先核实并重新申请");
            var window=one("SELECT * FROM besti_recruitment_window WHERE id=?",application.get("window_id"));
            var start=body.memberStart()==null?date(window.get("member_start")):body.memberStart();
            var end=body.memberEnd()==null?date(window.get("member_end")):body.memberEnd();
            if(start==null || end==null || end.isBefore(start) || end.isBefore(BestiRecordPolicy.today()))
                throw error(HttpStatus.BAD_REQUEST,"请设置尚未结束的有效队员任期");
            for(var existing:db.queryForList("SELECT term,start_date,end_date FROM association_member WHERE user_id=? AND department='ACM 校队' AND position IN ('队员','校队队长')",application.get("user_id"))) {
                var fallback=BestiRecordPolicy.termDates((String)existing.get("term"));
                var oldStart=existing.get("start_date")==null?fallback[0]:date(existing.get("start_date"));
                var oldEnd=existing.get("end_date")==null?fallback[1]:date(existing.get("end_date"));
                if(oldStart!=null && oldEnd!=null && !oldStart.isAfter(end) && !oldEnd.isBefore(start))
                    throw error(HttpStatus.CONFLICT,"该成员已有重叠的校队任期，请在校队成员管理中核实");
            }
            memberId=db.queryForObject("INSERT INTO association_member(term,department,user_id,real_name,school_name,number_id,position,sort_order,start_date,end_date) VALUES (?,'ACM 校队',?,?,?,?,'队员',0,?,?) RETURNING member_id",
                    Long.class,window.get("term"),application.get("user_id"),identity.get("real_name"),identity.get("school_name"),identity.get("number_id"),start,end);
        }
        db.update("UPDATE besti_team_application SET status=?,review_note=?,reviewed_by=?,reviewed_at=now(),member_id=?,version=version+1,updated_at=now() WHERE id=?",
                body.approved()?"approved":"rejected",note,reviewer.getUserId(),memberId,id);
        return Result.success(body.approved()?"已通过，成员已加入校队名单":"已驳回申请");
    }
}
