package com.cwnupaa.backend.controller;

import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.utils.SecurityUtils;
import com.cwnupaa.backend.utils.BestiRecordPolicy;
import com.cwnupaa.backend.service.TeamMembershipService;
import com.cwnupaa.backend.service.HistoricalAwardService;
import com.cwnupaa.backend.service.RatingRefreshRequested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import com.cwnupaa.backend.vo.UserProfileVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.LocalDate;
import java.util.*;

/** School records are factual claims, never an account-role assignment. */
@RestController
@RequestMapping("/team-space")
public class TeamSpaceController {
    @Autowired private ApplicationEventPublisher ratingEvents;
    static final Set<String> ORGANIZED = Set.of("联盟主办", "ICPC", "CCPC", "天梯赛");
    static final Set<String> VOLUNTARY = Set.of("蓝桥杯", "马蹄杯", "百度之星", "CCSP", "CNCC");
    static final Set<String> KINDS = Set.of("award", "position", "registration", "organizing");
    static final Set<String> CATEGORIES = Set.of("discussion", "training", "experience", "announcement");
    private final JdbcTemplate db;
    private final TeamMembershipService membership;
    public TeamSpaceController(JdbcTemplate db, TeamMembershipService membership) {
        this.db = db;
        this.membership = membership;
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result<String>> handleStatus(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Result.error(exception.getStatusCode().value(),exception.getReason()));
    }

    public record MemberRecord(Long id, Integer version, String kind, String family, String title,
        String organization, String result, String teamName, LocalDate startDate, LocalDate endDate,
        String sourceUrl, String notes, String awardLevel, String awardGrade, String contestStage, String contestAward) {}
    public record Review(int version, boolean approved, String note) {}
    public record Roster(MemberRecord record, List<Long> userIds) {}
    public record Topic(String category, String title, String content) {}
    public record Reply(String content) {}
    public record Moderation(boolean pinned, boolean locked) {}

    private UserProfileVO verifiedUser(HttpServletRequest request) {
        var u = SecurityUtils.currentUser(request);
        if (u == null) throw error(HttpStatus.UNAUTHORIZED, "请先登录");
        if (!"normal".equals(u.getAccountStatus())) throw error(HttpStatus.FORBIDDEN, "请先完成学生身份认证");
        return u;
    }
    private UserProfileVO forumMember(HttpServletRequest request) {
        var u = verifiedUser(request);
        require(moderator(u) || membership.isCurrentMember(u.getUserId()),
            "校队交流区仅限任期内的校队成员；学生身份认证通过后仍需管理员确认校队资格");
        return u;
    }
    private boolean moderator(UserProfileVO u) {
        return SecurityUtils.hasPermission(u,"manage_user") || SecurityUtils.hasPermission(u,"manage_sys");
    }
    private boolean coordinator(UserProfileVO u) {
        return SecurityUtils.hasPermission(u,"manage_event") || SecurityUtils.hasPermission(u,"manage_contest") || moderator(u);
    }
    private void require(boolean condition, String message) {
        if (!condition) throw error(HttpStatus.FORBIDDEN, message);
    }
    static ResponseStatusException error(HttpStatus status, String text) { return new ResponseStatusException(status,text); }
    static String text(String value, int max, boolean required) {
        String s = value == null ? "" : value.trim();
        if ((required && s.isEmpty()) || s.length() > max) throw error(HttpStatus.BAD_REQUEST,"内容不能为空或超过长度限制");
        return s;
    }
    static void validate(MemberRecord r) {
        if (r == null || r.kind()==null || !KINDS.contains(r.kind())) throw error(HttpStatus.BAD_REQUEST,"无效的记录类型");
        if (!"position".equals(r.kind()) && (r.family()==null || (!ORGANIZED.contains(r.family()) && !VOLUNTARY.contains(r.family()))))
            throw error(HttpStatus.BAD_REQUEST,"赛事不在校队平台登记范围内");
        text(r.title(),200,true); text(r.organization(),200,false); text(r.result(),100,false);
        text(r.teamName(),200,false); text(r.notes(),4000,false);
        if ("organizing".equals(r.kind())) {
            text(r.organization(),200,true); text(r.result(),100,true);
            if (r.startDate()==null) throw error(HttpStatus.BAD_REQUEST,"请填写办赛日期");
        }
        if (r.startDate()!=null && r.endDate()!=null && r.endDate().isBefore(r.startDate()))
            throw error(HttpStatus.BAD_REQUEST,"结束日期不能早于开始日期");
        if ("position".equals(r.kind())) {
            if (!BestiRecordPolicy.validRole(r.organization(), r.title()))
                throw error(HttpStatus.BAD_REQUEST,"请选择指定组织及职务");
            if (r.startDate() == null || r.endDate() == null)
                throw error(HttpStatus.BAD_REQUEST,"请填写任职开始和结束日期");
        }
        if ("award".equals(r.kind()) && (r.awardLevel() == null || r.awardGrade() == null
                || !BestiRecordPolicy.AWARD_LEVELS.contains(r.awardLevel()) || !BestiRecordPolicy.AWARD_GRADES.contains(r.awardGrade())))
            throw error(HttpStatus.BAD_REQUEST,"请选择奖项级别与等次");
        if ("award".equals(r.kind())) {
            try {
                String level = BestiRecordPolicy.recognizedLevel(r.family(), r.contestStage());
                if (level != null && !level.equals(r.awardLevel()))
                    throw new IllegalArgumentException("该赛事阶段按学校口径应登记为" + level);
                if (BestiRecordPolicy.isXcpc(r.family())) {
                    String grade = BestiRecordPolicy.XCPC_AWARD_GRADES.get(text(r.contestAward(),20,true));
                    if (grade == null) throw new IllegalArgumentException("请选择赛事金牌、银牌、铜牌或优胜奖");
                    if (!grade.equals(r.awardGrade())) throw new IllegalArgumentException("该赛事奖项按学校口径应认定为" + grade);
                }
            } catch (IllegalArgumentException e) { throw error(HttpStatus.BAD_REQUEST, e.getMessage()); }
        }
        String url=text(r.sourceUrl(),1000,false);
        if (!url.isEmpty()) {
            try {
                URI uri=URI.create(url);
                if (!Set.of("https","http").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null)
                    throw new IllegalArgumentException();
            } catch (IllegalArgumentException e) { throw error(HttpStatus.BAD_REQUEST,"来源需为有效的 http 或 https 链接"); }
        }
    }
    static void validateRosterFamily(MemberRecord r) {
        validate(r);
        if (!"registration".equals(r.kind()) || !ORGANIZED.contains(r.family()))
            throw error(HttpStatus.BAD_REQUEST,"自愿登记赛事只能由成员本人申请，不能由队长统一登记");
    }
    private Map<String,Object> row(String sql, Object... args) {
        var rows=db.queryForList(sql,args);
        if (rows.isEmpty()) throw error(HttpStatus.NOT_FOUND,"记录不存在");
        return rows.getFirst();
    }
    private long number(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
    private int page(int page) { return Math.max(1,Math.min(page,100000)); }

    private String result(MemberRecord r) {
        return "award".equals(r.kind()) ? r.awardLevel() + r.awardGrade() : text(r.result(),100,false);
    }
    private String awardLevel(MemberRecord r) { return "award".equals(r.kind()) ? r.awardLevel() : null; }
    private String awardGrade(MemberRecord r) { return "award".equals(r.kind()) ? r.awardGrade() : null; }
    private String contestAward(MemberRecord r) { return "award".equals(r.kind()) && BestiRecordPolicy.isXcpc(r.family()) ? text(r.contestAward(),20,true) : null; }
    private List<Map<String,Object>> displayRecords(List<Map<String,Object>> records) {
        for (var r : records) {
            if (!"position".equals(r.get("kind"))) continue;
            LocalDate start = r.get("start_date") == null ? null : LocalDate.parse(r.get("start_date").toString());
            LocalDate end = r.get("end_date") == null ? null : LocalDate.parse(r.get("end_date").toString());
            String status = BestiRecordPolicy.positionStatus(start, end, BestiRecordPolicy.today());
            r.put("position_status", status);
            r.put("display_title", BestiRecordPolicy.positionLabel((String)r.get("organization"), (String)r.get("title"), status));
        }
        return records;
    }

    @GetMapping("/me")
    public Result<?> me(HttpServletRequest request) {
        var u=verifiedUser(request);
        boolean teamMember = membership.isCurrentMember(u.getUserId());
        return Result.success(Map.of("userId",u.getUserId(),"name",u.getUserName(),"moderator",moderator(u),
            "coordinator",coordinator(u),"teamMember",teamMember,"communityAccess",teamMember || moderator(u)));
    }
    @GetMapping("/public-records")
    public Result<?> publicRecords(@RequestParam(required=false) Long userId,@RequestParam(defaultValue="1") int page,
                                   @RequestParam(defaultValue="all") String section) {
        if (!Set.of("all","experience").contains(section)) throw error(HttpStatus.BAD_REQUEST,"无效的展示分类");
        String where="r.status='verified' AND (r.kind IN ('award','position','organizing') OR r.history_link_id IS NOT NULL) AND u.account_status='normal' AND "+HistoricalAwardService.visibleRecordSql("r");
        if ("experience".equals(section)) where+=" AND r.kind IN ('position','organizing')";
        var args=new ArrayList<Object>();
        if(userId!=null){where+=" AND r.user_id=?";args.add(userId);}
        var total=db.queryForObject("SELECT count(*) FROM besti_member_record r JOIN users u ON u.user_id=r.user_id WHERE "+where,Long.class,args.toArray());
        var counts=db.queryForMap("SELECT count(CASE WHEN r.kind='award' AND r.is_starred=false THEN 1 END) AS awards,count(CASE WHEN r.kind='award' AND r.is_starred=true THEN 1 END) AS starred FROM besti_member_record r JOIN users u ON u.user_id=r.user_id WHERE "+where,args.toArray());
        args.add((page(page)-1)*50);
        var items=db.queryForList("SELECT r.id,r.user_id,u.user_name AS owner_name,r.kind,r.family,r.title,r.organization,r.result,r.award_level,r.award_grade,r.contest_stage,r.contest_award,r.team_name,r.start_date,r.end_date,r.source_url,r.history_link_id,r.is_starred FROM besti_member_record r JOIN users u ON u.user_id=r.user_id WHERE "+where+" ORDER BY r.start_date DESC NULLS LAST,r.id DESC LIMIT 50 OFFSET ?",args.toArray());
        return Result.success(Map.of("items",displayRecords(items),"positions",officialPositions(userId),"total",total,"awards",counts.get("awards"),"starred",counts.get("starred")));
    }
    public Result<?> publicRecords(Long userId,int page) { return publicRecords(userId,page,"all"); }
    private List<Map<String,Object>> officialPositions(Long userId) {
        if(userId==null) return List.of();
        var positions=db.queryForList("SELECT m.member_id AS id,m.term,m.department AS organization,m.position AS title,m.start_date,m.end_date FROM association_member m JOIN users u ON u.user_id=m.user_id WHERE m.user_id=? AND u.account_status='normal' ORDER BY m.start_date DESC NULLS LAST,m.member_id DESC",userId);
        for(var p:positions) {
            var dates=BestiRecordPolicy.termDates((String)p.get("term"));
            if(p.get("start_date")==null) p.put("start_date",dates[0]);
            if(p.get("end_date")==null) p.put("end_date",dates[1]);
            p.put("id","position-"+p.get("id"));p.put("kind","position");p.put("result",p.get("organization"));
            p.put("status","verified");p.put("source_type","official_roster");
        }
        return displayRecords(positions);
    }
    @GetMapping("/records")
    public Result<?> records(HttpServletRequest request,@RequestParam(defaultValue="mine") String scope,
            @RequestParam(required=false) Long userId,@RequestParam(defaultValue="1") int page) {
        var u=verifiedUser(request);
        if ("member".equals(scope)) return publicRecords(userId==null?u.getUserId():userId,page);
        String where; Object[] args;
        if ("review".equals(scope)) {
            require(moderator(u),"无审核权限"); where="r.status='pending'"; args=new Object[]{};
        } else { where="r.user_id=? AND "+HistoricalAwardService.visibleRecordSql("r"); args=new Object[]{u.getUserId()}; }
        var count=db.queryForObject("SELECT count(*) FROM besti_member_record r WHERE "+where,Long.class,args);
        var params=new ArrayList<Object>(Arrays.asList(args));params.add((page(page)-1)*50);
        var items=db.queryForList("SELECT r.*,u.user_name AS owner_name FROM besti_member_record r JOIN users u ON u.user_id=r.user_id WHERE "+where+" ORDER BY r.updated_at DESC,r.id DESC LIMIT 50 OFFSET ?",params.toArray());
        return Result.success(Map.of("items",displayRecords(items),"total",count));
    }
    @PostMapping("/records")
    @Transactional
    public Result<?> saveRecord(HttpServletRequest request,@RequestBody MemberRecord r) {
        var u=verifiedUser(request); validate(r);
        if (r.id()==null) return Result.success(Map.of("id",insertRecord(r,u.getUserId(),u.getUserId())));
        var existing=row("SELECT user_id,version,history_link_id FROM besti_member_record WHERE id=? FOR UPDATE",r.id());
        require(number(existing,"user_id")==u.getUserId(),"只能修改自己的记录");
        if(existing.get("history_link_id")!=null) throw error(HttpStatus.CONFLICT,"历史战绩须由管理员在归属管理中修正，不能改写为自填奖项");
        if (r.version()==null || number(existing,"version")!=r.version()) throw error(HttpStatus.CONFLICT,"记录已更新，请刷新后再保存");
        db.update("UPDATE besti_member_record SET kind=?,family=?,title=?,organization=?,result=?,team_name=?,start_date=?,end_date=?,source_url=?,notes=?,status='pending',review_note='',reviewed_by=NULL,version=version+1,updated_at=now(),award_level=?,award_grade=?,contest_stage=?,contest_award=? WHERE id=?",
            r.kind(),"position".equals(r.kind())?"":r.family(),text(r.title(),200,true),text(r.organization(),200,false),result(r),text(r.teamName(),200,false),r.startDate(),r.endDate(),text(r.sourceUrl(),1000,false),text(r.notes(),4000,false),awardLevel(r),awardGrade(r),"award".equals(r.kind())?text(r.contestStage(),30,false):null,contestAward(r),r.id());
        RatingRefreshRequested.publish(ratingEvents);
        return Result.success("已保存，等待核验");
    }
    private Long insertRecord(MemberRecord r,long owner,long creator) {
        return db.queryForObject("INSERT INTO besti_member_record(user_id,kind,family,title,organization,result,team_name,start_date,end_date,source_url,notes,created_by,award_level,award_grade,contest_stage,contest_award) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id",Long.class,
            owner,r.kind(),"position".equals(r.kind())?"":r.family(),text(r.title(),200,true),text(r.organization(),200,false),result(r),text(r.teamName(),200,false),r.startDate(),r.endDate(),text(r.sourceUrl(),1000,false),text(r.notes(),4000,false),creator,awardLevel(r),awardGrade(r),"award".equals(r.kind())?text(r.contestStage(),30,false):null,contestAward(r));
    }
    @PostMapping("/roster")
    @Transactional
    public Result<?> roster(HttpServletRequest request,@RequestBody Roster body) {
        var u=verifiedUser(request);require(coordinator(u),"仅有赛事管理权限的队长或管理员可统一登记");
        validateRosterFamily(body.record());
        if(body.userIds()==null || body.userIds().isEmpty() || body.userIds().size()>100 || body.userIds().contains(null))
            throw error(HttpStatus.BAD_REQUEST,"请选择 1 至 100 位已认证参赛者");
        var ids=new LinkedHashSet<>(body.userIds());
        for(long id:ids) {
            if(!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM users WHERE user_id=? AND account_status='normal')",Boolean.class,id)))
                throw error(HttpStatus.BAD_REQUEST,"名单中包含不存在或未完成身份认证的账号");
            insertRecord(body.record(),id,u.getUserId());
        }
        return Result.success(Map.of("count",ids.size()));
    }
    @DeleteMapping("/records/{id}")
    public Result<?> deleteRecord(HttpServletRequest request,@PathVariable long id) {
        var u=verifiedUser(request);
        var existing=row("SELECT user_id,history_link_id FROM besti_member_record WHERE id=?",id);
        require(number(existing,"user_id")==u.getUserId(),"只能删除自己的记录");
        if(existing.get("history_link_id")!=null) throw error(HttpStatus.CONFLICT,"历史战绩须由管理员核验撤销归属");
        int changed=db.update("DELETE FROM besti_member_record WHERE id=? AND user_id=? AND history_link_id IS NULL",id,u.getUserId());
        if(changed==0) throw error(HttpStatus.NOT_FOUND,"记录不存在或不属于你");
        RatingRefreshRequested.publish(ratingEvents);
        return Result.success("已删除");
    }
    @PostMapping("/records/{id}/review")
    @Transactional
    public Result<?> review(HttpServletRequest request,@PathVariable long id,@RequestBody Review review) {
        var u=verifiedUser(request);require(moderator(u),"无审核权限");
        int changed=db.update("UPDATE besti_member_record SET status=?,review_note=?,reviewed_by=?,version=version+1,updated_at=now() WHERE id=? AND version=? AND status='pending' AND history_link_id IS NULL",
            review.approved()?"verified":"rejected",text(review.note(),1000,false),u.getUserId(),id,review.version());
        if(changed==0) throw error(HttpStatus.CONFLICT,"记录已发生变化，请刷新后审核");
        RatingRefreshRequested.publish(ratingEvents);
        return Result.success("核验结果已保存");
    }
    @GetMapping("/topics")
    public Result<?> topics(HttpServletRequest request,@RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="") String category,@RequestParam(defaultValue="") String keyword) {
        forumMember(request);
        String where="(?='' OR t.category=?) AND (?='' OR t.title ILIKE ?)";
        String query=text(keyword,100,false);String cat=text(category,30,false);
        Object[] params={cat,cat,query,"%"+query+"%"};
        var total=db.queryForObject("SELECT count(*) FROM besti_forum_topic t WHERE "+where,Long.class,params);
        var args=new ArrayList<Object>(Arrays.asList(params));args.add((page(page)-1)*30);
        var items=db.queryForList("SELECT t.id,t.author_id,t.category,t.title,t.pinned,t.locked,t.created_at,t.updated_at,u.user_name AS author_name,(SELECT count(*) FROM besti_forum_reply r WHERE r.topic_id=t.id) AS reply_count FROM besti_forum_topic t JOIN users u ON u.user_id=t.author_id WHERE "+where+" ORDER BY t.pinned DESC,t.updated_at DESC,t.id DESC LIMIT 30 OFFSET ?",args.toArray());
        return Result.success(Map.of("items",displayRecords(items),"total",total));
    }
    @GetMapping("/topics/{id}")
    public Result<?> topic(HttpServletRequest request,@PathVariable long id) {
        forumMember(request);
        return Result.success(row("SELECT t.*,u.user_name AS author_name FROM besti_forum_topic t JOIN users u ON u.user_id=t.author_id WHERE t.id=?",id));
    }
    @PostMapping("/topics")
    public Result<?> createTopic(HttpServletRequest request,@RequestBody Topic topic) {
        var u=forumMember(request);validateTopic(u,topic);
        Long id=db.queryForObject("INSERT INTO besti_forum_topic(author_id,category,title,content) VALUES(?,?,?,?) RETURNING id",Long.class,u.getUserId(),topic.category(),text(topic.title(),200,true),text(topic.content(),20000,true));
        return Result.success(Map.of("id",id));
    }
    private void validateTopic(UserProfileVO u,Topic topic) {
        if(topic.category()==null || !CATEGORIES.contains(topic.category())) throw error(HttpStatus.BAD_REQUEST,"请选择有效板块");
        if("announcement".equals(topic.category())) require(moderator(u),"仅管理员可发布校队公告");
        text(topic.title(),200,true);text(topic.content(),20000,true);
    }
    @PutMapping("/topics/{id}")
    @Transactional
    public Result<?> editTopic(HttpServletRequest request,@PathVariable long id,@RequestBody Topic topic) {
        var u=forumMember(request);validateTopic(u,topic);
        var existing=row("SELECT author_id,locked FROM besti_forum_topic WHERE id=? FOR UPDATE",id);
        require(number(existing,"author_id")==u.getUserId(),"只能编辑自己的帖子");
        require(!Boolean.TRUE.equals(existing.get("locked")) || moderator(u),"此讨论已锁定");
        db.update("UPDATE besti_forum_topic SET category=?,title=?,content=?,updated_at=now() WHERE id=?",topic.category(),text(topic.title(),200,true),text(topic.content(),20000,true),id);
        return Result.success("帖子已更新");
    }
    @DeleteMapping("/topics/{id}")
    @Transactional
    public Result<?> deleteTopic(HttpServletRequest request,@PathVariable long id) {
        var u=forumMember(request);var existing=row("SELECT author_id FROM besti_forum_topic WHERE id=? FOR UPDATE",id);
        require(number(existing,"author_id")==u.getUserId() || moderator(u),"无权删除此帖子");
        db.update("DELETE FROM besti_forum_topic WHERE id=?",id);return Result.success("已删除");
    }
    @PostMapping("/topics/{id}/moderate")
    public Result<?> moderate(HttpServletRequest request,@PathVariable long id,@RequestBody Moderation flags) {
        var u=forumMember(request);require(moderator(u),"无管理权限");
        if(db.update("UPDATE besti_forum_topic SET pinned=?,locked=? WHERE id=?",flags.pinned(),flags.locked(),id)==0)
            throw error(HttpStatus.NOT_FOUND,"帖子不存在");
        return Result.success("已更新");
    }
    @GetMapping("/topics/{id}/replies")
    public Result<?> replies(HttpServletRequest request,@PathVariable long id,@RequestParam(defaultValue="1") int page) {
        forumMember(request);row("SELECT id FROM besti_forum_topic WHERE id=?",id);
        var total=db.queryForObject("SELECT count(*) FROM besti_forum_reply WHERE topic_id=?",Long.class,id);
        return Result.success(Map.of("items",db.queryForList("SELECT r.*,u.user_name AS author_name FROM besti_forum_reply r JOIN users u ON u.user_id=r.author_id WHERE topic_id=? ORDER BY r.id LIMIT 50 OFFSET ?",id,(page(page)-1)*50),"total",total));
    }
    @PostMapping("/topics/{id}/replies")
    @Transactional
    public Result<?> reply(HttpServletRequest request,@PathVariable long id,@RequestBody Reply reply) {
        var u=forumMember(request);var topic=row("SELECT locked FROM besti_forum_topic WHERE id=? FOR UPDATE",id);
        require(!Boolean.TRUE.equals(topic.get("locked")),"此讨论已锁定");
        String content=text(reply.content(),10000,true);
        Long replyId=db.queryForObject("INSERT INTO besti_forum_reply(topic_id,author_id,content) VALUES(?,?,?) RETURNING id",Long.class,id,u.getUserId(),content);
        db.update("UPDATE besti_forum_topic SET updated_at=now() WHERE id=?",id);
        return Result.success(Map.of("id",replyId));
    }
    @PutMapping("/replies/{id}")
    @Transactional
    public Result<?> editReply(HttpServletRequest request,@PathVariable long id,@RequestBody Reply reply) {
        var u=forumMember(request);var r=row("SELECT r.author_id,t.locked FROM besti_forum_reply r JOIN besti_forum_topic t ON t.id=r.topic_id WHERE r.id=? FOR UPDATE OF t",id);
        require(number(r,"author_id")==u.getUserId(),"只能编辑自己的回复");
        require(!Boolean.TRUE.equals(r.get("locked")) || moderator(u),"此讨论已锁定");
        db.update("UPDATE besti_forum_reply SET content=?,updated_at=now() WHERE id=?",text(reply.content(),10000,true),id);
        return Result.success("回复已更新");
    }
    @DeleteMapping("/replies/{id}")
    public Result<?> deleteReply(HttpServletRequest request,@PathVariable long id) {
        var u=forumMember(request);var r=row("SELECT author_id FROM besti_forum_reply WHERE id=?",id);
        require(number(r,"author_id")==u.getUserId() || moderator(u),"无权删除此回复");
        db.update("DELETE FROM besti_forum_reply WHERE id=?",id);return Result.success("已删除");
    }
}
