"use client";
import { useEffect, useMemo, useState } from "react";
import {
  Alert,
  Button,
  Card,
  Col,
  Empty,
  Input,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from "antd";
import {
  DownloadOutlined,
  SearchOutlined,
  TrophyOutlined,
} from "@ant-design/icons";
import axios from "axios";
import Link from "next/link";
import SiteHeader from "../components/SiteHeader";
type Source = { name: string; url: string };
type Honor = {
  uid: string;
  title: string;
  event?: string;
  academicYear?: string;
  date?: string;
  family?: string;
  tier?: string;
  team?: string;
  members?: string[];
  award?: string;
  official?: boolean | null;
  rank?: number;
  officialRank?: number;
  solved?: number;
  penalty?: number;
  imageUrl?: string;
  notes?: string;
  sources?: Source[];
};
type Meta = {
  updatedAt?: string;
  coverage?: string;
  recordCount?: number;
  eventCount?: number;
};
const medals = ["金牌", "银牌", "铜牌"];
const tierNames: Record<string, string> = {
  regional: "区域赛",
  invitational: "邀请赛",
  final: "总决赛",
  preliminary: "网络预选赛",
  ladies: "女生赛",
  other: "其他",
};
export default function SchoolHonorsClient() {
  const [records, setRecords] = useState<Honor[]>([]);
  const [meta, setMeta] = useState<Meta>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [year, setYear] = useState("all");
  const [family, setFamily] = useState("all");
  const [mode, setMode] = useState("awards");
  const [keyword, setKeyword] = useState("");
  useEffect(() => {
    Promise.all([
      axios.get("/api/intro/honors"),
      axios.get("/api/intro/honors_meta"),
    ])
      .then(([a, b]) => {
        if (a.data.code !== 200) throw new Error(a.data.msg);
        const parsed = JSON.parse(a.data.data?.content || "[]");
        if (!Array.isArray(parsed)) throw new Error("成绩档案格式异常");
        setRecords(parsed);
        setMeta(JSON.parse(b.data.data?.content || "{}"));
      })
      .catch((e) => setError(e.message || "加载失败"))
      .finally(() => setLoading(false));
  }, []);
  const years = useMemo(
    () =>
      [...new Set(records.map((r) => r.academicYear).filter(Boolean))]
        .sort()
        .reverse(),
    [records],
  );
  const filtered = useMemo(
    () =>
      records
        .filter(
          (r) =>
            (year === "all" || r.academicYear === year) &&
            (family === "all" || r.family === family) &&
            (mode === "all" || !!r.award) &&
            (keyword.trim() === "" ||
              [r.event, r.title, r.team, ...(r.members || [])]
                .join(" ")
                .toLowerCase()
                .includes(keyword.trim().toLowerCase())),
        )
        .sort((a, b) =>
          (b.date || b.academicYear || "").localeCompare(
            a.date || a.academicYear || "",
          ),
        ),
    [records, year, family, mode, keyword],
  );
  const medalsCount = medals.map(
    (m) => records.filter((r) => r.award === m).length,
  );
  const download = () => {
    const fields = [
      "年份",
      "日期",
      "赛事",
      "类别",
      "队伍",
      "队员",
      "奖项",
      "正式参赛",
      "总排名",
      "正式排名",
      "过题数",
      "来源",
    ];
    const rows = filtered.map((r) => [
      r.academicYear,
      r.date,
      r.event || r.title,
      r.family,
      r.team,
      r.members?.join("、"),
      r.award || "",
      r.official === true
        ? "正式"
        : r.official === false
          ? "打星 / 非正式"
          : "未注明",
      r.rank,
      r.officialRank,
      r.solved,
      r.sources?.map((s) => s.url).join(" | "),
    ]);
    const cell = (x: unknown) => {
      let s = String(x ?? "");
      if (/^[=+@-]/.test(s)) s = "'" + s;
      return '"' + s.replaceAll('"', '""') + '"';
    };
    const blob = new Blob(
      [
        "\uFEFF" +
          [fields, ...rows].map((r) => r.map(cell).join(",")).join("\r\n"),
      ],
      { type: "text/csv;charset=utf-8" },
    );
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "BESTI-ACM-成绩档案.csv";
    a.click();
    URL.revokeObjectURL(url);
  };
  return (
    <>
      <SiteHeader />
      <main className="content-page-shell">
        <section className="content-hero content-hero-centered">
          <Tag className="bits-pill" icon={<TrophyOutlined />}>
            BESTI ACM · HONORS
          </Tag>
          <Typography.Title className="content-hero-title">
            校队荣誉与参赛档案
          </Typography.Title>
          <Typography.Paragraph>
            北京电子科技学院 · 每一场比赛，都有迹可循。
          </Typography.Paragraph>
        </section>
        <div style={{ maxWidth: 1240, margin: "0 auto" }}>
          <Row gutter={[16, 16]}>
            {medals.map((m, i) => (
              <Col xs={8} md={6} key={m}>
                <Card>
                  <Statistic
                    title={m + "（队次）"}
                    value={medalsCount[i]}
                    styles={{
                      content: { color: ["#a97b19", "#64748b", "#9c653b"][i] },
                    }}
                  />
                </Card>
              </Col>
            ))}
            <Col xs={24} md={6}>
              <Card>
                <Statistic
                  title="收录参赛记录（队次）"
                  value={records.length}
                />
              </Card>
            </Col>
          </Row>
          <Alert
            style={{ margin: "24px 0" }}
            type="info"
            showIcon
            title={meta.coverage || "根据公开榜单整理，持续补充历史成绩"}
            description={
              <>
                来源：
                <a
                  href="https://board.xcpcio.com/"
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  XCPCIO
                </a>
                、
                <a
                  href="https://cpcfinder.com/school/611fb81c-32da-4445-970b-a12d69e47039"
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  CPCFinder
                </a>
                、
                <a
                  href={
                    "https://heimaom.cn/#/school/" +
                    encodeURIComponent("北京电子科技学院")
                  }
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  XCPCRating
                </a>
                。同场同队合并，奖牌按队次统计；打星队伍、预选赛和未确认奖项不计入奖牌数。
                {meta.updatedAt && ` 整理日期：${meta.updatedAt}。`}
              </>
            }
          />
          {error && (
            <Alert type="error" title={error} style={{ marginBottom: 16 }} />
          )}
          <Card>
            <Space wrap style={{ marginBottom: 20 }}>
              <Select
                aria-label="记录范围"
                value={mode}
                onChange={setMode}
                style={{ width: 160 }}
                options={[
                  { value: "awards", label: "已确认获奖" },
                  { value: "all", label: "全部参赛记录" },
                ]}
              />
              <Select
                aria-label="年份"
                value={year}
                onChange={setYear}
                style={{ width: 120 }}
                options={[
                  { value: "all", label: "全部年份" },
                  ...years.map((y) => ({ value: y!, label: y + " 年" })),
                ]}
              />
              <Select
                aria-label="赛事系列"
                value={family}
                onChange={setFamily}
                style={{ width: 130 }}
                options={[
                  { value: "all", label: "全部赛事" },
                  ...["ICPC", "CCPC", "天梯赛", "联盟主办"].map((value) => ({
                    value,
                    label: value,
                  })),
                ]}
              />
              <Input
                prefix={<SearchOutlined />}
                allowClear
                placeholder="赛事、队伍或队员"
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                style={{ width: 250 }}
              />
              <Button icon={<DownloadOutlined />} onClick={download}>
                导出当前结果
              </Button>
            </Space>
            <Table
              rowKey="uid"
              loading={loading}
              dataSource={filtered}
              pagination={{
                pageSize: 20,
                showSizeChanger: true,
                showTotal: (n) => `共 ${n} 条记录`,
              }}
              scroll={{ x: 1050 }}
              locale={{ emptyText: <Empty description="当前筛选下暂无记录" /> }}
              expandable={{
                expandedRowRender: (r) => (
                  <div>
                    {r.members && r.members.length > 0 && (
                      <p>队员：{r.members.join("、")}</p>
                    )}
                    {r.notes && <p>{r.notes}</p>}
                    <p>
                      榜单原始来源：
                      <Space wrap>
                        {r.sources?.map((s) => (
                          <a
                            key={s.name + s.url}
                            href={s.url}
                            target="_blank"
                            rel="noopener noreferrer"
                          >
                            {s.name} ↗
                          </a>
                        ))}
                      </Space>
                    </p>
                    {r.imageUrl && (
                      <img
                        src={r.imageUrl}
                        alt={r.title}
                        style={{ maxWidth: 500, width: "100%" }}
                      />
                    )}
                  </div>
                ),
              }}
              columns={[
                { title: "年份", dataIndex: "academicYear", width: 80 },
                {
                  title: "比赛",
                  render: (_, r) => (
                    <div style={{ minWidth: 260 }}>
                      <strong>{r.event || r.title}</strong>
                      <div style={{ marginTop: 6 }}>
                        <Tag>{r.family || "校队荣誉"}</Tag>
                        {r.tier && <Tag>{tierNames[r.tier] || r.tier}</Tag>}
                        <span style={{ color: "#64748b" }}>{r.date}</span>
                      </div>
                    </div>
                  ),
                },
                {
                  title: "队伍与队员",
                  render: (_, r) => (
                    <div style={{ minWidth: 200 }}>
                      {r.team || "—"}
                      <div
                        style={{ fontSize: 12, color: "#64748b", marginTop: 6 }}
                      >
                        {r.members?.join("、") || "榜单未提供队员名单"}
                      </div>
                    </div>
                  ),
                },
                {
                  title: "奖项 / 身份",
                  render: (_, r) => (
                    <Space orientation="vertical" size={3}>
                      {r.award ? (
                        <Tag
                          color={
                            r.award === "金牌"
                              ? "gold"
                              : r.award === "银牌"
                                ? "blue"
                                : "orange"
                          }
                        >
                          {r.award}
                        </Tag>
                      ) : (
                        <span style={{ color: "#64748b" }}>参赛记录</span>
                      )}
                      {r.official === false && <Tag>打星 / 非正式</Tag>}
                    </Space>
                  ),
                },
                {
                  title: "总 / 正式排名",
                  render: (_, r) => (
                    <span>
                      {r.rank ?? "—"} / {r.officialRank ?? "—"}
                    </span>
                  ),
                },
                { title: "过题", dataIndex: "solved", render: (v) => v ?? "—" },
                {
                  title: "来源",
                  render: (_, r) => (
                    <Space orientation="vertical" size={2}>
                      {r.sources?.map((s) => (
                        <a
                          key={s.name + s.url}
                          href={s.url}
                          target="_blank"
                          rel="noopener noreferrer"
                        >
                          {s.name} ↗
                        </a>
                      ))}
                    </Space>
                  ),
                },
              ]}
            />
          </Card>
          <p style={{ textAlign: "center", marginTop: 28 }}>
            个人奖项与职务可在 <Link href="/team-space">我的校队档案</Link>{" "}
            中维护，核验后展示于个人主页。
          </p>
        </div>
      </main>
    </>
  );
}
