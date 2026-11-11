"use client";
import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Button,
  Card,
  Drawer,
  Empty,
  Form,
  Input,
  List,
  Modal,
  Pagination,
  Popconfirm,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
  message,
} from "antd";
import Link from "next/link";
import SiteHeader from "../components/SiteHeader";
import { TeamIdentity, teamApi, teamError } from "@/lib/besti";

const categories: Record<string, string> = {
  discussion: "日常讨论",
  training: "训练答疑",
  experience: "参赛经验",
  announcement: "校队公告",
};
type Topic = {
  id: number;
  author_id: number;
  author_name: string;
  title: string;
  content: string;
  category: string;
  pinned: boolean;
  locked: boolean;
  updated_at: string;
  created_at: string;
  reply_count: number;
};
type Reply = {
  id: number;
  author_id: number;
  author_name: string;
  content: string;
  created_at: string;
};
const date = (v: string) =>
  new Date(v).toLocaleString("zh-CN", { hour12: false });
export default function CommunityClient() {
  const [me, setMe] = useState<TeamIdentity>();
  const [accessError, setAccessError] = useState("");
  const [loading, setLoading] = useState(true);
  const [topics, setTopics] = useState<Topic[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [category, setCategory] = useState("");
  const [keyword, setKeyword] = useState("");
  const [active, setActive] = useState<Topic>();
  const [replies, setReplies] = useState<Reply[]>([]);
  const [replyPage, setReplyPage] = useState(1);
  const [replyTotal, setReplyTotal] = useState(0);
  const [replyText, setReplyText] = useState("");
  const [editor, setEditor] = useState(false);
  const [editing, setEditing] = useState<Topic>();
  const [form] = Form.useForm();
  const [busy, setBusy] = useState(false);
  const [replyEdit, setReplyEdit] = useState<Reply>();
  const [replyEditText, setReplyEditText] = useState("");
  useEffect(() => {
    teamApi<TeamIdentity>("/me")
      .then((identity) => {
        if (identity.communityAccess) setMe(identity);
        else setAccessError("你已完成学生身份认证，当前为普通参赛者。校队交流区需由管理员确认校队资格后开放。");
      })
      .catch((e) => setAccessError(teamError(e)))
      .finally(() => setLoading(false));
  }, []);
  const load = useCallback(async () => {
    if (!me) return;
    setLoading(true);
    try {
      const r = await teamApi<{ items: Topic[]; total: number }>(
        `/topics?page=${page}&category=${encodeURIComponent(category)}&keyword=${encodeURIComponent(keyword)}`,
      );
      setTopics(r.items);
      setTotal(r.total);
    } catch (e) {
      message.error(teamError(e));
    } finally {
      setLoading(false);
    }
  }, [me, page, category, keyword]);
  useEffect(() => {
    void load();
  }, [load]);
  const loadReplies = useCallback(async (id: number, p: number) => {
    const r = await teamApi<{ items: Reply[]; total: number }>(
      `/topics/${id}/replies?page=${p}`,
    );
    setReplies(r.items);
    setReplyTotal(r.total);
  }, []);
  const openTopic = async (id: number) => {
    try {
      const t = await teamApi<Topic>(`/topics/${id}`);
      setActive(t);
      setReplyPage(1);
      setReplyText("");
      await loadReplies(id, 1);
    } catch (e) {
      message.error(teamError(e));
    }
  };
  const edit = (t?: Topic) => {
    form.resetFields();
    setEditing(t);
    form.setFieldsValue(
      t
        ? { category: t.category, title: t.title, content: t.content }
        : { category: "discussion" },
    );
    setEditor(true);
  };
  const save = async (v: {
    category: string;
    title: string;
    content: string;
  }) => {
    setBusy(true);
    try {
      await teamApi(
        editing ? `/topics/${editing.id}` : "/topics",
        editing ? "PUT" : "POST",
        v,
      );
      setEditor(false);
      await load();
      if (editing && active?.id === editing.id) await openTopic(editing.id);
      message.success("帖子已保存");
    } catch (e) {
      message.error(teamError(e));
    } finally {
      setBusy(false);
    }
  };
  const sendReply = async () => {
    if (!active) return;
    setBusy(true);
    try {
      await teamApi(`/topics/${active.id}/replies`, "POST", {
        content: replyText,
      });
      setReplyText("");
      const last = Math.ceil((replyTotal + 1) / 50);
      setReplyPage(last);
      await loadReplies(active.id, last);
      await load();
    } catch (e) {
      message.error(teamError(e));
    } finally {
      setBusy(false);
    }
  };
  const moderate = async (flags: { pinned: boolean; locked: boolean }) => {
    if (!active) return;
    try {
      await teamApi(`/topics/${active.id}/moderate`, "POST", flags);
      setActive({ ...active, ...flags });
      await load();
    } catch (e) {
      message.error(teamError(e));
    }
  };
  return (
    <>
      <SiteHeader />
      <main className="content-page-shell">
        <section className="content-hero content-hero-centered">
          <Tag>BESTI ACM · COMMUNITY</Tag>
          <Typography.Title className="content-hero-title">
            校队交流区
          </Typography.Title>
          <Typography.Paragraph>
            讨论算法、分享比赛，也记录一起训练的日常。内容仅任期内的校队成员与交流区管理员可见。
          </Typography.Paragraph>
        </section>
        <div style={{ maxWidth: 1100, margin: "0 auto" }}>
          {accessError ? (
            <Alert
              type="info"
              showIcon
              title={accessError}
              description={
                <Space>
                  <Link href="/login">登录账号</Link>
                  <Link href="/team-space">我的参赛档案</Link>
                  <Link href="/contest/signup">查看公开赛事</Link>
                </Space>
              }
            />
          ) : !me ? (
            <Spin />
          ) : (
            <Card>
              <Space wrap style={{ marginBottom: 24 }}>
                <Select
                  aria-label="讨论板块"
                  style={{ width: 145 }}
                  value={category}
                  onChange={(v) => {
                    setCategory(v);
                    setPage(1);
                  }}
                  options={[
                    { value: "", label: "全部板块" },
                    ...Object.entries(categories).map(([value, label]) => ({
                      value,
                      label,
                    })),
                  ]}
                />
                <Input.Search
                  aria-label="搜索讨论"
                  placeholder="搜索帖子标题"
                  allowClear
                  onSearch={(v) => {
                    setKeyword(v);
                    setPage(1);
                  }}
                  style={{ width: 260 }}
                />
                <Button type="primary" onClick={() => edit()}>
                  发起讨论
                </Button>
                <Link href="/team-space">我的参赛档案</Link>
              </Space>
              <List
                loading={loading}
                dataSource={topics}
                locale={{
                  emptyText: (
                    <Empty description="还没有讨论，来分享你的第一个问题或经验" />
                  ),
                }}
                renderItem={(t) => (
                  <List.Item
                    actions={[
                      <span key="replies">{t.reply_count} 条回复</span>,
                    ]}
                  >
                    <List.Item.Meta
                      title={
                        <Button
                          type="link"
                          style={{
                            height: "auto",
                            padding: 0,
                            textAlign: "left",
                            whiteSpace: "normal",
                          }}
                          onClick={() => openTopic(t.id)}
                        >
                          {t.pinned && <Tag color="red">置顶</Tag>}
                          {t.locked && <Tag>已锁定</Tag>}
                          {t.title}
                        </Button>
                      }
                      description={
                        <Space wrap>
                          <Tag>{categories[t.category]}</Tag>
                          <span>{t.author_name}</span>
                          <span>{date(t.updated_at)}</span>
                        </Space>
                      }
                    />
                  </List.Item>
                )}
              />
              <Pagination
                style={{ marginTop: 20 }}
                current={page}
                pageSize={30}
                total={total}
                showSizeChanger={false}
                onChange={setPage}
              />
            </Card>
          )}
        </div>
        <Drawer
          title="校队内部讨论"
          open={!!active}
          onClose={() => setActive(undefined)}
          width="min(900px, 100vw)"
        >
          {active && me && (
            <>
              <Space wrap>
                <Tag>{categories[active.category]}</Tag>
                {active.pinned && <Tag color="red">置顶</Tag>}
                {active.locked && <Tag>已锁定</Tag>}
              </Space>
              <Typography.Title level={2}>{active.title}</Typography.Title>
              <p style={{ color: "#6b7280" }}>
                {active.author_name} · {date(active.created_at)}
              </p>
              <div
                style={{
                  whiteSpace: "pre-wrap",
                  overflowWrap: "anywhere",
                  fontSize: 16,
                  lineHeight: 1.9,
                  margin: "24px 0",
                }}
              >
                {active.content}
              </div>
              <Space wrap>
                {active.author_id === me.userId && (
                  <Button
                    onClick={() => edit(active)}
                    disabled={active.locked && !me.moderator}
                  >
                    编辑帖子
                  </Button>
                )}
                {(active.author_id === me.userId || me.moderator) && (
                  <Popconfirm
                    title="删除帖子及其回复？"
                    onConfirm={async () => {
                      try {
                        await teamApi(`/topics/${active.id}`, "DELETE");
                        setActive(undefined);
                        await load();
                      } catch (e) {
                        message.error(teamError(e));
                      }
                    }}
                  >
                    <Button danger>删除帖子</Button>
                  </Popconfirm>
                )}
                {me.moderator && (
                  <>
                    <Button
                      onClick={() =>
                        moderate({
                          pinned: !active.pinned,
                          locked: active.locked,
                        })
                      }
                    >
                      {active.pinned ? "取消置顶" : "置顶"}
                    </Button>
                    <Button
                      onClick={() =>
                        moderate({
                          pinned: active.pinned,
                          locked: !active.locked,
                        })
                      }
                    >
                      {active.locked ? "解除锁定" : "锁定讨论"}
                    </Button>
                  </>
                )}
              </Space>
              <Typography.Title level={4} style={{ marginTop: 36 }}>
                回复 · {replyTotal}
              </Typography.Title>
              <List
                dataSource={replies}
                locale={{ emptyText: "暂无回复" }}
                renderItem={(r) => (
                  <List.Item>
                    <div style={{ width: "100%" }}>
                      <Space wrap>
                        <strong>{r.author_name}</strong>
                        <span style={{ color: "#6b7280" }}>
                          {date(r.created_at)}
                        </span>
                        {r.author_id === me.userId && (
                          <Button
                            size="small"
                            type="link"
                            disabled={active.locked && !me.moderator}
                            onClick={() => {
                              setReplyEdit(r);
                              setReplyEditText(r.content);
                            }}
                          >
                            编辑
                          </Button>
                        )}
                        {(r.author_id === me.userId || me.moderator) && (
                          <Popconfirm
                            title="删除回复？"
                            onConfirm={async () => {
                              try {
                                await teamApi(`/replies/${r.id}`, "DELETE");
                                await loadReplies(active.id, replyPage);
                                await load();
                              } catch (e) {
                                message.error(teamError(e));
                              }
                            }}
                          >
                            <Button size="small" type="link" danger>
                              删除
                            </Button>
                          </Popconfirm>
                        )}
                      </Space>
                      <p
                        style={{
                          whiteSpace: "pre-wrap",
                          overflowWrap: "anywhere",
                          lineHeight: 1.8,
                        }}
                      >
                        {r.content}
                      </p>
                    </div>
                  </List.Item>
                )}
              />
              <Pagination
                current={replyPage}
                pageSize={50}
                total={replyTotal}
                showSizeChanger={false}
                onChange={async (p) => {
                  setReplyPage(p);
                  try {
                    await loadReplies(active.id, p);
                  } catch (e) {
                    message.error(teamError(e));
                  }
                }}
              />
              {active.locked ? (
                <Alert
                  style={{ marginTop: 24 }}
                  title="此讨论已锁定，暂时不能回复。"
                />
              ) : (
                <div style={{ marginTop: 24 }}>
                  <Input.TextArea
                    aria-label="回复内容"
                    rows={5}
                    value={replyText}
                    onChange={(e) => setReplyText(e.target.value)}
                    maxLength={10000}
                    placeholder="写下你的回复…"
                  />
                  <Button
                    type="primary"
                    style={{ marginTop: 12 }}
                    loading={busy}
                    disabled={!replyText.trim()}
                    onClick={sendReply}
                  >
                    发表回复
                  </Button>
                </div>
              )}
            </>
          )}
        </Drawer>
        <Modal
          title={editing ? "编辑帖子" : "发起讨论"}
          open={editor}
          onCancel={() => setEditor(false)}
          footer={null}
          width={700}
          destroyOnHidden
        >
          <Form form={form} layout="vertical" onFinish={save}>
            <Form.Item
              name="category"
              label="板块"
              rules={[{ required: true }]}
            >
              <Select
                options={Object.entries(categories)
                  .filter(([v]) => v !== "announcement" || me?.moderator)
                  .map(([value, label]) => ({ value, label }))}
              />
            </Form.Item>
            <Form.Item
              name="title"
              label="标题"
              rules={[{ required: true, whitespace: true, max: 200 }]}
            >
              <Input maxLength={200} />
            </Form.Item>
            <Form.Item
              name="content"
              label="正文"
              rules={[{ required: true, whitespace: true, max: 20000 }]}
            >
              <Input.TextArea
                rows={12}
                maxLength={20000}
                showCount
                placeholder="支持普通文本与代码。内容仅校队成员可见。"
              />
            </Form.Item>
            <Button type="primary" htmlType="submit" loading={busy}>
              发布
            </Button>
          </Form>
        </Modal>
        <Modal
          title="编辑回复"
          open={!!replyEdit}
          onCancel={() => setReplyEdit(undefined)}
          confirmLoading={busy}
          onOk={async () => {
            if (!replyEdit || !active) return;
            setBusy(true);
            try {
              await teamApi(`/replies/${replyEdit.id}`, "PUT", {
                content: replyEditText,
              });
              setReplyEdit(undefined);
              await loadReplies(active.id, replyPage);
            } catch (e) {
              message.error(teamError(e));
            } finally {
              setBusy(false);
            }
          }}
        >
          <Input.TextArea
            rows={7}
            maxLength={10000}
            value={replyEditText}
            onChange={(e) => setReplyEditText(e.target.value)}
          />
        </Modal>
      </main>
    </>
  );
}
