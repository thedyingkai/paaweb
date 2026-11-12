package com.cwnupaa.backend.service;

import com.cwnupaa.backend.utils.BestiRecordPolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDate;
import java.util.*;

/** Reads only the applicant's existing records; never fetches or exposes OJ credentials. */
public final class RecruitmentPrefillService {
    private RecruitmentPrefillService() {}
    private static String text(Map<String,Object> row,String key) { return Objects.toString(row.get(key),""); }
    private static String lines(Collection<String> rows,String empty,int max) {
        if(rows.isEmpty())return empty;
        var out=new StringBuilder();
        for(String row:rows){if(out.length()+row.length()+1>max)break;if(!out.isEmpty())out.append('\n');out.append(row);}
        return out.toString();
    }
    public static Map<String,String> load(JdbcTemplate db,long userId) {
        var awards=new ArrayList<String>();
        for(var row:db.queryForList("SELECT r.title,r.start_date,r.contest_award,r.result,r.is_starred FROM besti_member_record r WHERE r.user_id=? AND r.kind='award' AND r.status='verified' AND "+HistoricalAwardService.visibleRecordSql("r")+" ORDER BY r.start_date DESC NULLS LAST,r.id DESC LIMIT 30",userId)) {
            String award=text(row,"contest_award");if(award.isEmpty())award=text(row,"result");
            if(Boolean.TRUE.equals(row.get("is_starred")))award="打星（等效"+award+"）";
            awards.add(text(row,"start_date")+" "+text(row,"title")+" · "+award);
        }
        var positions=new LinkedHashSet<String>();
        String roleSql="SELECT department AS organization,position AS title,start_date,end_date,term FROM association_member WHERE user_id=? UNION ALL SELECT organization,title,start_date,end_date,NULL AS term FROM besti_member_record WHERE user_id=? AND kind='position' AND status='verified'";
        for(var row:db.queryForList(roleSql,userId,userId)) {
            var fallback=BestiRecordPolicy.termDates(text(row,"term"));
            LocalDate start=row.get("start_date")==null?fallback[0]:LocalDate.parse(text(row,"start_date"));
            LocalDate end=row.get("end_date")==null?fallback[1]:LocalDate.parse(text(row,"end_date"));
            String label=BestiRecordPolicy.positionLabel(text(row,"organization"),text(row,"title"),BestiRecordPolicy.positionStatus(start,end,BestiRecordPolicy.today()));
            positions.add(label+(start==null?"":"（"+start+" 至 "+(end==null?"至今":end)+"）"));
        }
        var bindings=db.queryForList("SELECT newcoder_id,luogu_id,codeforces_id,atcoder_id,cwnuoj_id,qoj_id,ucup_id FROM oj_user_account WHERE user_id=?",userId);
        Map<String,Object> accounts=bindings.isEmpty()?Map.of():bindings.getFirst();
        var counts=db.queryForList("SELECT p.oj_name,count(r.practice_problem_id) AS solved FROM oj_platform p LEFT JOIN oj_effective_practice_record r ON r.source_id=p.oj_id AND r.user_id=? WHERE p.oj_id<>7 GROUP BY p.oj_id,p.oj_name ORDER BY p.oj_id",userId);
        var platforms=new ArrayList<String>();
        var fields=Map.of("NowCoder","newcoder_id","Luogu","luogu_id","CodeForces","codeforces_id","AtCoder","atcoder_id","CWNU","cwnuoj_id","QOJ","qoj_id");
        for(var row:counts) {
            String platform=text(row,"oj_name"),account=text(accounts,fields.getOrDefault(platform,""));
            if("QOJ".equals(platform)) { platform="QOJ / UCup";String ucup=text(accounts,"ucup_id");if(!ucup.isBlank())account+=(account.isBlank()?"":" / ")+ucup; }
            long solved=((Number)row.get("solved")).longValue();
            if(!account.isBlank() || solved>0)platforms.add(platform+"："+(account.isBlank()?"未绑定账号":account)+"；累计通过 "+solved+" 题");
        }
        return Map.of("experience",lines(awards,"暂无已核验的竞赛经历",4000),"studentWork",lines(positions,"无",2000),
                "platformAccounts",lines(platforms,"暂无已绑定的平台账号及题量记录",4000));
    }
}
