"use client";

import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Button,
  Card,
  Empty,
  Form,
  Input,
  Modal,
  Pagination,
  Popconfirm,
  Select,
  Space,
  Spin,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from "antd";
import Link from "next/link";
import axios from "axios";
import SiteHeader from "../components/SiteHeader";
import { memberRoles, memberRoleKey, positionStatusLabels, awardLevels, awardGrades, awardStages, xcpcAwards, xcpcAwardGrades, contestStageLabels, academicTermDates, currentAcademicTerm } from "@/lib/besti-members";
import {
  familyOptions,
  kindNames,
  organizedFamilies,
  statusNames,
  teamApi,
  teamError,
  TeamIdentity,
  voluntaryFamilies,
} from "@/lib/besti";

type Entry = {
  id: number;
  version: number;
  user_id: number;
  owner_name: string;
  kind: string;
  family: string;
  title: string;
  organization: string;
  result: string;
  team_name: string;
  start_date?: string;
  end_date?: string;
  display_title?: string;
  position_status?: string;
  award_level?: string;
  award_grade?: string;
  contest_stage?: string;
  contest_award?: string;
  source_url: string;
  notes: string;
  status: string;
  review_note: string;
};
type RecordForm = {
  id?: number;
  version?: number;
  kind: string;
  family?: string;
  title: string;
  organization?: string;
  result?: string;
  teamName?: string;
  startDate?: string;
  endDate?: string;
  sourceUrl?: string;
  notes?: string;
  userIds?: number[];
  positionKey?: string;
  awardLevel?: string;
  awardGrade?: string;
  contestStage?: string;
  contestAward?: string;
};
export default function TeamSpaceClient() {
  const [identity, setIdentity] = useState<TeamIdentity>();
  const [accessError, setAccessError] = useState("");
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState("mine");
  const [items, setItems] = useState<Entry[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [open, setOpen] = useState(false);
  const [roster, setRoster] = useState(false);
  const [saving, setSaving] = useState(false);
  const [options, setOptions] = useState<{ label: string; value: number }[]>(
    [],
  );
  const [review, setReview] = useState<Entry>();
  const [reviewNote, setReviewNote] = useState("");
  const [form] = Form.useForm<RecordForm>();
  const kind = Form.useWatch("kind", form);
  const family = Form.useWatch("family", form);
  const stage = Form.useWatch("contestStage", form);
  const contestAward = Form.useWatch("contestAward", form);
  const isXcpc = family === 'ICPC' || family === 'CCPC';
  const fixedLevel = awardStages(family).find(option => option.value === stage)?.level;
  useEffect(() => {
    if (kind === 'award' && fixedLevel) form.setFieldsValue({ awardLevel: fixedLevel });
  }, [kind, fixedLevel, form]);
  useEffect(() => {
    if (kind === 'award' && isXcpc) form.setFieldsValue({ awardGrade: xcpcAwardGrades[contestAward || ''] });
  }, [kind, isXcpc, contestAward, form]);
  useEffect(() => {
    teamApi<TeamIdentity>("/me")
      .then(setIdentity)
      .catch((e) => setAccessError(teamError(e)))
      .finally(() => setLoading(false));
  }, []);
  const load = useCallback(async () => {
    if (!identity) return;
    setLoading(true);
    try {
      const r = await teamApi<{ items: Entry[]; total: number }>(
        `/records?scope=${tab}&page=${page}`,
      );
      setItems(r.items);
      setTotal(r.total);
    } catch (e) {
      message.error(teamError(e));
    } finally {
      setLoading(false);
    }
  }, [identity, tab, page]);
  useEffect(() => {
    void load();
  }, [load]);
  const edit = (entry?: Entry, batch = false) => {
    form.resetFields();
    setRoster(batch);
    form.setFieldsValue(
      entry
        ? {
            id: entry.id,
            version: entry.version,
            kind: entry.kind,
            family: entry.family,
            title: entry.title,
            organization: entry.organization,
            result: entry.result,
            teamName: entry.team_name,
            startDate: entry.start_date,
            endDate: entry.end_date,
            positionKey: memberRoleKey(entry.organization, entry.title),
            awardLevel: entry.award_level,
            awardGrade: entry.award_grade,
            contestStage: entry.contest_stage,
            contestAward: entry.contest_award,
            sourceUrl: entry.source_url,
            notes: entry.notes,
          }
        : { kind: batch ? "registration" : "award", family: "ICPC" },
    );
    setOpen(true);
  };
  const submit = async (values: RecordForm) => {
    setSaving(true);
    try {
      const role = memberRoles.find(role => role.value === values.positionKey);
      const record = {
        ...values,
        ...(values.kind === 'position' && role ? { title: role.title, organization: role.organization } : {}),
        startDate: values.startDate || null,
        endDate: values.endDate || null,
      };
      await teamApi(
        roster ? "/roster" : "/records",
        "POST",
        roster ? { record, userIds: values.userIds } : record,
      );
      message.success("已保存，等待核验");
      setOpen(false);
      await load();
    } catch (e) {
      message.error(teamError(e));
    } finally {
      setSaving(false);
    }
  };
  const audit = async (approved: boolean) => {
    if (!review) return;
    setSaving(true);
    try {
      await teamApi(`/records/${review.id}/review`, "POST", {
        version: review.version,
        approved,
        note: reviewNote,
      });
      setReview(undefined);
      await load();
      message.success("核验结果已保存");
    } catch (e) {
      message.error(teamError(e));
    } finally {
      setSaving(false);
    }
  };
  return (
    <>
      <SiteHeader />
      <main className="content-page-shell">
        <section className="content-hero content-hero-centered">
          <Tag>BESTI ACM · MEMBER SPACE</Tag>
          <Typography.Title className="content-hero-title">
            我的校队档案
          </Typography.Title>
          <Typography.Paragraph>
            维护获奖经历、职务与任期，登记你参加的比赛。
          </Typography.Paragraph>
        </section>
        <div style={{ maxWidth: 1200, margin: "0 auto" }}>
          {accessError ? (
            <Alert
              type="info"
              showIcon
              title={accessError}
              description={
                <Space>
                  <Link href="/login">登录账号</Link>
                  <Link href="/register">注册并提交身份审核</Link>
                </Space>
              }
            />
          ) : !identity ? (
            <Spin />
          ) : (
            <>
              <Alert
                type="info"
                showIcon
                title="校队统一组织与个人自愿登记"
                description={
                  <>
                    联盟主办比赛及 ICPC、CCPC、天梯赛由队长统一组织。
                    {voluntaryFamilies.join("、")}
                    由本人自愿申请登记。获奖、职务核验后展示在个人主页，修改后重新核验；参赛登记与补充材料仅本人和审核人员可见。职务记录不改变账号权限。
                  </>
                }
              />
              <Card style={{ marginTop: 24 }}>
                <Tabs
                  activeKey={tab}
                  onChange={(v) => {
                    setTab(v);
                    setPage(1);
                  }}
                  items={[
                    { key: "mine", label: "我的记录" },
                    ...(identity.moderator
                      ? [{ key: "review", label: "待核验记录" }]
                      : []),
                  ]}
                />
                <Space wrap style={{ marginBottom: 20 }}>
                  <Button type="primary" onClick={() => edit()}>
                    新增我的记录
                  </Button>
                  {identity.coordinator && (
                    <Button onClick={() => edit(undefined, true)}>
                      队长统一登记
                    </Button>
                  )}
                  <Link href="/community">进入校队交流区</Link>
                  <Link href="/competitions">查看赛事范围</Link>
                </Space>
                <Table
                  rowKey="id"
                  loading={loading}
                  dataSource={items}
                  pagination={false}
                  scroll={{ x: 850 }}
                  locale={{
                    emptyText: (
                      <Empty description="暂无记录，添加第一段校队经历" />
                    ),
                  }}
                  expandable={{
                    expandedRowRender: (r) => (
                      <div style={{ whiteSpace: "pre-wrap" }}>
                        {r.organization && (
                          <p>组织 / 主办方：{r.organization}</p>
                        )}
                        {r.notes && <p>{r.notes}</p>}
                        {r.source_url && (
                          <p>
                            <a
                              href={r.source_url}
                              target="_blank"
                              rel="noopener noreferrer"
                            >
                              查看证明来源
                            </a>
                          </p>
                        )}
                        {r.review_note && <p>核验意见：{r.review_note}</p>}
                      </div>
                    ),
                  }}
                  columns={[
                    {
                      title: "类型",
                      dataIndex: "kind",
                      render: (v) => kindNames[v],
                    },
                    ...(tab === "review"
                      ? [{ title: "成员", dataIndex: "owner_name" }]
                      : []),
                    {
                      title: "比赛 / 职务",
                      dataIndex: "title",
                      render: (v, r) => (
                        <>
                          <strong>{r.display_title || v}</strong>
                          {r.kind === "position" && <Tag color={r.position_status === "former" ? "default" : "blue"}>{positionStatusLabels[r.position_status || "unknown"]}</Tag>}
                          <div>
                            {r.family && <Tag>{r.family}</Tag>}
                            {r.contest_stage && <Tag>{contestStageLabels[r.contest_stage] || r.contest_stage}</Tag>}
                            {r.team_name}
                          </div>
                        </>
                      ),
                    },
                    {
                      title: "奖项 / 结果",
                      dataIndex: "result",
                      render: (v, r) => r.contest_award ? <><Tag>{r.contest_award}</Tag><div style={{ color: '#64748b', fontSize: 12 }}>学校认定：{v}</div></> : v || "—",
                    },
                    {
                      title: "日期 / 任期",
                      render: (_, r) =>
                        [r.start_date, r.end_date]
                          .filter(Boolean)
                          .join(" — ") || "—",
                    },
                    {
                      title: "状态",
                      dataIndex: "status",
                      render: (v) => (
                        <Tag
                          color={
                            v === "verified"
                              ? "green"
                              : v === "rejected"
                                ? "orange"
                                : "blue"
                          }
                        >
                          {statusNames[v]}
                        </Tag>
                      ),
                    },
                    {
                      title: "操作",
                      render: (_, r) => (
                        <Space>
                          {tab === "review" ? (
                            <Button
                              size="small"
                              onClick={() => {
                                setReview(r);
                                setReviewNote("");
                              }}
                            >
                              核验
                            </Button>
                          ) : (
                            <>
                              <Button size="small" onClick={() => edit(r)}>
                                编辑
                              </Button>
                              <Popconfirm
                                title="删除这条个人记录？"
                                onConfirm={async () => {
                                  try {
                                    await teamApi(`/records/${r.id}`, "DELETE");
                                    await load();
                                  } catch (e) {
                                    message.error(teamError(e));
                                  }
                                }}
                              >
                                <Button size="small" danger>
                                  删除
                                </Button>
                              </Popconfirm>
                            </>
                          )}
                        </Space>
                      ),
                    },
                  ]}
                />
                <Pagination
                  style={{ marginTop: 20 }}
                  current={page}
                  pageSize={50}
                  total={total}
                  showSizeChanger={false}
                  onChange={setPage}
                />
              </Card>
            </>
          )}
        </div>
        <Modal
          title={roster ? "队长统一登记" : "维护我的校队记录"}
          open={open}
          onCancel={() => setOpen(false)}
          footer={null}
          destroyOnHidden
        >
          <Form form={form} layout="vertical" onFinish={submit}>
            <Form.Item name="id" hidden>
              <Input />
            </Form.Item>
            <Form.Item name="version" hidden>
              <Input />
            </Form.Item>
            <Form.Item
              name="kind"
              label="记录类型"
              rules={[{ required: true }]}
            >
              <Select
                disabled={roster}
                onChange={(value) => {
                  if (value === 'position') form.setFieldsValue({ ...academicTermDates(currentAcademicTerm()), positionKey: undefined, title: '', organization: '', result: '', awardLevel: undefined, awardGrade: undefined });
                  else form.setFieldsValue({ startDate: undefined, endDate: undefined, title: '', organization: '', result: '' });
                }}
                options={Object.entries(kindNames).map(([value, label]) => ({
                  value,
                  label,
                }))}
              />
            </Form.Item>
            {kind !== "position" && (
              <Form.Item
                name="family"
                label="赛事类别"
                rules={[{ required: true }]}
              >
                <Select
                  onChange={() => form.setFieldsValue({ contestStage: undefined, awardLevel: undefined, awardGrade: undefined, contestAward: undefined })}
                  options={
                    roster
                      ? familyOptions.filter((x) =>
                          organizedFamilies.includes(x.value),
                        )
                      : familyOptions
                  }
                />
              </Form.Item>
            )}
            {kind === "registration" &&
              voluntaryFamilies.includes(family || "") && (
                <Alert
                  style={{ marginBottom: 16 }}
                  type="info"
                  title="这是个人自愿登记，仅登记你本人，不代替赛事官方报名。"
                />
              )}
            {roster && (
              <Form.Item
                name="userIds"
                label="参赛成员（搜索姓名或用户名）"
                rules={[{ required: true }]}
              >
                <Select
                  mode="multiple"
                  filterOption={false}
                  options={options}
                  onSearch={async (keyword) => {
                    if (keyword.length < 2) return;
                    try {
                      const r = await axios.get("/api/user/search", {
                        params: { keyword },
                      });
                      if (r.data.code === 200)
                        setOptions(
                          (r.data.data || []).map(
                            (u: { userId: number; userName: string }) => ({
                              value: u.userId,
                              label: u.userName,
                            }),
                          ),
                        );
                    } catch (e) {
                      message.error(teamError(e));
                    }
                  }}
                />
              </Form.Item>
            )}
            {kind === 'position' ? (
              <Form.Item name="positionKey" label="职务" rules={[{ required: true, message: '请选择职务' }]}>
                <Select options={memberRoles} placeholder="选择组织及职务" />
              </Form.Item>
            ) : (
              <>
                <Form.Item name="title" label="比赛完整名称" rules={[{ required: true, whitespace: true, max: 200 }]}>
                  <Input maxLength={200} placeholder="请包含年份、赛站或组别" />
                </Form.Item>
                <Form.Item name="organization" label="主办方"><Input maxLength={200} /></Form.Item>
                <Form.Item name="teamName" label="队伍名称"><Input maxLength={200} /></Form.Item>
                {kind === 'award' ? (
                  <>
                    {awardStages(family).length > 0 && <Form.Item name="contestStage" label="赛事阶段" rules={[{ required: true, message: '请选择赛事阶段' }]}>
                      <Select options={awardStages(family)} placeholder="按赛事阶段确定学校认定级别" />
                    </Form.Item>}
                    {isXcpc && <Form.Item name="contestAward" label="赛事奖项" rules={[{ required: true, message: '请选择赛事原始奖项' }]}>
                      <Select options={xcpcAwards.map(value => ({ value, label: value }))} placeholder="金牌、银牌、铜牌或优胜奖" />
                    </Form.Item>}
                    <Form.Item name="awardLevel" label={isXcpc ? '学校认定级别' : '奖项级别'} rules={[{ required: true, message: '请选择奖项级别' }]}>
                      <Select disabled={awardStages(family).length > 0} placeholder="选择赛事阶段后自动确定" options={awardLevels.map(value => ({ value, label: value }))} />
                    </Form.Item>
                    <Form.Item name="awardGrade" label={isXcpc ? '学校认定等次' : '奖项等次'} rules={[{ required: true, message: isXcpc ? '请先选择赛事奖项' : '请选择奖项等次' }]} extra={isXcpc ? '金、银、铜牌分别认定为一、二、三等奖；优胜奖认定为优秀奖。' : undefined}>
                      <Select disabled={isXcpc} options={awardGrades.map(value => ({ value, label: value }))} />
                    </Form.Item>
                  </>
                ) : (
                  <Form.Item name="result" label="参赛结果"><Input maxLength={100} placeholder="如：待参赛、已参赛" /></Form.Item>
                )}
              </>
            )}
            <Space align="start">
              <Form.Item
                name="startDate"
                label={kind === "position" ? "任职开始" : "比赛 / 获奖日期"}
                rules={[{ required: kind === "position", message: "请选择任职开始日期" }]}
              >
                <Input type="date" />
              </Form.Item>
              <Form.Item
                name="endDate"
                dependencies={['startDate']}
                rules={[
                  { required: kind === 'position', message: '请选择任职结束日期' },
                  ({ getFieldValue }) => ({ validator(_, value) {
                    return !value || !getFieldValue('startDate') || value >= getFieldValue('startDate')
                      ? Promise.resolve() : Promise.reject(new Error('结束日期不能早于开始日期'));
                  } }),
                ]}
                extra={kind === 'position' ? '结束日期次日起自动显示为“前”职务。' : undefined}
                label={
                  kind === "position"
                    ? "任职结束"
                    : "结束日期（可选）"
                }
              >
                <Input type="date" />
              </Form.Item>
            </Space>
            <Form.Item
              name="sourceUrl"
              label="证明来源链接"
              rules={[
                { type: "url", message: "请输入完整的 http 或 https 链接" },
              ]}
            >
              <Input maxLength={1000} placeholder="https://..." />
            </Form.Item>
            <Form.Item name="notes" label="补充说明 / 队友 / 核验材料说明">
              <Input.TextArea rows={4} maxLength={4000} showCount />
            </Form.Item>
            <Button type="primary" htmlType="submit" loading={saving}>
              保存并提交核验
            </Button>
          </Form>
        </Modal>
        <Modal
          title="核验成员记录"
          open={!!review}
          onCancel={() => setReview(undefined)}
          footer={
            <Space>
              <Button loading={saving} onClick={() => audit(false)}>
                退回补充
              </Button>
              <Button
                type="primary"
                loading={saving}
                onClick={() => audit(true)}
              >
                核验通过
              </Button>
            </Space>
          }
        >
          <p>
            {review?.owner_name} · {review?.display_title || review?.title} · {review?.contest_award ? `${review.contest_award}（学校认定：${review.result}）` : review?.result}
            <br />{[review?.start_date, review?.end_date].filter(Boolean).join(" — ")}
          </p>
          <p style={{ whiteSpace: "pre-wrap" }}>{review?.notes}</p>
          {review?.source_url && (
            <p>
              <a
                href={review.source_url}
                target="_blank"
                rel="noopener noreferrer"
              >
                查看证明来源
              </a>
            </p>
          )}
          <Input.TextArea
            value={reviewNote}
            onChange={(e) => setReviewNote(e.target.value)}
            maxLength={1000}
            placeholder="核验意见或需要补充的材料"
          />
        </Modal>
      </main>
    </>
  );
}
