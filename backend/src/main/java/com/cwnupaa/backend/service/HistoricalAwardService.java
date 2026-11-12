package com.cwnupaa.backend.service;

import com.cwnupaa.backend.utils.BestiRecordPolicy;
import com.cwnupaa.backend.utils.SecurityUtils;
import com.cwnupaa.backend.vo.UserProfileVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;

/** Historical results are factual associations. They never grant team membership or account permissions. */
@Service
public class HistoricalAwardService {
    public static final String SCHOOL = "北京电子科技学院";
    private final JdbcTemplate db;
    @Autowired private ApplicationEventPublisher ratingEvents;
    private final ObjectMapper json = new ObjectMapper();
    public HistoricalAwardService(JdbcTemplate db) { this.db = db; }

    public record ClaimInput(String honorUid, int memberIndex, String sourceHash, String notes, String evidenceUrl, Integer version) {}
    public record Decision(int version, boolean approved, String note) {}
    public record RosterRow(String honorUid, int memberIndex, String school, String studentNumber, String realName) {}
    public record ImportInput(List<RosterRow> rows, String token) {}
    public record Revoke(int version, String note) {}
    private record Identity(long id, String school, String number, String name) {}
    private record Honor(Map<String,Object> raw, String uid, String hash, List<String> members) {
        String field(String key) { return str(raw.get(key)); }
        int size() { return members.isEmpty() ? 3 : members.size(); }
        String member(int index) {
            if (index < 1 || index > size()) throw fail(HttpStatus.BAD_REQUEST,"队员序号不在这条战绩的名单内");
            return members.isEmpty() ? "" : members.get(index - 1);
        }
    }
    private static String str(Object value) { return value == null ? "" : value.toString().trim(); }
    private static long num(Map<String,Object> row, String key) { return ((Number)row.get(key)).longValue(); }
    private static ResponseStatusException fail(HttpStatus status, String message) { return new ResponseStatusException(status,message); }
    private static String text(String value, int max, boolean required) {
        String result = str(value);
        if ((required && result.isBlank()) || result.length()>max) throw fail(HttpStatus.BAD_REQUEST,"请填写完整信息，并检查内容长度");
        return result;
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch(Exception e) { throw new IllegalStateException("历史战绩数据无法序列化"); }
    }
    private String hash(Object value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(write(value).getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException("历史战绩校验失败"); }
    }
    private Map<String,Object> one(String sql,Object...args) {
        var rows=db.queryForList(sql,args);
        return rows.isEmpty()?null:rows.getFirst();
    }
    private long insert(String sql,Object...args) {
        var key=new GeneratedKeyHolder();
        db.update(connection -> {
            var statement=connection.prepareStatement(sql,new String[]{"id"});
            for(int i=0;i<args.length;i++) statement.setObject(i+1,args[i]);
            return statement;
        },key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
    private Map<String,Honor> catalog(boolean lock) {
        // One common row lock serializes claim, review, import and source editing, including initially absent slots.
        var source=one("SELECT content FROM sys_intro WHERE category='honors'"+(lock?" FOR UPDATE":""));
        if(source==null) throw fail(HttpStatus.CONFLICT,"历史战绩尚未导入");
        List<Map<String,Object>> rows;
        try { rows=json.readValue(str(source.get("content")),new TypeReference<>(){}); }
        catch(Exception e) { throw fail(HttpStatus.CONFLICT,"历史战绩格式异常，请先修复荣誉墙档案"); }
        var result=new LinkedHashMap<String,Honor>();
        for(var raw:rows) {
            if(!BestiRecordPolicy.isXcpc(str(raw.get("family")))) continue;
            String uid=text(str(raw.get("uid")),100,true);
            if(result.containsKey(uid)) throw fail(HttpStatus.CONFLICT,"历史战绩编号重复，请先修复档案");
            List<String> members=raw.get("members") instanceof List<?> list?list.stream().map(HistoricalAwardService::str).toList():List.of();
            if(members.size()>20) throw fail(HttpStatus.CONFLICT,"历史战绩队员数异常");
            var stable=new LinkedHashMap<String,Object>();
            for(String key:List.of("uid","event","title","date","academicYear","family","tier","team","members","official","contestAward","awardLevel","awardGrade","equivalentContestAward")) stable.put(key,raw.get(key));
            result.put(uid,new Honor(raw,uid,hash(stable),members));
        }
        return result;
    }
    private Honor honor(Map<String,Honor> catalog,String uid) {
        Honor value=catalog.get(uid);
        if(value==null) throw fail(HttpStatus.NOT_FOUND,"战绩不存在或不在 ICPC、CCPC 历史档案内");
        return value;
    }
    private static final String IDENTITY_SQL="SELECT u.user_id,u.real_name,u.number_id,v.university_name FROM users u JOIN major m ON m.major_id=u.major_id JOIN school s ON s.school_id=m.school_id JOIN university v ON v.university_id=s.university_id WHERE u.account_status='normal' ";
    private Identity identity(long userId) {
        var row=one(IDENTITY_SQL+"AND u.user_id=?",userId);
        if(row==null || str(row.get("real_name")).isBlank() || str(row.get("number_id")).isBlank())
            throw fail(HttpStatus.FORBIDDEN,"请先完成学生身份认证，补齐学校、学号和姓名");
        return new Identity(userId,str(row.get("university_name")),str(row.get("number_id")),str(row.get("real_name")));
    }
    public boolean manager(UserProfileVO actor) { return SecurityUtils.hasPermission(actor,"manage_user") || SecurityUtils.hasPermission(actor,"manage_sys"); }
    private void requireManager(UserProfileVO actor) {
        if(!manager(actor)) throw fail(HttpStatus.FORBIDDEN,"需要用户管理或系统管理权限");
    }
    private boolean same(Identity user,Map<String,Object> link) {
        return user.school.equals(str(link.get("school_name"))) && user.number.equals(str(link.get("student_number"))) && user.name.equals(str(link.get("real_name")));
    }
    private Map<String,Object> link(Honor honor,int index,boolean create) {
        String member=honor.member(index);
        var row=one("SELECT * FROM besti_honor_link WHERE honor_uid=? AND member_index=?",honor.uid,index);
        if(row==null && create) {
            long id=insert("INSERT INTO besti_honor_link(honor_uid,member_index,member_name,source_hash,source_snapshot) VALUES (?,?,?,?,?)",honor.uid,index,member,honor.hash,write(honor.raw));
            row=one("SELECT * FROM besti_honor_link WHERE id=?",id);
        }
        return row;
    }
    private void audit(long linkId,Long claimId,long actor,String action,String detail) {
        db.update("INSERT INTO besti_honor_audit(link_id,claim_id,actor_id,action,detail) VALUES (?,?,?,?,?)",linkId,claimId,actor,action,text(detail,4000,true));
    }
    private boolean available(Map<String,Object> link) { return link==null || Set.of("unassigned","revoked").contains(str(link.get("status"))); }
    private Map<String,Object> summary(Honor h) {
        var result=new LinkedHashMap<String,Object>();
        for(String key:List.of("uid","event","title","date","academicYear","family","tier","team","members","official","contestAward","equivalentContestAward","awardLevel","awardGrade","sources")) result.put(key,h.raw.get(key));
        result.put("sourceHash",h.hash);
        return result;
    }

    public Map<String,Object> list(UserProfileVO actor,String keyword,String uid,boolean mine) {
        var current=manager(actor)?new Identity(actor.getUserId(),SCHOOL,"",str(actor.getRealName())):identity(actor.getUserId());
        var links=new HashMap<String,Map<String,Object>>();
        for(var l:db.queryForList("SELECT * FROM besti_honor_link")) links.put(l.get("honor_uid")+":"+l.get("member_index"),l);
        var claims=new HashMap<Long,Map<String,Object>>();
        for(var c:db.queryForList("SELECT id,link_id,status,version FROM besti_honor_claim WHERE claimant_id=?",current.id)) claims.put(num(c,"link_id"),c);
        var items=new ArrayList<Map<String,Object>>();
        for(var h:catalog(false).values()) {
            if(!str(uid).isBlank() && !uid.equals(h.uid)) continue;
            String haystack=(h.field("event")+h.field("title")+h.field("team")+String.join(" ",h.members)).toLowerCase(Locale.ROOT);
            if(!str(keyword).isBlank() && !haystack.contains(keyword.trim().toLowerCase(Locale.ROOT))) continue;
            var slots=new ArrayList<Map<String,Object>>();boolean relevant=h.members.contains(current.name);
            for(int index=1;index<=h.size();index++) {
                var l=links.get(h.uid+":"+index);var c=l==null?null:claims.get(num(l,"id"));
                boolean own=l!=null && Objects.equals(l.get("user_id"),current.id) && same(current,l);
                relevant |= own || c!=null;
                var slot=new LinkedHashMap<String,Object>();slot.put("index",index);slot.put("memberName",h.member(index));
                slot.put("status",l==null?"unassigned":l.get("status"));slot.put("own",own);
                slot.put("claimId",c==null?null:c.get("id"));slot.put("claimStatus",c==null?null:c.get("status"));slot.put("claimVersion",c==null?null:c.get("version"));
                slot.put("canClaim",available(l) && (h.member(index).isBlank() || h.member(index).equals(current.name)) && (c==null || Set.of("rejected","withdrawn").contains(str(c.get("status")))));
                slots.add(slot);
            }
            if(mine && !relevant) continue;
            var item=summary(h);item.put("slots",slots);items.add(item);
        }
        return Map.of("items",items,"total",items.size(),"realName",current.name,"school",current.school,"manager",manager(actor));
    }

    @Transactional
    public Map<String,Object> claim(UserProfileVO actor,ClaimInput input) {
        var all=catalog(true);var h=honor(all,input.honorUid);var user=identity(actor.getUserId());
        if(!SCHOOL.equals(user.school)) throw fail(HttpStatus.FORBIDDEN,"本档案为北京电子科技学院战绩，学校身份不匹配");
        if(!h.hash.equals(input.sourceHash)) throw fail(HttpStatus.CONFLICT,"战绩已更新，请刷新后重新认领");
        String sourceName=h.member(input.memberIndex);
        if(!sourceName.isBlank() && !sourceName.equals(user.name)) throw fail(HttpStatus.BAD_REQUEST,"榜单姓名与实名认证姓名不一致，请联系管理员核实原始名单");
        String notes=text(input.notes,4000,true),url=text(input.evidenceUrl,1000,false);
        if(notes.length()<8) throw fail(HttpStatus.BAD_REQUEST,"请补充参赛身份、队伍及证明说明（至少 8 个字）");
        validateUrl(url);
        var l=link(h,input.memberIndex,true);
        if(!available(l)) throw fail(HttpStatus.CONFLICT,"该队员名额已有归属或已登记待实名关联，请联系管理员核实");
        if(db.queryForObject("SELECT count(*) FROM besti_honor_claim c JOIN besti_honor_link l ON l.id=c.link_id WHERE l.honor_uid=? AND c.claimant_id=? AND c.status IN ('pending','approved')",Long.class,h.uid,user.id)>0)
            throw fail(HttpStatus.CONFLICT,"你已提交这条战绩的认领，请勿重复提交");
        if(db.queryForObject("SELECT count(*) FROM besti_honor_link WHERE honor_uid=? AND user_id=? AND status='linked'",Long.class,h.uid,user.id)>0)
            throw fail(HttpStatus.CONFLICT,"这条战绩已关联到你的账号");
        var prior=one("SELECT * FROM besti_honor_claim WHERE link_id=? AND claimant_id=?",l.get("id"),user.id);
        long claimId;
        if(prior==null) claimId=insert("INSERT INTO besti_honor_claim(link_id,claimant_id,claimant_school,claimant_number,claimant_name,source_hash,source_snapshot,evidence_notes,evidence_url) VALUES (?,?,?,?,?,?,?,?,?)",l.get("id"),user.id,user.school,user.number,user.name,h.hash,write(h.raw),notes,url);
        else {
            if(input.version==null || input.version.longValue()!=num(prior,"version")) throw fail(HttpStatus.CONFLICT,"申请已变化，请刷新");
            claimId=num(prior,"id");
            db.update("UPDATE besti_honor_claim SET claimant_school=?,claimant_number=?,claimant_name=?,source_hash=?,source_snapshot=?,evidence_notes=?,evidence_url=?,status='pending',review_note='',reviewed_by=NULL,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",user.school,user.number,user.name,h.hash,write(h.raw),notes,url,claimId);
        }
        audit(num(l,"id"),claimId,user.id,"claim","成员提交认领，等待人工核验");
        return Map.of("id",claimId,"status","pending");
    }
    private void validateUrl(String url) {
        if(url.isEmpty()) return;
        try {var uri=URI.create(url);if(!Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null) throw new IllegalArgumentException();}
        catch(Exception e) {throw fail(HttpStatus.BAD_REQUEST,"证明链接必须是有效的 http 或 https 地址");}
    }
    public List<Map<String,Object>> claims(UserProfileVO actor,boolean review) {
        if(review) requireManager(actor);
        var rows=review?db.queryForList("SELECT c.*,l.honor_uid,l.member_index,l.member_name FROM besti_honor_claim c JOIN besti_honor_link l ON l.id=c.link_id ORDER BY CASE WHEN c.status='pending' THEN 0 ELSE 1 END,c.updated_at DESC LIMIT 500")
                :db.queryForList("SELECT c.*,l.honor_uid,l.member_index,l.member_name FROM besti_honor_claim c JOIN besti_honor_link l ON l.id=c.link_id WHERE c.claimant_id=? ORDER BY c.updated_at DESC LIMIT 500",actor.getUserId());
        var all=catalog(false);
        for(var row:rows) {var h=all.get(str(row.get("honor_uid")));row.put("sourceChanged",h==null || !h.hash.equals(row.get("source_hash")));row.put("honor",h==null?readSnapshot(str(row.get("source_snapshot"))):summary(h));row.remove("source_snapshot");}
        return rows;
    }
    private Object readSnapshot(String snapshot) { try {return json.readValue(snapshot,Map.class);} catch(Exception e) {return Map.of();} }
    @Transactional
    public void withdraw(UserProfileVO actor,long id,int version) {
        catalog(true);
        var row=one("SELECT * FROM besti_honor_claim WHERE id=? AND claimant_id=?",id,actor.getUserId());
        if(row==null) throw fail(HttpStatus.NOT_FOUND,"申请不存在");
        if(num(row,"version")!=version || !"pending".equals(row.get("status"))) throw fail(HttpStatus.CONFLICT,"只有待审核申请可以撤回，请刷新状态");
        db.update("UPDATE besti_honor_claim SET status='withdrawn',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",id);
        audit(num(row,"link_id"),id,actor.getUserId(),"withdraw","成员撤回认领申请");
    }
    @Transactional
    public void review(UserProfileVO actor,long id,Decision decision) {
        requireManager(actor);var all=catalog(true);
        var c=one("SELECT * FROM besti_honor_claim WHERE id=? FOR UPDATE",id);
        if(c==null) throw fail(HttpStatus.NOT_FOUND,"申请不存在");
        if(num(c,"version")!=decision.version || !"pending".equals(c.get("status"))) throw fail(HttpStatus.CONFLICT,"申请已审核或已更新，请刷新");
        String note=text(decision.note,1000,!decision.approved);
        if(decision.approved) {
            var l=one("SELECT * FROM besti_honor_link WHERE id=?",c.get("link_id"));
            var h=honor(all,str(l.get("honor_uid")));
            if(!h.hash.equals(c.get("source_hash"))) throw fail(HttpStatus.CONFLICT,"原始战绩已变化，请退回后重新认领");
            var user=identity(num(c,"claimant_id"));
            if(!user.school.equals(c.get("claimant_school")) || !user.name.equals(c.get("claimant_name")) || !user.number.equals(c.get("claimant_number"))) throw fail(HttpStatus.CONFLICT,"认领人的实名信息已变化，请重新提交申请");
            if(!available(l)) throw fail(HttpStatus.CONFLICT,"该名额已有归属，请先处理归属冲突");
            ensureUnique(h.uid,(int)num(l,"member_index"),user.school,user.number);
            assign(h,(int)num(l,"member_index"),user.school,user.number,user.name,user.id,actor.getUserId(),"review");
            db.update("UPDATE besti_honor_claim SET status='rejected',review_note='该名额已核验归属其他申请，请联系管理员处理异议',reviewed_by=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE link_id=? AND id<>? AND status='pending'",actor.getUserId(),l.get("id"),id);
        }
        db.update("UPDATE besti_honor_claim SET status=?,review_note=?,reviewed_by=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",decision.approved?"approved":"rejected",note,actor.getUserId(),id);
        audit(num(c,"link_id"),id,actor.getUserId(),decision.approved?"approve":"reject",note.isBlank()?"核验身份与参赛证明后通过":note);
        if (decision.approved) RatingRefreshRequested.publish(ratingEvents);
    }

    private void ensureUnique(String uid,int index,String school,String number) {
        if(db.queryForObject("SELECT count(*) FROM besti_honor_link WHERE honor_uid=? AND member_index<>? AND school_name=? AND student_number=? AND status IN ('waiting','linked')",Long.class,uid,index,school,number)>0)
            throw fail(HttpStatus.CONFLICT,"同一名成员不能占用同一队伍的多个队员名额");
    }
    private void assign(Honor h,int index,String school,String number,String name,Long owner,long actor,String action) {
        var l=link(h,index,true);long id=num(l,"id");
        db.update("UPDATE besti_honor_link SET member_name=?,source_hash=?,source_snapshot=?,school_name=?,student_number=?,real_name=?,user_id=?,status=?,assigned_by=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",h.member(index),h.hash,write(h.raw),school,number,name,owner,owner==null?"waiting":"linked",actor,id);
        if(owner!=null) publish(h,id,owner,actor);
        audit(id,null,actor,action,"学校："+school+"；学号："+number+"；姓名："+name+"；"+(owner==null?"待实名自动关联":"已关联个人档案"));
    }
    private void publish(Honor h,long linkId,long owner,long actor) {
        boolean star=Boolean.FALSE.equals(h.raw.get("official"));
        String award=star?h.field("equivalentContestAward"):h.field("contestAward");
        boolean hasAward=BestiRecordPolicy.XCPC_AWARD_GRADES.containsKey(award) && (star || Boolean.TRUE.equals(h.raw.get("official")));
        String kind=hasAward?"award":"registration";
        String grade=hasAward?BestiRecordPolicy.XCPC_AWARD_GRADES.get(award):null;
        String level=hasAward?(star?BestiRecordPolicy.recognizedLevel(h.field("family"),h.field("tier")):h.field("awardLevel")):null;
        String result=hasAward?(star?"打星 · 等效"+award:str(level)+str(grade)):(star?"打星参赛":"参赛记录");
        String source="";
        if(h.raw.get("sources") instanceof List<?> sources && !sources.isEmpty() && sources.getFirst() instanceof Map<?,?> first) source=str(first.get("url"));
        validateUrl(source);
        LocalDate date=null;try { if(!h.field("date").isBlank()) date=LocalDate.parse(h.field("date")); } catch(Exception ignored) {}
        var prior=one("SELECT id FROM besti_member_record WHERE history_link_id=?",linkId);
        String title=h.field("event").isBlank()?h.field("title"):h.field("event");
        if(prior==null) db.update("INSERT INTO besti_member_record(user_id,kind,family,title,organization,result,team_name,start_date,source_url,notes,status,reviewed_by,created_by,award_level,award_grade,contest_stage,contest_award,history_link_id,is_starred) VALUES (?,?,?,?,?,?,?,?,?,?,'verified',?,?,?,?,?,?,?,?)",owner,kind,h.field("family"),text(title,200,true),SCHOOL,result,h.field("team"),date,source,"历史战绩归属已核验",actor,actor,level,grade,h.field("tier"),hasAward?award:null,linkId,star);
        else db.update("UPDATE besti_member_record SET user_id=?,kind=?,family=?,title=?,organization=?,result=?,team_name=?,start_date=?,source_url=?,notes='历史战绩归属已核验',status='verified',reviewed_by=?,review_note='',award_level=?,award_grade=?,contest_stage=?,contest_award=?,is_starred=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE history_link_id=?",owner,kind,h.field("family"),text(title,200,true),SCHOOL,result,h.field("team"),date,source,actor,level,grade,h.field("tier"),hasAward?award:null,star,linkId);
    }

    public Map<String,Object> preview(UserProfileVO actor,List<RosterRow> rows) { requireManager(actor);return preview(catalog(false),rows); }
    private Map<String,Object> preview(Map<String,Honor> all,List<RosterRow> rows) {
        if(rows==null || rows.isEmpty() || rows.size()>1000) throw fail(HttpStatus.BAD_REQUEST,"请导入 1 至 1000 行名单");
        var output=new ArrayList<Map<String,Object>>();var slots=new HashSet<String>();var identities=new HashSet<String>();int errors=0,matched=0,waiting=0;
        for(int i=0;i<rows.size();i++) {
            var r=rows.get(i);var out=new LinkedHashMap<String,Object>();out.put("row",i+2);
            try {
                if(r==null) throw fail(HttpStatus.BAD_REQUEST,"空白名单行");
                String school=text(r.school,100,true),number=text(r.studentNumber,50,true),name=text(r.realName,100,true);
                if(!SCHOOL.equals(school)) throw fail(HttpStatus.BAD_REQUEST,"学校须为北京电子科技学院");
                if(!number.matches("[A-Za-z0-9-]{3,50}")) throw fail(HttpStatus.BAD_REQUEST,"学号格式无效，请按文本填写真实学号");
                var h=honor(all,r.honorUid);String sourceName=h.member(r.memberIndex);
                if(!sourceName.isBlank() && !sourceName.equals(name)) throw fail(HttpStatus.CONFLICT,"姓名与榜单不一致，请先核实原始队员名单");
                if(!slots.add(h.uid+":"+r.memberIndex) || !identities.add(h.uid+":"+school+":"+number)) throw fail(HttpStatus.CONFLICT,"同一战绩的队员名额或学号重复");
                ensureUnique(h.uid,r.memberIndex,school,number);
                var l=link(h,r.memberIndex,false);
                if(!available(l) && (!school.equals(l.get("school_name")) || !number.equals(l.get("student_number")) || !name.equals(l.get("real_name")))) throw fail(HttpStatus.CONFLICT,"该名额已有不同的身份归属，须先撤销后重新登记");
                var users=db.queryForList(IDENTITY_SQL+"AND v.university_name=? AND u.number_id=? AND u.real_name=?",school,number,name);
                if(users.size()>1) throw fail(HttpStatus.CONFLICT,"实名信息匹配多个账号，需人工排查");
                Long userId=users.isEmpty()?null:num(users.getFirst(),"user_id");
                out.put("honorUid",h.uid);out.put("memberIndex",r.memberIndex);out.put("event",h.field("event"));out.put("team",h.field("team"));out.put("school",school);out.put("studentNumber",number);out.put("realName",name);out.put("userId",userId);out.put("sourceHash",h.hash);out.put("linkVersion",l==null?0:l.get("version"));out.put("status",userId==null?"waiting":"matched");out.put("message",userId==null?"未找到已通过实名审核的精确匹配账号，保留名单等待认证":"学校、学号、姓名完全匹配");
                if(userId==null) waiting++;else matched++;
            } catch(ResponseStatusException e) {errors++;out.put("status","error");out.put("message",e.getReason());}
            output.add(out);
        }
        return Map.of("items",output,"errors",errors,"matched",matched,"waiting",waiting,"token",hash(output));
    }
    @Transactional
    public Map<String,Object> importRoster(UserProfileVO actor,ImportInput input) {
        requireManager(actor);var all=catalog(true);var preview=preview(all,input.rows);
        if(((Number)preview.get("errors")).intValue()>0) throw fail(HttpStatus.BAD_REQUEST,"名单存在错误，未写入任何归属");
        if(!preview.get("token").equals(input.token)) throw fail(HttpStatus.CONFLICT,"战绩、身份或归属已变化，请重新预检");
        @SuppressWarnings("unchecked") var items=(List<Map<String,Object>>)preview.get("items");
        for(var item:items) {
            var h=honor(all,str(item.get("honorUid")));int index=((Number)item.get("memberIndex")).intValue();
            assign(h,index,str(item.get("school")),str(item.get("studentNumber")),str(item.get("realName")),(Long)item.get("userId"),actor.getUserId(),"roster_import");
            var assigned=link(h,index,false);
            db.update("UPDATE besti_honor_claim SET status=CASE WHEN claimant_id=? AND claimant_school=? AND claimant_number=? AND claimant_name=? THEN 'approved' ELSE 'rejected' END,review_note='管理员已按完整名单核验归属',reviewed_by=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE link_id=? AND status='pending'",item.get("userId"),item.get("school"),item.get("studentNumber"),item.get("realName"),actor.getUserId(),assigned.get("id"));
        }
        RatingRefreshRequested.publish(ratingEvents);
        return Map.of("matched",preview.get("matched"),"waiting",preview.get("waiting"),"count",items.size());
    }
    /** Called inside the existing verified-identity workflow. Only administrator-approved identity tuples auto-link. */
    @Transactional
    public void linkVerifiedUser(long userId) {
        Identity user;try {user=identity(userId);}catch(ResponseStatusException ignored){return;}
        var candidates=db.queryForList("SELECT * FROM besti_honor_link WHERE user_id=? OR (school_name=? AND student_number=? AND real_name=? AND status='waiting')",userId,user.school,user.number,user.name);
        if(candidates.isEmpty()) return;
        var all=catalog(true);
        for(var prior:candidates) {
            var l=one("SELECT * FROM besti_honor_link WHERE id=?",prior.get("id"));
            if(!Objects.equals(l.get("user_id"),userId) && !("waiting".equals(l.get("status")) && same(user,l))) continue;
            if(!same(user,l)) {
                db.update("UPDATE besti_honor_link SET user_id=NULL,status='waiting',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",l.get("id"));
                db.update("UPDATE besti_member_record SET status='rejected',review_note='实名信息已变化，等待重新核验',version=version+1 WHERE history_link_id=?",l.get("id"));
                audit(num(l,"id"),null,userId,"identity_changed","实名身份与原归属不一致，停止展示");
            } else if("waiting".equals(l.get("status"))) {
                var h=all.get(str(l.get("honor_uid")));
                if(h==null || !h.hash.equals(l.get("source_hash"))) continue;
                ensureUnique(h.uid,(int)num(l,"member_index"),user.school,user.number);
                assign(h,(int)num(l,"member_index"),user.school,user.number,user.name,userId,num(l,"assigned_by"),"identity_verified");
            }
        }
        RatingRefreshRequested.publish(ratingEvents);
    }
    public List<Map<String,Object>> links(UserProfileVO actor) {
        requireManager(actor);
        var rows=db.queryForList("SELECT * FROM besti_honor_link WHERE status IN ('linked','waiting') ORDER BY updated_at DESC LIMIT 1000");
        var all=catalog(false);
        for(var row:rows) {var h=all.get(str(row.get("honor_uid")));row.put("sourceChanged",h==null || !h.hash.equals(row.get("source_hash")));row.put("honor",h==null?readSnapshot(str(row.get("source_snapshot"))):summary(h));row.remove("source_snapshot");}
        return rows;
    }
    public List<Map<String,Object>> audit(UserProfileVO actor,long linkId) {
        requireManager(actor);return db.queryForList("SELECT id,actor_id,action,detail,created_at FROM besti_honor_audit WHERE link_id=? ORDER BY id DESC",linkId);
    }
    @Transactional
    public void revoke(UserProfileVO actor,long linkId,Revoke input) {
        requireManager(actor);catalog(true);var l=one("SELECT * FROM besti_honor_link WHERE id=?",linkId);
        if(l==null) throw fail(HttpStatus.NOT_FOUND,"归属记录不存在");
        if(input.version!=num(l,"version") || !Set.of("linked","waiting").contains(str(l.get("status")))) throw fail(HttpStatus.CONFLICT,"归属已变化，请刷新");
        String note=text(input.note,1000,true);
        db.update("UPDATE besti_member_record SET status='rejected',review_note=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE history_link_id=?","历史归属撤销："+note,linkId);
        db.update("UPDATE besti_honor_link SET user_id=NULL,status='revoked',school_name='',student_number='',real_name='',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",linkId);
        db.update("UPDATE besti_honor_claim SET status='rejected',review_note=?,reviewed_by=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE link_id=? AND status='approved'","历史归属撤销："+note,actor.getUserId(),linkId);
        audit(linkId,null,actor.getUserId(),"revoke",note+"；原身份："+l.get("school_name")+" / "+l.get("student_number")+" / "+l.get("real_name"));
        RatingRefreshRequested.publish(ratingEvents);
    }

    /** Do not expose a linked record after its owner's verified identity has changed. */
    public static String visibleRecordSql(String alias) {
        return "("+alias+".history_link_id IS NULL OR EXISTS (SELECT 1 FROM besti_honor_link hl JOIN users hu ON hu.user_id=hl.user_id JOIN major hm ON hm.major_id=hu.major_id JOIN school hs ON hs.school_id=hm.school_id JOIN university hv ON hv.university_id=hs.university_id WHERE hl.id="+alias+".history_link_id AND hl.user_id="+alias+".user_id AND hl.status='linked' AND hu.account_status='normal' AND hu.real_name=hl.real_name AND hu.number_id=hl.student_number AND hv.university_name=hl.school_name))";
    }
}
