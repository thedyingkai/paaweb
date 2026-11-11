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
import { awardLevels, awardGrades } from "@/lib/besti-members";
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
  contestAward?: string;
  equivalentContestAward?: string;
  awardLevel?: string;
  awardGrade?: string;
  equivalentAwardLevel?: string;
  equivalentAwardGrade?: string;
  official?: boolean | null;
  rank?: number;
  officialRank?: number;
  totalTeams?: number;
  totalOfficialTeams?: number;
  teamCountSource?: string;
  location?: string;
  equivalentAward?: string;
  equivalentRank?: number;
  equivalentAwardChecked?: boolean;
  equivalentAwardBasis?: string;
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
const awardName = (r: Honor) => r.awardLevel && r.awardGrade ? r.awardLevel + r.awardGrade : r.award || '';
const contestName = (r: Honor) => r.contestAward || r.award || '';
const equivalentContestName = (r: Honor) => r.equivalentContestAward || r.equivalentAward || '';
const awardColor = (name: string) => name === '金牌' ? 'gold' : name === '银牌' ? 'blue' : name === '铜牌' ? 'orange' : 'default';
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
  const [mode, setMode] = useState("all");
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
            (mode === "all" || (mode === "awards" && !!awardName(r)) || (mode === "equivalent" && !!equivalentContestName(r))) &&
            (keyword.trim() === "" ||
              [r.event, r.title, r.team, contestName(r), equivalentContestName(r), awardName(r), ...(r.members || [])]
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
  const download = () => {
    const fields = [
      "年份",
      "日期",
      "赛事",
      "类别",
      "赛事阶段",
      "队伍",
      "队员",
      "赛事奖项",
      "学校认定奖项",
      "打星等效赛事奖项",
      "正式参赛",
      "总排名",
      "正式排名",
      "总队伍数",
      "正式队伍数",
      "打星等效正式名次",
      "等效奖项计算依据",
      "过题数",
      "来源",
    ];
    const rows = filtered.map((r) => [
      r.academicYear,
      r.date,
      r.event || r.title,
      r.family,
      tierNames[r.tier || ''] || r.tier,
      r.team,
      r.members?.join("、"),
      contestName(r),
      awardName(r),
      equivalentContestName(r),
      r.official === true
        ? "正式"
        : r.official === false
          ? "打星 / 非正式"
          : "未注明",
      r.rank,
      r.officialRank,
      r.totalTeams,
      r.totalOfficialTeams,
      r.equivalentRank,
      r.equivalentAwardBasis,
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
        <div style={{ maxWidth: 1500, margin: "0 auto" }}>
          <Row gutter={[16, 16]}>
            {[
              { label: '正式获奖（队次）', value: records.filter(r => awardName(r)).length },
              { label: '打星等效获奖（队次）', value: records.filter(r => equivalentContestName(r)).length },
              { label: '收录比赛', value: meta.eventCount || 0 },
              { label: '参赛记录（队次）', value: records.length },
            ].map(item => <Col xs={12} md={6} key={item.label}><Card><Statistic title={item.label} value={item.value} /></Card></Col>)}
          </Row>
          <Card style={{ marginTop: 20 }}>
            <Table rowKey="level" pagination={false} size="small" scroll={{ x: 620 }}
              dataSource={awardLevels.map(level => ({ level }))}
              columns={[
                { title: '级别', dataIndex: 'level', width: 100 },
                ...awardGrades.map(grade => ({ title: grade, key: grade, render: (_: unknown, row: { level: string }) => {
                  const official = records.filter(r => r.official !== false && r.awardLevel === row.level && r.awardGrade === grade).length;
                  const stars = records.filter(r => r.official === false && r.equivalentAwardLevel === row.level && r.equivalentAwardGrade === grade).length;
                  return <span><strong>{official}</strong><span style={{ color: stars ? '#1677ff' : '#94a3b8' }}>（+{stars}）</span></span>;
                } })),
              ]}
            />
            <p style={{ margin: '14px 0 0', color: '#64748b' }}>学校认定统计：正式奖项（+打星等效奖项）。ICPC / CCPC 区域赛、总决赛按国家级，邀请赛按省部级；赛事金、银、铜牌分别认定为一、二、三等奖，优胜奖认定为优秀奖。蓝桥杯国赛按省部级，省赛按校级。</p>
          </Card>
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
                。同场同队合并，奖项按队次统计；括号内单列打星队按榜单奖牌线计算的等效奖项，不计入正式获奖。排名分母为对应榜单的队伍总数；预选赛不计奖牌。
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
                  { value: "awards", label: "正式获奖记录" },
                  { value: "equivalent", label: "打星等效获奖" },
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
              scroll={{ x: 1450 }}
              locale={{ emptyText: <Empty description="当前筛选下暂无记录" /> }}
              expandable={{
                expandedRowRender: (r) => (
                  <div>
                    {r.members && r.members.length > 0 && (
                      <p>队员：{r.members.join("、")}</p>
                    )}
                    {r.notes && <p>{r.notes}</p>}
                    {(contestName(r) || equivalentContestName(r)) && <p>赛事奖项：{contestName(r) || `打星等效${equivalentContestName(r)}`}。{r.official !== false && awardName(r) && <>学校认定：{awardName(r)}。</>}</p>}
                    <p>总队伍数：{r.totalTeams ?? '待核实'}；正式队伍数：{r.totalOfficialTeams ?? '待核实'}。来源：{r.teamCountSource || '待核实'}。</p>
                    {r.equivalentAwardBasis && <p>打星等效名次：{r.equivalentRank}；{r.equivalentAwardBasis}</p>}
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

                {
                  title: "比赛",
                  render: (_, r) => (
                    <div style={{ minWidth: 260 }}>
                      <strong><a href={r.sources?.find(source => source.name === 'CPCFinder')?.url || r.sources?.[0]?.url} target="_blank" rel="noopener noreferrer">{r.event || r.title}</a></strong>
                      <div style={{ marginTop: 6 }}>
                        <Tag>{r.family || "校队荣誉"}</Tag>
                        {r.tier && <Tag>{tierNames[r.tier] || r.tier}</Tag>}

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
                  title: "赛事奖项 / 身份",
                  render: (_, r) => (
                    <Space orientation="vertical" size={3}>
                      {contestName(r) ? <Tag color={awardColor(contestName(r))}>{contestName(r)}</Tag>
                        : r.official !== false && <span style={{ color: "#64748b" }}>参赛记录</span>}
                      {r.official === false && <>
                        <Tag>打星</Tag>
                        {equivalentContestName(r) ? <Tag color={awardColor(equivalentContestName(r))}>（等效{equivalentContestName(r)}）</Tag>
                          : <span style={{ fontSize: 12, color: '#64748b' }}>{r.equivalentAwardChecked ? '未达到奖牌线' : '等效奖项待核实'}</span>}
                      </>}
                    </Space>
                  ),
                },
                {
                  title: '学校认定',
                  width: 180,
                  render: (_, r) => r.official === false ? '—' : awardName(r) || '—',
                },
                { title: '时间', dataIndex: 'date', width: 110 },
                { title: '地点', dataIndex: 'location', width: 75, render: v => v || '—' },
                {
                  title: '正式排名 / 正式队伍数',
                  width: 160,
                  render: (_, r) => r.official === false ? <span title="打星队不占正式名次">*（打星）</span> : `${r.officialRank ?? '—'} / ${r.totalOfficialTeams ?? '待核实'}`,
                },
                {
                  title: '全部排名 / 总队伍数',
                  width: 150,
                  render: (_, r) => `${r.rank ?? '—'} / ${r.totalTeams ?? '待核实'}`,
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
            个人奖项与职务可在 <Link href="/team-space">我的参赛档案</Link>{" "}
            中维护，核验后展示于个人主页。
          </p>
        </div>
      </main>
    </>
  );
}
