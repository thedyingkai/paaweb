"use client";
import { useEffect, useState } from "react";
import { Alert, Card, Empty, List, Tag } from "antd";
import Link from "next/link";
import { positionStatusLabels, contestStageLabels } from "@/lib/besti-members";
import { teamApi, teamError, kindNames } from "@/lib/besti";
type RecordItem = {
  id: number;
  kind: string;
  title: string;
  result: string;
  family?: string;
  contest_stage?: string;
  contest_award?: string;
  start_date?: string;
  end_date?: string;
  display_title?: string;
  position_status?: string;
  source_url?: string;
  history_link_id?: number;
  is_starred?: boolean;
};
export default function MemberRecordSummary({
  userId,
}: {
  userId: string | number;
}) {
  const [items, setItems] = useState<RecordItem[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [counts,setCounts] = useState({awards:0,starred:0});
  useEffect(() => {
    setLoading(true);
    teamApi<{ items: RecordItem[]; awards: number; starred: number }>(
      `/public-records?userId=${encodeURIComponent(userId)}`,
    )
      .then((r) => {setItems(r.items);setCounts({awards:r.awards||0,starred:r.starred||0});})
      .catch((e) => setError(teamError(e)))
      .finally(() => setLoading(false));
  }, [userId]);
  return (
    <Card
      title={<>个人奖项、职务与经历 <Tag>获奖 {counts.awards}{counts.starred>0?`（+${counts.starred} 打星）`:''}</Tag></>}
      extra={<Link href="/team-space">维护我的档案</Link>}
      style={{ marginTop: 24 }}
    >
      {error ? (
        <Alert type="warning" title={error} />
      ) : (
        <List
          loading={loading}
          dataSource={items}
          locale={{ emptyText: <Empty description="暂无已核验的个人记录" /> }}
          renderItem={(r) => (
            <List.Item>
              <List.Item.Meta
                title={
                  <>
                    <Tag color="green">已核验</Tag>
                    <Tag>{kindNames[r.kind]}</Tag>
                    {r.history_link_id && <Tag color="blue">历史战绩</Tag>}
                    {r.display_title || r.title}
                    {r.kind === "position" && <Tag color={r.position_status === "former" ? "default" : "blue"}>{positionStatusLabels[r.position_status || "unknown"]}</Tag>}
                    {r.is_starred ? ` · 打星${r.contest_award?`（等效${r.contest_award}）`:'参赛'}` : r.contest_award ? ` · ${contestStageLabels[r.contest_stage || ''] || ''}${r.contest_award}` : r.result && ` · ${r.result}`}
                  </>
                }
                description={
                  <>
                    {r.contest_award && !r.is_starred && <div>学校认定：{r.result}</div>}
                    {[r.start_date, r.end_date].filter(Boolean).join(" — ")}
                    {r.source_url && (
                      <>
                        {" "}
                        ·{" "}
                        <a
                          href={r.source_url}
                          target="_blank"
                          rel="noopener noreferrer"
                        >
                          证明来源
                        </a>
                      </>
                    )}
                  </>
                }
              />
            </List.Item>
          )}
        />
      )}
    </Card>
  );
}
