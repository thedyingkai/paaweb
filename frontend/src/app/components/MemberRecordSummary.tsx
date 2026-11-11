"use client";
import { useEffect, useState } from "react";
import { Alert, Card, Empty, List, Tag } from "antd";
import Link from "next/link";
import { positionStatusLabels } from "@/lib/besti-members";
import { teamApi, teamError, kindNames } from "@/lib/besti";
type RecordItem = {
  id: number;
  kind: string;
  title: string;
  result: string;
  start_date?: string;
  end_date?: string;
  display_title?: string;
  position_status?: string;
  source_url?: string;
};
export default function MemberRecordSummary({
  userId,
}: {
  userId: string | number;
}) {
  const [items, setItems] = useState<RecordItem[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    setLoading(true);
    teamApi<{ items: RecordItem[] }>(
      `/public-records?userId=${encodeURIComponent(userId)}`,
    )
      .then((r) => setItems(r.items))
      .catch((e) => setError(teamError(e)))
      .finally(() => setLoading(false));
  }, [userId]);
  return (
    <Card
      title="校队奖项与职务"
      extra={<Link href="/team-space">维护我的档案</Link>}
      style={{ marginTop: 24 }}
    >
      {error ? (
        <Alert type="warning" title={error} />
      ) : (
        <List
          loading={loading}
          dataSource={items}
          locale={{ emptyText: <Empty description="暂无已核验的校队记录" /> }}
          renderItem={(r) => (
            <List.Item>
              <List.Item.Meta
                title={
                  <>
                    <Tag color="green">已核验</Tag>
                    <Tag>{kindNames[r.kind]}</Tag>
                    {r.display_title || r.title}
                    {r.kind === "position" && <Tag color={r.position_status === "former" ? "default" : "blue"}>{positionStatusLabels[r.position_status || "unknown"]}</Tag>}
                    {r.result && ` · ${r.result}`}
                  </>
                }
                description={
                  <>
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
