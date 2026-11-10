"use client";

import { useCallback, useEffect, useState } from 'react';
import {
  Alert, Button, Card, Col, DatePicker, Descriptions, Empty, Form, Input,
  InputNumber, Modal, Popconfirm, Result, Row, Select, Space, Spin, Switch,
  Table, Tabs, Tag, Typography, message,
} from 'antd';
import { PlusOutlined, ReloadOutlined, SaveOutlined } from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import utc from 'dayjs/plugin/utc';
import timezone from 'dayjs/plugin/timezone';
import { readStoredUser } from '@/utils/auth';
import { describeRequestError } from '@/utils/errorMessage';
import { canManageQqBot } from '@/security/qqBotAccess';
import {
  createQqBotTask, deleteQqBotTask, getQqBotConfig, getQqBotStatus,
  getQqBotTasks, reconnectQqBot, updateQqBotConfig, updateQqBotTask,
  type QqBotConfig, type QqBotRecurrence, type QqBotStatus,
  type QqBotTask, type QqBotTaskInput,
} from '@/services/qqBot.service';

dayjs.extend(utc);
dayjs.extend(timezone);

const { Text, Paragraph, Title } = Typography;
const SHANGHAI = 'Asia/Shanghai';
const groupPattern = /^[1-9]\d{4,14}$/;
const defaults: QqBotConfig = {
  enabled: false,
  wsUrl: 'ws://127.0.0.1:3001',
  accessTokenConfigured: false,
  hydroBaseUrl: '',
  hydroApiKeyConfigured: false,
  reconnectSeconds: 10,
  typingMillisPerChar: 60,
  minReplyDelayMillis: 600,
  maxReplyDelayMillis: 12000,
  queryGroups: [],
  pushGroups: [],
  pushOffline: false,
  pushOnline: false,
  pushTraining: false,
  pushBoard: false,
};

const recurrenceOptions: { value: QqBotRecurrence; label: string }[] = [
  { value: 'ONCE', label: '仅一次' },
  { value: 'DAILY', label: '每天' },
  { value: 'WEEKLY', label: '每周' },
  { value: 'INTERVAL', label: '每隔 N 分钟' },
];

const commands = [
  ['offline contest / 近期线下赛', '查询官网尚未开始报名、正在报名、正在进行的赛事'],
  ['online contest / 近期线上赛', '查询校队 OJ 的 System 域比赛及 CP Tracker 的近期线上赛'],
  ['training / 培训', '查询官网近期培训及报名信息'],
  ['notice / 通知', '查询最近 5 条通知'],
  ['news / 新闻', '查询最近 5 条新闻'],
  ['publicity / 公示', '查询最近 5 条公示'],
  ['board / 外榜', '查询最近 5 条外榜及比赛时间'],
  ['/rating 用户名', '按官网用户名及绑定邮箱查询校队 OJ Rating，回复信息卡片'],
  ['/tracker 用户名', '按官网用户名查询五个平台的解题数量，回复信息卡片'],
  ['help / 帮助', '查看全部查询指令及用法'],
];

interface TaskFormValues {
  title: string;
  groupId: string;
  content?: string;
  imageText?: string;
  dueAt: Dayjs;
  recurrence: QqBotRecurrence;
  intervalMinutes?: number | null;
  enabled: boolean;
}

function errorText(error: unknown, fallback: string) {
  return describeRequestError(error, error instanceof Error && error.message ? error.message : fallback);
}

function isFormError(error: unknown) {
  return Boolean(error && typeof error === 'object' && 'errorFields' in error);
}

function formatTime(value?: string | null) {
  if (!value) return '—';
  // 服务端无时区时间为上海本地时间，有时区时间转换为上海时间。
  const time = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(value)
    ? dayjs(value).tz(SHANGHAI) : dayjs.tz(value, SHANGHAI);
  return time.isValid() ? time.format('YYYY-MM-DD HH:mm:ss') : '—';
}

function validateUrl(value: string, protocols: string[]) {
  try {
    const url = new URL(value);
    return protocols.includes(url.protocol) && !url.username && !url.password && !url.hash;
  } catch {
    return false;
  }
}

export default function QqBotManager() {
  const [form] = Form.useForm<QqBotConfig>();
  const [taskForm] = Form.useForm<TaskFormValues>();
  const [authorized, setAuthorized] = useState<boolean | null>(null);
  const [loading, setLoading] = useState(true);
  const [loaded, setLoaded] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [saving, setSaving] = useState(false);
  const [reconnecting, setReconnecting] = useState(false);
  const [status, setStatus] = useState<QqBotStatus | null>(null);
  const [tasks, setTasks] = useState<QqBotTask[]>([]);
  const [taskModalOpen, setTaskModalOpen] = useState(false);
  const [editingTask, setEditingTask] = useState<QqBotTask | null>(null);
  const [taskSaving, setTaskSaving] = useState(false);
  const [changingTaskId, setChangingTaskId] = useState<string | null>(null);
  const [secretState, setSecretState] = useState({ accessTokenConfigured: false, hydroApiKeyConfigured: false });
  const enabled = Form.useWatch('enabled', form);
  const recurrence = Form.useWatch('recurrence', taskForm);

  useEffect(() => {
    const sync = () => setAuthorized(canManageQqBot(readStoredUser()));
    sync();
    window.addEventListener('auth-state-change', sync);
    return () => window.removeEventListener('auth-state-change', sync);
  }, []);

  const load = useCallback(async () => {
    if (!canManageQqBot(readStoredUser())) return;
    setLoading(true);
    setLoadError('');
    try {
      const [config, nextStatus, nextTasks] = await Promise.all([
        getQqBotConfig(), getQqBotStatus(), getQqBotTasks(),
      ]);
      if (!canManageQqBot(readStoredUser())) return;
      // 凭据只在更换时填写，不将任何服务端返回的凭据放进表单。
      form.setFieldsValue({ ...defaults, ...config, accessToken: '', hydroApiKey: '' });
      setSecretState({
        accessTokenConfigured: Boolean(config.accessTokenConfigured),
        hydroApiKeyConfigured: Boolean(config.hydroApiKeyConfigured),
      });
      setStatus(nextStatus);
      setTasks(nextTasks);
      setLoaded(true);
    } catch (error) {
      setLoadError(errorText(error, '读取机器人配置失败'));
    } finally {
      setLoading(false);
    }
  }, [form]);

  useEffect(() => {
    if (!authorized) return;
    const initialLoad = window.setTimeout(() => void load(), 0);
    const timer = window.setInterval(() => {
      if (!canManageQqBot(readStoredUser())) return;
      void getQqBotStatus().then(setStatus).catch(() => undefined);
    }, 15000);
    return () => {
      window.clearTimeout(initialLoad);
      window.clearInterval(timer);
    };
  }, [authorized, load]);

  const save = async () => {
    if (!canManageQqBot(readStoredUser())) return;
    try {
      const values = await form.validateFields();
      setSaving(true);
      await updateQqBotConfig({
        enabled: values.enabled,
        wsUrl: values.wsUrl.trim(),
        accessToken: values.accessToken?.trim() || null,
        hydroBaseUrl: values.hydroBaseUrl.trim().replace(/\/+$/, ''),
        hydroApiKey: values.hydroApiKey?.trim() || null,
        reconnectSeconds: values.reconnectSeconds,
        typingMillisPerChar: values.typingMillisPerChar,
        minReplyDelayMillis: values.minReplyDelayMillis,
        maxReplyDelayMillis: values.maxReplyDelayMillis,
        queryGroups: [...new Set(values.queryGroups || [])],
        pushGroups: [...new Set(values.pushGroups || [])],
        pushOffline: values.pushOffline,
        pushOnline: values.pushOnline,
        pushTraining: values.pushTraining,
        pushBoard: values.pushBoard,
      });
      const config = await getQqBotConfig();
      form.setFieldsValue({ accessToken: '', hydroApiKey: '' });
      setSecretState({
        accessTokenConfigured: Boolean(config.accessTokenConfigured),
        hydroApiKeyConfigured: Boolean(config.hydroApiKeyConfigured),
      });
      message.success('机器人配置已保存');
      setStatus(await getQqBotStatus());
    } catch (error) {
      if (!isFormError(error)) message.error(errorText(error, '保存配置失败'));
    } finally {
      setSaving(false);
    }
  };

  const reconnect = async () => {
    if (!canManageQqBot(readStoredUser())) return;
    setReconnecting(true);
    try {
      await reconnectQqBot();
      setStatus(await getQqBotStatus());
      message.success('已发起重新连接');
    } catch (error) {
      message.error(errorText(error, '重新连接失败'));
    } finally {
      setReconnecting(false);
    }
  };

  const openTask = (task?: QqBotTask) => {
    setEditingTask(task || null);
    taskForm.resetFields();
    taskForm.setFieldsValue(task ? {
      ...task,
      imageText: (task.images || []).join('\n'),
      // DatePicker 编辑的是上海墙上时间，不受管理员浏览器时区影响。
      dueAt: dayjs(formatTime(task.dueAt)),
    } : {
      title: '', groupId: '', content: '', imageText: '',
      dueAt: dayjs(dayjs().tz(SHANGHAI).add(5, 'minute').format('YYYY-MM-DD HH:mm:ss')),
      recurrence: 'ONCE', intervalMinutes: 60, enabled: true,
    });
    setTaskModalOpen(true);
  };

  const saveTask = async () => {
    if (!canManageQqBot(readStoredUser())) return;
    try {
      const values = await taskForm.validateFields();
      const content = values.content?.trim() || '';
      const images = (values.imageText || '').split(/\r?\n/).map(item => item.trim()).filter(Boolean);
      if (!content && images.length === 0) {
        message.error('请至少填写消息文字或一张图片');
        return;
      }
      setTaskSaving(true);
      const task: QqBotTaskInput = {
        title: values.title.trim(), groupId: values.groupId.trim(), content, images,
        dueAt: values.dueAt.format('YYYY-MM-DDTHH:mm:ss'), recurrence: values.recurrence,
        intervalMinutes: values.recurrence === 'INTERVAL' ? values.intervalMinutes || null : null,
        enabled: values.enabled,
      };
      if (editingTask) await updateQqBotTask(editingTask.id, task);
      else await createQqBotTask(task);
      setTasks(await getQqBotTasks());
      setTaskModalOpen(false);
      message.success('定时消息已保存');
    } catch (error) {
      if (!isFormError(error)) message.error(errorText(error, '保存定时消息失败'));
    } finally {
      setTaskSaving(false);
    }
  };

  const changeTask = async (task: QqBotTask, nextEnabled?: boolean) => {
    if (!canManageQqBot(readStoredUser())) return;
    setChangingTaskId(task.id);
    try {
      if (nextEnabled === undefined) await deleteQqBotTask(task.id);
      else await updateQqBotTask(task.id, {
        title: task.title, groupId: task.groupId, content: task.content, images: task.images,
        dueAt: task.dueAt, recurrence: task.recurrence,
        intervalMinutes: task.intervalMinutes, enabled: nextEnabled,
      });
      setTasks(await getQqBotTasks());
      message.success(nextEnabled === undefined ? '定时消息已删除' : nextEnabled ? '定时消息已启用' : '定时消息已停用');
    } catch (error) {
      message.error(errorText(error, '更新定时消息失败'));
    } finally {
      setChangingTaskId(null);
    }
  };

  if (authorized === null) return <Spin />;
  if (!authorized) return <Result status="403" title="仅超级管理员可管理 QQ 机器人" subTitle="请使用状态正常的超级管理员账号登录。" />;

  const groupRules = [{
    validator: (_: unknown, groups: string[] | undefined) => {
      if (groups && new Set(groups).size > 100) return Promise.reject(new Error('最多可指定 100 个群'));
      return !groups?.some(group => !groupPattern.test(group))
        ? Promise.resolve() : Promise.reject(new Error('群号必须是 5–15 位数字，且不能以 0 开头'));
    },
  }];

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <div>
        <Title level={3} style={{ margin: 0 }}>QQ 机器人管理</Title>
        <Text type="secondary">仅超级管理员可配置连接、信息推送及定时消息。所有时间均为北京时间（Asia/Shanghai）。</Text>
      </div>
      {loadError && <Alert type="error" showIcon title={loadError} action={<Button size="small" onClick={() => void load()}>重新加载</Button>} />}
      <Card title="运行状态" extra={<Space wrap>
        <Button icon={<ReloadOutlined />} onClick={() => void getQqBotStatus().then(setStatus).catch(error => message.error(errorText(error, '刷新状态失败')))}>刷新状态</Button>
        <Button onClick={reconnect} loading={reconnecting} disabled={!loaded || !status?.enabled}>重新连接</Button>
      </Space>}>
        <Descriptions size="small" column={{ xs: 1, sm: 2, lg: 3 }} items={[
          { key: 'connection', label: '连接', children: <Tag color={status?.connected ? 'success' : status?.connecting ? 'processing' : 'default'}>{status?.connected ? '已连接' : status?.connecting ? '连接中' : status?.enabled ? '未连接' : '未启用'}</Tag> },
          { key: 'account', label: 'QQ 账号', children: status?.self?.user_id ? `${status.self.nickname || '机器人'} (${status.self.user_id})` : '—' },
          { key: 'queue', label: '待发送消息', children: status?.queueSize ?? '—' },
          { key: 'connectedAt', label: '最近连接', children: formatTime(status?.lastConnectedAt) },
          { key: 'tasks', label: '定时任务总数', children: status?.scheduledTasks ?? '—' },
          { key: 'error', label: '最近错误', children: status?.lastError || '无' },
        ]} />
      </Card>
      <Tabs items={[
        {
          key: 'configuration', label: '连接与推送配置', children: <Card loading={loading} extra={<Button type="primary" icon={<SaveOutlined />} loading={saving} disabled={!loaded} onClick={save}>保存配置</Button>}>
            <Form form={form} layout="vertical" initialValues={defaults} disabled={!loaded || saving}>
              <Form.Item name="enabled" label="启用 QQ 机器人" valuePropName="checked"><Switch checkedChildren="已启用" unCheckedChildren="已停用" /></Form.Item>
              <Row gutter={24}>
                <Col xs={24} lg={16}>
                  <Form.Item name="wsUrl" label="OneBot 11 正向 WebSocket 地址" extra="由官网后端主动连接 NapCat / OneBot 的 WebSocket 服务。部署在同一主机时可使用 127.0.0.1。" rules={[
                    { required: enabled, message: '请输入 WebSocket 地址' },
                    { validator: (_, value) => !value || validateUrl(value, ['ws:', 'wss:']) ? Promise.resolve() : Promise.reject(new Error('请输入不含用户名、密码或片段的 ws:// 或 wss:// 地址')) },
                  ]}><Input placeholder="ws://127.0.0.1:3001" maxLength={1024} autoComplete="off" /></Form.Item>
                </Col>
                <Col xs={24} lg={8}>
                  <Form.Item name="reconnectSeconds" label="断线重连间隔（秒）" rules={[{ required: true, message: '请输入重连间隔' }]}><InputNumber min={1} max={3600} precision={0} style={{ width: '100%' }} /></Form.Item>
                </Col>
              </Row>
              <Form.Item name="accessToken" label={<Space>OneBot Access Token <Tag color={secretState.accessTokenConfigured ? 'green' : 'default'}>{secretState.accessTokenConfigured ? '已配置' : '未配置'}</Tag></Space>} extra="填写后更新，留空保留已保存的凭据。凭据不会回显。" rules={[{ pattern: /^[^\r\n]*$/, message: 'Access Token 不能包含换行' }]}><Input.Password placeholder="需要更新时填写" maxLength={512} autoComplete="new-password" /></Form.Item>
              <Row gutter={24}>
                <Col xs={24} lg={12}><Form.Item name="hydroBaseUrl" label="校队 Hydro OJ 地址" rules={[
                  { required: true, message: '请输入 Hydro OJ 地址' },
                  { validator: (_, value) => validateUrl(value, ['https:', 'http:']) ? Promise.resolve() : Promise.reject(new Error('请输入不含用户名、密码或片段的 http(s) 地址')) },
                ]}><Input placeholder="https://oj.example.com" maxLength={1024} /></Form.Item></Col>
                <Col xs={24} lg={12}><Form.Item name="hydroApiKey" label={<Space>Hydro API Key <Tag color={secretState.hydroApiKeyConfigured ? 'green' : 'default'}>{secretState.hydroApiKeyConfigured ? '已配置' : '未配置'}</Tag></Space>} extra="与 rating-system 插件中配置的 API Key 保持一致；留空保留，不回显。" rules={[{ validator: (_, value: string | undefined) => !value || (value.trim().length >= 32 && value.trim().length <= 256 && !/[\r\n]/.test(value)) ? Promise.resolve() : Promise.reject(new Error('API Key 应为 32–256 个字符且不含换行')) }]}><Input.Password placeholder="需要更新时填写" maxLength={256} autoComplete="new-password" /></Form.Item></Col>
              </Row>
              <Title level={5}>查询与回复</Title>
              <Form.Item name="queryGroups" label="允许查询的 QQ 群" extra="输入群号后按回车，可添加多个。留空允许机器人加入的所有群查询。" rules={groupRules}><Select mode="tags" tokenSeparators={[',', '，', ' ']} placeholder="例如 123456789" options={[]} /></Form.Item>
              <Row gutter={24}>
                <Col xs={24} md={8}><Form.Item name="typingMillisPerChar" label="每字回复延迟（毫秒）" rules={[{ required: true, message: '请输入每字延迟' }]}><InputNumber min={0} max={1000} precision={0} style={{ width: '100%' }} /></Form.Item></Col>
                <Col xs={24} md={8}><Form.Item name="minReplyDelayMillis" label="最短回复延迟（毫秒）" rules={[{ required: true, message: '请输入最短延迟' }]}><InputNumber min={0} max={60000} precision={0} style={{ width: '100%' }} /></Form.Item></Col>
                <Col xs={24} md={8}><Form.Item name="maxReplyDelayMillis" label="最长回复延迟（毫秒）" dependencies={['minReplyDelayMillis']} rules={[
                  { required: true, message: '请输入最长延迟' },
                  ({ getFieldValue }) => ({ validator: (_, value) => value >= getFieldValue('minReplyDelayMillis') ? Promise.resolve() : Promise.reject(new Error('最长延迟不能小于最短延迟')) }),
                ]}><InputNumber min={0} max={60000} precision={0} style={{ width: '100%' }} /></Form.Item></Col>
              </Row>
              <Paragraph type="secondary">查询回复会 @ 发起者，并按队列依次发送；延迟根据回复文字数量计算，并受最短和最长延迟限制。</Paragraph>
              <Title level={5}>信息推送</Title>
              <Form.Item name="pushGroups" label="接收信息推送的 QQ 群" extra="只有这里指定的群会收到赛事、培训与外榜推送；留空不推送。机器人需具备 @全体成员 权限。定时消息使用各自设置的目标群。" rules={groupRules}><Select mode="tags" tokenSeparators={[',', '，', ' ']} placeholder="输入群号后按回车" options={[]} /></Form.Item>
              <Row gutter={24}>
                <Col xs={24} md={12}><Form.Item name="pushOffline" label="线下赛：报名开始前 1 天" valuePropName="checked"><Switch /></Form.Item></Col>
                <Col xs={24} md={12}><Form.Item name="pushTraining" label="培训：报名开始前 1 天" valuePropName="checked"><Switch /></Form.Item></Col>
                <Col xs={24} md={12}><Form.Item name="pushOnline" label="校队 OJ 线上赛：开始前 1 天和 3 小时" valuePropName="checked"><Switch /></Form.Item></Col>
                <Col xs={24} md={12}><Form.Item name="pushBoard" label="外榜：比赛开始前 10 分钟" valuePropName="checked"><Switch /></Form.Item></Col>
              </Row>
              <Alert type="info" showIcon title="线上赛自动推送仅包含校队 OJ System 系统的比赛；AtCoder、Codeforces、牛客仅通过查询指令展示。" />
            </Form>
          </Card>,
        },
        {
          key: 'tasks', label: `定时消息（${tasks.length}）`, children: <Card title="定时消息" extra={<Button type="primary" icon={<PlusOutlined />} disabled={!loaded} onClick={() => openTask()}>新增定时消息</Button>}>
            <Paragraph type="secondary">每天、每周以首次执行时间为基准循环；每隔 N 分钟从首次执行时间开始。一次任务发送后自动停用。时间均按北京时间执行。</Paragraph>
            <Table<QqBotTask> rowKey="id" dataSource={tasks} loading={loading} scroll={{ x: 1100 }} locale={{ emptyText: <Empty description="暂无定时消息" /> }} columns={[
              { title: '标题 / 目标群', key: 'title', width: 200, render: (_, task) => <Space orientation="vertical" size={0}><Text strong>{task.title}</Text><Text type="secondary">QQ群 {task.groupId}</Text></Space> },
              { title: '消息', key: 'content', width: 250, render: (_, task) => <Space orientation="vertical" size={0}><Text ellipsis style={{ maxWidth: 230 }}>{task.content || '（仅图片）'}</Text>{Boolean(task.images?.length) && <Tag>{task.images.length} 张图片</Tag>}</Space> },
              { title: '重复方式', key: 'recurrence', width: 140, render: (_, task) => task.recurrence === 'INTERVAL' ? `每隔 ${task.intervalMinutes} 分钟` : recurrenceOptions.find(option => option.value === task.recurrence)?.label || task.recurrence },
              { title: '下次执行 / 最近发送', key: 'dueAt', width: 200, render: (_, task) => <Space orientation="vertical" size={0}><Text>{formatTime(task.dueAt)}</Text><Text type="secondary">{formatTime(task.lastSentAt)}</Text>{task.lastError && <Text type="danger">{task.lastError}</Text>}</Space> },
              { title: '启用', key: 'enabled', width: 80, render: (_, task) => <Switch size="small" checked={task.enabled} loading={changingTaskId === task.id} disabled={changingTaskId !== null} onChange={value => void changeTask(task, value)} /> },
              { title: '操作', key: 'actions', width: 150, render: (_, task) => <Space><Button type="link" size="small" disabled={changingTaskId !== null} onClick={() => openTask(task)}>编辑</Button><Popconfirm title="删除这条定时消息？" onConfirm={() => changeTask(task)} okText="删除" cancelText="取消"><Button type="link" size="small" danger disabled={changingTaskId !== null}>删除</Button></Popconfirm></Space> },
            ]} />
          </Card>,
        },
        {
          key: 'guide', label: '连接说明与指令', children: <Space orientation="vertical" size="large" style={{ width: '100%' }}>
            <Card title="连接步骤">
              <ol style={{ paddingLeft: 20, margin: 0, lineHeight: 2 }}>
                <li>在 NapCat / OneBot 11 中登录机器人 QQ，开启正向 WebSocket 服务，并设置 Access Token。</li>
                <li>将机器人 QQ 加入需要服务的群；如需推送 @全体成员，为机器人授予相应群权限。</li>
                <li>在此页填写官网后端可访问的 WebSocket 地址和 Access Token，保存并启用机器人，确认状态为“已连接”。</li>
                <li>在 Hydro 的 rating-system 插件中配置查询 API Key，并在此页填写相同的 Key 与校队 OJ 地址。</li>
                <li>配置查询群、推送群及推送类型，保存后可在群内发送“帮助”验证。定时消息需另行指定目标群和执行时间。</li>
              </ol>
            </Card>
            <Card title="可用查询指令">
              <Table rowKey="command" pagination={false} size="small" dataSource={commands.map(([command, description]) => ({ command, description }))} columns={[
                { title: '指令', dataIndex: 'command', key: 'command', render: value => <Text code>{value}</Text> },
                { title: '功能', dataIndex: 'description', key: 'description' },
              ]} />
              <Paragraph type="secondary" style={{ marginTop: 16, marginBottom: 0 }}>用户名为校队官网用户名。Rating 查询通过官网用户绑定邮箱匹配校队 OJ 用户；Tracker 使用官网已绑定的平台账号。指令格式错误时机器人会 @ 发起者并返回使用帮助。</Paragraph>
            </Card>
          </Space>,
        },
      ]} />
      <Modal title={editingTask ? '编辑定时消息' : '新增定时消息'} open={taskModalOpen} onCancel={() => setTaskModalOpen(false)} onOk={saveTask} confirmLoading={taskSaving} okText="保存" cancelText="取消" width={680} destroyOnHidden>
        <Form form={taskForm} layout="vertical" preserve={false} disabled={taskSaving}>
          <Form.Item name="title" label="任务标题" rules={[{ required: true, whitespace: true, message: '请输入任务标题' }]}><Input maxLength={100} /></Form.Item>
          <Form.Item name="groupId" label="目标 QQ 群号" rules={[{ required: true, message: '请输入目标 QQ 群号' }, { pattern: groupPattern, message: '群号必须是 5–15 位数字，且不能以 0 开头' }]}><Input maxLength={15} inputMode="numeric" /></Form.Item>
          <Form.Item name="content" label="消息文字" extra="文字与图片至少填写一项。消息按普通文本发送。"><Input.TextArea rows={4} maxLength={5000} showCount /></Form.Item>
          <Form.Item name="imageText" label="本站图片链接" extra="每行一个当前站点的公开 HTTPS 图片链接，最多 5 张、每张不超过 5MiB。请先在本站上传图片，并确保后端 PUBLIC_SITE_URL 与访问域名一致。" rules={[{
            validator: (_, value: string | undefined) => {
              const images = (value || '').split(/\r?\n/).map(item => item.trim()).filter(Boolean);
              if (images.length > 5) return Promise.reject(new Error('最多只能添加 5 张图片'));
              return images.every(item => {
                if (!validateUrl(item, ['https:'])) return false;
                const url = new URL(item);
                return url.hostname === window.location.hostname && (!url.port || url.port === '443');
              }) ? Promise.resolve() : Promise.reject(new Error('图片仅允许当前站点的公开 HTTPS 链接'));
            },
          }]}><Input.TextArea rows={3} placeholder="https://example.com/besti-acm-wordmark.svg" /></Form.Item>
          <Row gutter={16}>
            <Col xs={24} sm={12}><Form.Item name="recurrence" label="重复方式" rules={[{ required: true }]}><Select options={recurrenceOptions} /></Form.Item></Col>
            <Col xs={24} sm={12}><Form.Item name="dueAt" label="首次执行时间（北京时间）" rules={[
              { required: true, message: '请选择首次执行时间' },
              { validator: (_, value: Dayjs | undefined) => !value || (editingTask && value.format('YYYY-MM-DDTHH:mm:ss') === editingTask.dueAt) || dayjs.tz(value.format('YYYY-MM-DDTHH:mm:ss'), SHANGHAI).isAfter(dayjs()) ? Promise.resolve() : Promise.reject(new Error('首次执行时间必须晚于当前北京时间')) },
            ]}><DatePicker showTime={{ format: 'HH:mm:ss' }} format="YYYY-MM-DD HH:mm:ss" style={{ width: '100%' }} /></Form.Item></Col>
          </Row>
          {recurrence === 'INTERVAL' && <Form.Item name="intervalMinutes" label="循环间隔（分钟）" rules={[{ required: true, message: '请输入循环间隔' }]}><InputNumber min={1} max={525600} precision={0} style={{ width: '100%' }} /></Form.Item>}
          {recurrence === 'WEEKLY' && <Paragraph type="secondary">每周在首次执行时间对应的星期和时刻发送。</Paragraph>}
          <Form.Item name="enabled" label="启用此任务" valuePropName="checked"><Switch /></Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
