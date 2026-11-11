"use client";

import React, { useMemo, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Input,
  Modal,
  Progress,
  Radio,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  Upload,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  DeleteOutlined,
  DownloadOutlined,
  MailOutlined,
  SearchOutlined,
  SendOutlined,
  UploadOutlined,
  UserAddOutlined,
  UsergroupAddOutlined,
} from '@ant-design/icons';
import { MdEditor, MdPreview, type ExposeParam } from 'md-editor-rt';
import axios from 'axios';
import * as XLSX from 'xlsx';

interface Recipient {
  rowNumber?: number;
  userId?: number;
  name: string;
  email: string;
  numberId?: string;
  error?: string;
  fields?: Record<string, string>;
}

interface SendResult {
  successCount: number;
  failedCount: number;
  failedRecipients: Recipient[];
}

interface ImportResult {
  recipients: Recipient[];
  invalidRecipients: Recipient[];
  keywords: string[];
}

/** 每批发送的收件人数：单批耗时控制在 1 分钟内，避免触发网关/浏览器超时。 */
const CHUNK_SIZE = 50;
/** 估算用时用的保守值（含 TLS 握手，实测约 1~1.5 秒/封）。 */
const SECONDS_PER_EMAIL = 1.5;

const { Text, Title } = Typography;

const defaultTemplate = `请在这里填写正式的邮件正文。\n\n| 项目 | 内容 |\n| --- | --- |\n| 示例 | 可按需修改 |\n\n<span style="color:#1890ff"><strong>这是一段蓝色加粗文字示例</strong></span>`;

const BulkEmailSender: React.FC = () => {
  const editorRef = useRef<ExposeParam>(null);
  const [sourceMode, setSourceMode] = useState<'excel' | 'site'>('excel');
  const [subject, setSubject] = useState('');
  const [content, setContent] = useState(defaultTemplate);
  const [recipients, setRecipients] = useState<Recipient[]>([]);
  const [invalidRecipients, setInvalidRecipients] = useState<Recipient[]>([]);
  const [uploading, setUploading] = useState(false);
  const [loadingAllUsers, setLoadingAllUsers] = useState(false);
  const [sending, setSending] = useState(false);
  const [userOptions, setUserOptions] = useState<Recipient[]>([]);
  const [searching, setSearching] = useState(false);
  const [sendResult, setSendResult] = useState<SendResult | null>(null);
  const [progress, setProgress] = useState<{ done: number; total: number; success: number; failed: number } | null>(null);
  const [unfinished, setUnfinished] = useState<Recipient[]>([]);
  const [keywords, setKeywords] = useState<string[]>([]);
  const [siteUsernames, setSiteUsernames] = useState('');
  const [siteImportMode, setSiteImportMode] = useState<'username' | 'schoolNumber'>('username');
  const [schoolNumbers, setSchoolNumbers] = useState('');
  const [loadingBatchUsers, setLoadingBatchUsers] = useState(false);

  const dedupedRecipients = useMemo(() => {
    const map = new Map<string, Recipient>();
    recipients.forEach(item => {
      if (!item.email) return;
      map.set(item.email.toLowerCase(), item);
    });
    return Array.from(map.values());
  }, [recipients]);

  const addRecipients = (items: Recipient[]) => {
    setRecipients(prev => {
      const map = new Map<string, Recipient>();
      [...prev, ...items].forEach(item => {
        if (!item.email) return;
        map.set(item.email.toLowerCase(), item);
      });
      return Array.from(map.values());
    });
  };

  const handleTemplateDownload = () => {
    window.open('/api/admin/bulk-email/template', '_blank');
  };

  const parseExcel: NonNullable<React.ComponentProps<typeof Upload>['customRequest']> = async (options) => {
    const file = options.file as File;
    const formData = new FormData();
    formData.append('file', file);
    setUploading(true);
    try {
      const res = await axios.post('/api/admin/bulk-email/parse', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      if (res.data.code === 200) {
        setRecipients(res.data.data.recipients || []);
        setInvalidRecipients(res.data.data.invalidRecipients || []);
        setKeywords(res.data.data.keywords || []);
        setSendResult(null);
        message.success(`解析完成，可发送 ${res.data.data.recipients?.length || 0} 人`);
        options.onSuccess?.(res.data);
      } else {
        message.error(res.data.msg || '解析失败');
        options.onError?.(new Error(res.data.msg || '解析失败'));
      }
    } catch (error) {
      message.error('Excel 上传解析失败');
      options.onError?.(error as Error);
    } finally {
      setUploading(false);
    }
  };

  const searchSiteUsers = async (keyword: string) => {
    if (!keyword.trim()) {
      setUserOptions([]);
      return;
    }
    setSearching(true);
    try {
      const res = await axios.get('/api/admin/bulk-email/users/search', { params: { keyword } });
      if (res.data.code === 200) {
        setUserOptions(res.data.data || []);
      } else {
        message.error(res.data.msg || '搜索用户失败');
      }
    } finally {
      setSearching(false);
    }
  };

  const loadAllSiteUsers = async () => {
    setLoadingAllUsers(true);
    try {
      const res = await axios.get('/api/admin/bulk-email/users/all');
      if (res.data.code === 200) {
        setRecipients(res.data.data || []);
        setInvalidRecipients([]);
        setSendResult(null);
        message.success(`已选择 ${res.data.data?.length || 0} 位站内用户`);
      } else {
        message.error(res.data.msg || '加载用户失败');
      }
    } finally {
      setLoadingAllUsers(false);
    }
  };

  const loadBatchSiteUsers = async () => {
    const usernames = siteUsernames.trim();
    if (!usernames) {
      message.warning('请每行输入一个用户名');
      return;
    }
    setLoadingBatchUsers(true);
    try {
      const res = await axios.post('/api/admin/bulk-email/users/batch', { usernames });
      if (res.data.code === 200) {
        addRecipients(res.data.data || []);
        message.success(`已加入 ${res.data.data?.length || 0} 位站内用户`);
      } else {
        message.error(res.data.msg || '批量选择用户失败');
      }
    } finally {
      setLoadingBatchUsers(false);
    }
  };

  const addSchoolNumberImport = (result: ImportResult) => {
    addRecipients(result.recipients || []);
    setInvalidRecipients(result.invalidRecipients || []);
    setKeywords(previous => Array.from(new Set([...previous, ...(result.keywords || [])])));
    setSendResult(null);
    const count = result.recipients?.length || 0;
    if (count) message.success(`已匹配并加入 ${count} 位站内用户`);
    else message.warning('没有可加入的用户，请查看下方未加入的导入记录');
  };

  const loadSchoolNumberUsers = async () => {
    if (!schoolNumbers.trim()) {
      message.warning('请填写学校和学号，每行一人');
      return;
    }
    setLoadingBatchUsers(true);
    try {
      const res = await axios.post('/api/admin/bulk-email/users/batch-by-school-number', { content: schoolNumbers });
      if (res.data.code === 200) addSchoolNumberImport(res.data.data);
      else message.error(res.data.msg || '学校学号导入失败');
    } catch {
      message.error('学校学号导入失败，请重试');
    } finally {
      setLoadingBatchUsers(false);
    }
  };

  const parseSchoolNumberExcel: NonNullable<React.ComponentProps<typeof Upload>['customRequest']> = async options => {
    const formData = new FormData();
    formData.append('file', options.file as File);
    setUploading(true);
    try {
      const res = await axios.post('/api/admin/bulk-email/parse', formData, { params: { mode: 'schoolNumber' } });
      if (res.data.code === 200) {
        addSchoolNumberImport(res.data.data);
        options.onSuccess?.(res.data);
      } else {
        message.error(res.data.msg || '学校学号表格导入失败');
        options.onError?.(new Error(res.data.msg || '导入失败'));
      }
    } catch (error) {
      message.error('学校学号表格导入失败，请重试');
      options.onError?.(error as Error);
    } finally {
      setUploading(false);
    }
  };

  const removeRecipient = (email: string) => {
    setRecipients(prev => prev.filter(item => item.email.toLowerCase() !== email.toLowerCase()));
  };

  const insertText = (text: string) => {
    editorRef.current?.insert(() => ({
      targetValue: text,
      select: false,
      deviationStart: 0,
      deviationEnd: 0,
    }));
  };

  /**
   * 分批发送：1000 封以上不会把所有压力压在一个请求上。
   * 每批 50 封（约 1 分钟），网关超时、浏览器断开的风险都消除，并且能看到实时进度。
   * 注意：某批请求异常时不会自动重发——那一批可能已经在服务端发出去了。
   */
  const sendInChunks = async (targets: Recipient[]) => {
    const total = targets.length;
    let success = 0;
    const failed: Recipient[] = [];
    let done = 0;
    setSending(true);
    setSendResult(null);
    setUnfinished([]);
    setProgress({ done: 0, total, success: 0, failed: 0 });
    try {
      for (let offset = 0; offset < total; offset += CHUNK_SIZE) {
        const batchNo = Math.floor(offset / CHUNK_SIZE) + 1;
        const chunk = targets.slice(offset, offset + CHUNK_SIZE);
        let data: SendResult;
        try {
          const res = await axios.post(
            '/api/admin/bulk-email/send',
            { subject, htmlContent: content, recipients: chunk },
            { timeout: 0 },
          );
          if (res.data?.code !== 200) {
            message.error(`第 ${batchNo} 批被服务端拒绝：${res.data?.msg || '未知错误'}`);
            setUnfinished(targets.slice(offset));
            break;
          }
          data = res.data.data as SendResult;
        } catch {
          message.error(
            `第 ${batchNo} 批请求异常（网络中断或网关超时），这一批可能仍在服务端发送中。` +
            `请先查看后端日志确认，不要直接重发，避免重复收信。`,
          );
          setUnfinished(targets.slice(offset));
          break;
        }
        success += data.successCount || 0;
        if (data.failedRecipients?.length) failed.push(...data.failedRecipients);
        done = offset + chunk.length;
        setProgress({ done, total, success, failed: failed.length });
      }
      setSendResult({ successCount: success, failedCount: failed.length, failedRecipients: failed });
      if (done >= total) {
        message.success(`发送完成：成功 ${success} 人，失败 ${failed.length} 人`);
      }
    } catch {
      message.error('发送过程中出现异常，请用后端日志确认已发送数量后再决定是否重发。');
    } finally {
      setSending(false);
      setProgress(null);
    }
  };

  const estimateMinutes = (count: number) => Math.max(1, Math.round((count * SECONDS_PER_EMAIL) / 60));

  const submitSend = async () => {
    if (!subject.trim()) {
      message.warning('请填写邮件主题');
      return;
    }
    if (!content.trim()) {
      message.warning('请填写邮件模板内容');
      return;
    }
    if (dedupedRecipients.length === 0) {
      message.warning('请先添加收件人');
      return;
    }

    const total = dedupedRecipients.length;
    const batchCount = Math.ceil(total / CHUNK_SIZE);

    Modal.confirm({
      title: '确认发送批量邮件',
      width: 860,
      okText: `确认发送 ${total} 封`,
      cancelText: '取消',
      icon: <MailOutlined />,
      content: (
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Alert
            type="warning"
            showIcon
            message={`本次将发送给 ${total} 位收件人，邮件发送后无法撤回。`}
            description={
              total > CHUNK_SIZE
                ? `为避免超时，将分 ${batchCount} 批发送（每批 ${CHUNK_SIZE} 封），预计用时约 ${estimateMinutes(total)} 分钟。发送期间请保持本页面打开，不要关闭或刷新。`
                : undefined
            }
          />
          <div>
            <Text strong>邮件主题：</Text>
            <Text>{subject}</Text>
          </div>
          <div style={{ maxHeight: 180, overflow: 'auto', border: '1px solid #f0f0f0', borderRadius: 6, padding: 12 }}>
            {dedupedRecipients.slice(0, 30).map(item => (
              <Tag key={item.email} style={{ marginBottom: 8 }}>
                {item.name} &lt;{item.email}&gt;
              </Tag>
            ))}
            {dedupedRecipients.length > 30 && <Tag>还有 {dedupedRecipients.length - 30} 人</Tag>}
          </div>
          <div style={{ border: '1px solid #f0f0f0', borderRadius: 6, padding: 12, maxHeight: 260, overflow: 'auto' }}>
            <MdPreview modelValue={content.split('{姓名}').join(dedupedRecipients[0]?.name || '姓名')} />
          </div>
        </Space>
      ),
      // 不返回 Promise：让确认框立刻关闭，页面上的进度条才能被看到（1000 封会跑十几分钟）。
      onOk: () => {
        void sendInChunks(dedupedRecipients);
      },
    });
  };

  const downloadFailedList = (list: Recipient[], fileName = '批量邮件发送失败名单.xlsx') => {
    const worksheet = XLSX.utils.json_to_sheet(list.map(item => ({
      姓名: item.name,
      邮件地址: item.email,
      失败原因: item.error || '邮件发送失败',
    })));
    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, worksheet, '失败名单');
    XLSX.writeFile(workbook, fileName);
  };

  const columns: ColumnsType<Recipient> = [
    { title: '姓名', dataIndex: 'name', width: 140 },
    { title: '学校', width: 160, render: (_, record) => record.fields?.['学校'] || '-' },
    { title: '邮件地址', dataIndex: 'email', ellipsis: true },
    { title: '学号/工号', dataIndex: 'numberId', width: 140, render: (value?: string) => value || '-' },
    {
      title: '来源',
      width: 90,
      render: (_, record) => record.userId ? <Tag color="blue">站内</Tag> : <Tag>Excel</Tag>,
    },
    {
      title: '操作',
      width: 90,
      render: (_, record) => (
        <Button size="small" danger icon={<DeleteOutlined />} onClick={() => removeRecipient(record.email)} />
      ),
    },
  ];

  const invalidColumns: ColumnsType<Recipient> = [
    { title: '行号', dataIndex: 'rowNumber', width: 80 },
    { title: '学校', width: 160, render: (_, record) => record.fields?.['学校'] || '-' },
    { title: '学号/工号', dataIndex: 'numberId', width: 140 },
    { title: '姓名', dataIndex: 'name', width: 140 },
    { title: '邮件地址', dataIndex: 'email' },
    { title: '原因', dataIndex: 'error', width: 180 },
  ];

  return (
    <Space direction="vertical" size={20} style={{ width: '100%' }}>
      <div>
        <Title level={3} style={{ marginTop: 0, marginBottom: 6 }}>批量邮件发送</Title>
        <Text type="secondary">仅超级管理员可使用。邮件模板为临时内容，不会保存。</Text>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 360px), 1fr))', gap: 20 }}>
        <div style={{ border: '1px solid #f0f0f0', borderRadius: 8, padding: 16 }}>
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Radio.Group
              value={sourceMode}
              onChange={e => setSourceMode(e.target.value)}
              optionType="button"
              buttonStyle="solid"
              options={[
                { label: 'Excel 导入', value: 'excel' },
                { label: '站内用户', value: 'site' },
              ]}
            />

            {sourceMode === 'excel' ? (
              <Space direction="vertical" size={12} style={{ width: '100%' }}>
                <Button icon={<DownloadOutlined />} onClick={handleTemplateDownload}>
                  下载发送模板表格
                </Button>
                <Upload.Dragger
                  accept=".xls,.xlsx"
                  maxCount={1}
                  customRequest={parseExcel}
                  showUploadList={false}
                  disabled={uploading}
                >
                  <p><UploadOutlined style={{ fontSize: 26, color: '#1677ff' }} /></p>
                  <p>上传包含“姓名、邮件地址”的 Excel</p>
                </Upload.Dragger>
                {invalidRecipients.length > 0 && (
                  <Alert
                    type="warning"
                    showIcon
                    message={`有 ${invalidRecipients.length} 条无效数据，已自动排除`}
                  />
                )}
              </Space>
            ) : (
              <Space direction="vertical" size={12} style={{ width: '100%' }}>
                <Button
                  type="primary"
                  ghost
                  icon={<UsergroupAddOutlined />}
                  loading={loadingAllUsers}
                  onClick={loadAllSiteUsers}
                >
                  全选所有有邮箱的站内用户
                </Button>
                <Select
                  showSearch
                  value={undefined}
                  placeholder="搜索姓名、账号、学号或邮箱后加入"
                  filterOption={false}
                  onSearch={searchSiteUsers}
                  loading={searching}
                  suffixIcon={<SearchOutlined />}
                  style={{ width: '100%' }}
                  onSelect={(value) => {
                    const target = userOptions.find(item => item.email === value);
                    if (target) {
                      addRecipients([target]);
                      message.success(`已加入 ${target.name}`);
                    }
                  }}
                  options={userOptions.map(item => ({
                    value: item.email,
                    label: `${item.name} ${item.numberId ? `(${item.numberId})` : ''} - ${item.email}`,
                  }))}
                />
                <Radio.Group
                  value={siteImportMode}
                  onChange={event => setSiteImportMode(event.target.value)}
                  options={[{ label: '按用户名', value: 'username' }, { label: '按学校＋学号', value: 'schoolNumber' }]}
                />
                {siteImportMode === 'username' ? <>
                  <Input.TextArea
                    value={siteUsernames}
                    onChange={e => setSiteUsernames(e.target.value)}
                    placeholder="批量输入用户名，每行一个，例如：\nzhangsan\nlisi123"
                    autoSize={{ minRows: 4, maxRows: 8 }}
                  />
                  <Button loading={loadingBatchUsers} onClick={loadBatchSiteUsers} icon={<UserAddOutlined />}>
                    按用户名批量加入
                  </Button>
                </> : <>
                  <Text type="secondary">填写学校全称（大学）和学号，每行一人，可直接粘贴 Excel 两列，也支持用空格、逗号或＋分隔。系统自动读取站内绑定邮箱。</Text>
                  <Input.TextArea
                    aria-label="学校和学号导入名单"
                    value={schoolNumbers}
                    onChange={event => setSchoolNumbers(event.target.value)}
                    placeholder={'学校\t学号\n北京电子科技学院\t00123456'}
                    autoSize={{ minRows: 4, maxRows: 8 }}
                  />
                  <Button loading={loadingBatchUsers} onClick={loadSchoolNumberUsers} icon={<UserAddOutlined />}>按学校＋学号批量加入</Button>
                  <Space wrap>
                    <Button icon={<DownloadOutlined />} onClick={() => window.open('/api/admin/bulk-email/template?mode=schoolNumber', '_blank')}>下载学校学号模板</Button>
                    <Upload accept=".xls,.xlsx" maxCount={1} customRequest={parseSchoolNumberExcel} showUploadList={false} disabled={uploading}>
                      <Button loading={uploading} icon={<UploadOutlined />}>导入学校学号 Excel</Button>
                    </Upload>
                  </Space>
                  {invalidRecipients.length > 0 && <Alert type="warning" showIcon title={`${invalidRecipients.length} 条记录未加入，请核对下方原因`} />}
                </>}
                <Text type="secondary">搜索结果仅展示已绑定有效邮箱的用户。</Text>
              </Space>
            )}

            <div>
              <Space style={{ marginBottom: 10 }}>
                <Text strong>当前收件人</Text>
                <Tag color="processing">{dedupedRecipients.length} 人</Tag>
              </Space>
              <Table
                size="small"
                rowKey="email"
                columns={columns}
                dataSource={dedupedRecipients}
                pagination={{ pageSize: 6, size: 'small' }}
                scroll={{ x: 720 }}
              />
            </div>
          </Space>
        </div>

        <div style={{ border: '1px solid #f0f0f0', borderRadius: 8, padding: 16 }}>
          <Space direction="vertical" size={14} style={{ width: '100%' }}>
            <Input
              size="large"
              prefix={<MailOutlined />}
              placeholder="邮件主题"
              value={subject}
              onChange={e => setSubject(e.target.value)}
              maxLength={120}
              showCount
            />
            <Space wrap>
              <Button icon={<UserAddOutlined />} onClick={() => insertText('{姓名}')}>插入姓名占位符</Button>
              {keywords.slice(0, 20).map(keyword => (
                <Button key={keyword} size="small" onClick={() => insertText(`{${keyword}}`)}>
                  插入 {keyword}
                </Button>
              ))}
              <Button onClick={() => insertText('&nbsp;&nbsp;&nbsp;&nbsp;')}>插入缩进</Button>
              <Button onClick={() => insertText('\n\n| 项目 | 内容 |\n| --- | --- |\n|  |  |\n')}>插入表格</Button>
              <Button onClick={() => insertText('<span style="color:#ff4d4f"><strong>红色加粗文字</strong></span>')}>插入彩色文字</Button>
            </Space>
            <MdEditor
              ref={editorRef}
              modelValue={content}
              onChange={setContent}
              style={{ height: 460 }}
            />
            <Button
              type="primary"
              size="large"
              icon={<SendOutlined />}
              loading={sending}
              onClick={submitSend}
            >
              确认名单与模板后发送
            </Button>
          </Space>
        </div>
      </div>

      {invalidRecipients.length > 0 && (
        <div style={{ border: '1px solid #faad14', borderRadius: 8, padding: 16 }}>
          <Title level={5} style={{ marginTop: 0 }}>未加入的导入记录</Title>
          <Table size="small" rowKey={(record) => `${record.rowNumber}-${record.email}`} columns={invalidColumns} dataSource={invalidRecipients} pagination={{ pageSize: 5 }} scroll={{ x: 800 }} />
        </div>
      )}

      {progress && (
        <Alert
          type="info"
          showIcon
          message={`正在发送：已处理 ${progress.done} / ${progress.total} 人（成功 ${progress.success}，失败 ${progress.failed}）`}
          description={
            <Space direction="vertical" size={6} style={{ width: '100%' }}>
              <Progress percent={Math.round((progress.done / Math.max(progress.total, 1)) * 100)} status="active" />
              <Text type="secondary">
                分批发送中，请保持本页面打开；整体进度按批次推进，单批最多 {CHUNK_SIZE} 封。
              </Text>
            </Space>
          }
        />
      )}

      {unfinished.length > 0 && (
        <Alert
          type="error"
          showIcon
          message={`有 ${unfinished.length} 位收件人未确认发送完成`}
          description={
            <Space direction="vertical" size={8}>
              <Text>
                最后失败的那一批可能仍在服务端发送中（后端日志会记录完整结果），请先确认后再决定是否重发，
                以免同一批收到两封邮件。
              </Text>
              <Space wrap>
                <Button size="small" icon={<DownloadOutlined />} onClick={() => downloadFailedList(unfinished, '批量邮件未发送名单.xlsx')}>
                  下载未完成名单
                </Button>
                <Button
                  size="small"
                  danger
                  onClick={() => sendInChunks(unfinished)}
                  disabled={sending}
                >
                  仍然重发这 {unfinished.length} 封
                </Button>
              </Space>
            </Space>
          }
        />
      )}

      {sendResult && (
        <Alert
          type={sendResult.failedCount > 0 ? 'warning' : 'success'}
          showIcon
          message={`发送完成：成功 ${sendResult.successCount} 人，失败 ${sendResult.failedCount} 人`}
          action={sendResult.failedCount > 0 ? (
            <Button size="small" icon={<DownloadOutlined />} onClick={() => downloadFailedList(sendResult.failedRecipients)}>
              下载失败名单
            </Button>
          ) : undefined}
        />
      )}
    </Space>
  );
};

export default BulkEmailSender;
