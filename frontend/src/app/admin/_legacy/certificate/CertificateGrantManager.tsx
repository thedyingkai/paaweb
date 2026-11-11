"use client";
/* eslint-disable @typescript-eslint/no-explicit-any, react-hooks/exhaustive-deps, react-hooks/set-state-in-effect */

import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  DatePicker,
  Form,
  Input,
  InputNumber,
  List,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Statistic,
  Switch,
  Table,
  Tag,
  Upload,
  message,
} from 'antd';
import {
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  EyeOutlined,
  FileExcelOutlined,
  FilePdfOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  UnorderedListOutlined,
  UsergroupAddOutlined,
} from '@ant-design/icons';
import axios from 'axios';
import dayjs from 'dayjs';
import CertificateTemplateDesigner, { CertificateQrCodeBlock, CertificateTextBlock, defaultQrCodeBlock, defaultTemplateBlocks } from './CertificateTemplateDesigner';
import ImportValidationPreview, { ImportPreviewData } from '../components/ImportPreview';
import { downloadExcelTemplate, readExcelRows, rowsToTsv } from '@/utils/excelImport';

type ImportPreview = {
  valid: boolean;
  certificateCount: number;
  recipientCount: number;
  rows: Array<{
    rowNumber: number;
    teamName?: string;
    awardName: string;
    certificateNo: string;
    members: Array<{ name: string; studentId: string }>;
  }>;
  errors: Array<{ rowNumber: number; columnName: string; message: string }>;
};

type LevelScopeOption = {
  label: string;
  value: number;
  levelType: string;
};

type IdentityReviewRow = {
  recordKey: string;
  userAccount?: string;
  realName?: string;
  schoolName?: string;
  userId?: number;
  context?: string;
};

type IdentityConfirmation = {
  recordKey: string;
  userId: number;
  school: string;
  numberId: string;
  realName: string;
};

const displayLevelScope = (levelScope?: string) => {
  if (!levelScope) return '-';
  if (levelScope.includes('校') && !levelScope.includes('市')) return '校（市）级';
  if (levelScope.includes('院')) return '院级';
  return levelScope;
};

const displayIssueStatus = (status?: string) => {
  if (status === 'revoked') return <Tag color="red">已撤回</Tag>;
  if (status === 'active') return <Tag color="green">有效</Tag>;
  return <Tag>旧版</Tag>;
};

const CertificateGrantManager: React.FC = () => {
  const [templates, setTemplates] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [filterTerm, setFilterTerm] = useState<number | null>(null);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [terms, setTerms] = useState<any[]>([]);
  const [levelOptions, setLevelOptions] = useState<LevelScopeOption[]>([]);
  const [levelTypeById, setLevelTypeById] = useState<Record<number, string>>({});

  const [templateModalOpen, setTemplateModalOpen] = useState(false);
  const [isEditMode, setIsEditMode] = useState(false);
  const [editRecord, setEditRecord] = useState<any>(null);
  const [templateFileId, setTemplateFileId] = useState<number | null>(null);
  const [templateBlocks, setTemplateBlocks] = useState<CertificateTextBlock[]>([]);
  const [templateQrCode, setTemplateQrCode] = useState<CertificateQrCodeBlock>(defaultQrCodeBlock());
  const [templateSaving, setTemplateSaving] = useState(false);
  const [templatePreviewing, setTemplatePreviewing] = useState(false);
  const [form] = Form.useForm();
  const watchedIsTeam = Form.useWatch('isTeam', form) === 1;

  const [grantOpen, setGrantOpen] = useState(false);
  const [selectedTemplate, setSelectedTemplate] = useState<any>(null);
  const [importFile, setImportFile] = useState<File | null>(null);
  const [importPreview, setImportPreview] = useState<ImportPreview | null>(null);
  const [importLoading, setImportLoading] = useState(false);
  const [issueLoading, setIssueLoading] = useState(false);
  const [legacyImportRows, setLegacyImportRows] = useState<string[][]>([]);
  const [legacyImportPreview, setLegacyImportPreview] = useState<ImportPreviewData | null>(null);

  const [listManageOpen, setListManageOpen] = useState(false);
  const [issuedList, setIssuedList] = useState<any[]>([]);
  const [issuedLoading, setIssuedLoading] = useState(false);
  const [listSearchText, setListSearchText] = useState('');
  const [selectedGrantIds, setSelectedGrantIds] = useState<React.Key[]>([]);

  const [identityReviewOpen, setIdentityReviewOpen] = useState(false);
  const [identityReviewTypeId, setIdentityReviewTypeId] = useState<number | undefined>();
  const [identityRows, setIdentityRows] = useState<IdentityReviewRow[]>([]);
  const [identityLoading, setIdentityLoading] = useState(false);
  const [identitySaving, setIdentitySaving] = useState(false);
  const [identityForm] = Form.useForm<IdentityConfirmation>();

  const fetchIdentityRows = async (typeId?: number) => {
    const res = await axios.get<{ code: number; msg?: string; data?: IdentityReviewRow[] }>('/api/certificate/grant/identity-review/list', { params: { typeId } });
    if (res.data.code !== 200) throw new Error(res.data.msg || '待确认记录加载失败');
    return res.data.data || [];
  };

  const openIdentityReview = async (typeId?: number) => {
    if (identityLoading) return;
    setIdentityLoading(true);
    try {
      const rows = await fetchIdentityRows(typeId);
      setIdentityRows(rows);
      setIdentityReviewTypeId(typeId);
      identityForm.resetFields();
      setIdentityReviewOpen(true);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '待确认记录加载失败，请重试');
    } finally { setIdentityLoading(false); }
  };
  const confirmIdentity = async () => {
    if (identitySaving) return;
    let values: IdentityConfirmation;
    try { values = await identityForm.validateFields(); }
    catch { return; }
    setIdentitySaving(true);
    try {
      const res = await axios.post<{ code: number; msg?: string }>('/api/certificate/grant/identity-review/confirm', values);
      if (res.data.code !== 200) return message.error(res.data.msg || '确认失败');
      message.success('证书归属已确认');
      setIdentityRows(rows => rows.filter(row => row.recordKey !== values.recordKey));
      identityForm.resetFields();
      const [identityRefresh, issuedRefresh] = await Promise.allSettled([
        fetchIdentityRows(identityReviewTypeId),
        selectedTemplate && listManageOpen ? fetchIssuedList(selectedTemplate.certificateTypeId) : Promise.resolve(true),
      ]);
      if (identityRefresh.status === 'fulfilled') setIdentityRows(identityRefresh.value);
      else message.warning('归属已确认，待确认记录刷新失败，请稍后重新打开列表');
      if (issuedRefresh.status !== 'fulfilled' || !issuedRefresh.value) message.warning('归属已确认，发放名单刷新失败，请稍后刷新');
    } catch { message.error('确认请求失败，请重新打开列表核实是否已确认'); }
    finally { setIdentitySaving(false); }
  };

  const user = typeof window === 'undefined' ? {} : JSON.parse(localStorage.getItem('user_info') || '{}');

  const fetchTemplates = async () => {
    setLoading(true);
    try {
      const res = await axios.get('/api/certificate/grant/template/list', {
        params: { current: 1, size: 100, termId: filterTerm, keyword: searchKeyword },
      });
      if (res.data.code === 200) setTemplates(res.data.data.records || []);
    } catch {
      message.error('证书模板加载失败');
    } finally {
      setLoading(false);
    }
  };

  const fetchOptions = async () => {
    try {
      const [termRes, levelRes] = await Promise.all([
        axios.get('/api/certificate/terms'),
        axios.get('/api/certificate/levels'),
      ]);
      setTerms(termRes.data.data || []);
      const representatives: Record<string, any> = {};
      const lookup: Record<number, string> = {};
      (levelRes.data.data || []).forEach((level: any) => {
        if (!level.levelType) return;
        lookup[level.levelId] = level.levelType;
        const current = representatives[level.levelType];
        const currentIsOther = String(current?.rankType || current?.name || '').includes('其他');
        const candidateIsOther = String(level.rankType || level.name || '').includes('其他');
        const currentScore = Number(current?.baseRating ?? Number.MAX_SAFE_INTEGER);
        const candidateScore = Number(level.baseRating ?? Number.MAX_SAFE_INTEGER);
        if (!current || (candidateIsOther && !currentIsOther) || (candidateIsOther === currentIsOther && candidateScore < currentScore)) {
          representatives[level.levelType] = level;
        }
      });
      setLevelTypeById(lookup);
      setLevelOptions(Object.values(representatives).map((level: any) => ({
        label: displayLevelScope(level.levelType),
        value: level.levelId,
        levelType: level.levelType,
      })));
    } catch {
      message.error('学期或等级大类加载失败');
    }
  };

  useEffect(() => {
    fetchTemplates();
  }, [filterTerm]);

  useEffect(() => {
    fetchOptions();
  }, []);

  const getTermName = (termId: number) => terms.find(item => item.termId === termId)?.termName || termId;
  const getLevelInfo = (record: any) => {
    const option = levelOptions.find(item => item.value === record.typeId);
    return displayLevelScope(record.levelScope || option?.levelType || levelTypeById[record.typeId]);
  };
  const getLevelValue = (record: any) => {
    const levelScope = record.levelScope || levelTypeById[record.typeId];
    if (levelScope) {
      return levelOptions.find(item => item.levelType === levelScope)?.value || record.typeId;
    }
    return record.typeId;
  };

  const openCreate = () => {
    setIsEditMode(false);
    setEditRecord(null);
    setTemplateFileId(null);
    setTemplateBlocks(defaultTemplateBlocks(false));
    setTemplateQrCode(defaultQrCodeBlock());
    form.resetFields();
    form.setFieldsValue({ isTeam: 0, fontSize: 24, ratingEnabled: true });
    setTemplateModalOpen(true);
  };

  const openEdit = (record: any) => {
    setIsEditMode(true);
    setEditRecord(record);
    setTemplateFileId(record.urlId || null);
    let blocks: CertificateTextBlock[] = [];
    let qrCode = defaultQrCodeBlock();
    if (record.templateSchema) {
      try {
        const schema = JSON.parse(record.templateSchema);
        blocks = (schema.blocks || []).map((block: CertificateTextBlock) => ({
          ...block,
          bold: block.bold === true,
          underline: block.underline === true,
        }));
        qrCode = { ...defaultQrCodeBlock(), ...(schema.qrCode || {}) };
      } catch {
        blocks = [];
      }
    }
    setTemplateBlocks(blocks.length > 0 ? blocks : defaultTemplateBlocks(record.isTeam === 1));
    setTemplateQrCode(qrCode);
    form.setFieldsValue({
      typeName: record.typeName,
      level: getLevelValue(record),
      term: record.term,
      time: record.time ? dayjs(record.time) : null,
      fontSize: record.fontSize || 24,
      isTeam: record.isTeam || 0,
      ratingEnabled: record.ratingEnabled !== false,
    });
    setTemplateModalOpen(true);
  };

  const onCertificateKindChange = (value: number) => {
    const hasOnlyDefaults = templateBlocks.length === 0 || templateBlocks.length === 3 || templateBlocks.length === 4;
    if (!isEditMode && hasOnlyDefaults) setTemplateBlocks(defaultTemplateBlocks(value === 1));
  };

  const templateSchemaPayload = () => JSON.stringify({ version: 2, blocks: templateBlocks, qrCode: templateQrCode });

  const saveTemplate = async (values: any) => {
    if (!templateFileId) return message.error('请先上传 PDF 模板文件');
    if (templateBlocks.length === 0 && !templateQrCode.enabled) return message.error('请至少添加一个文字块或二维码');
    setTemplateSaving(true);
    try {
      const selectedLevel = levelOptions.find(item => item.value === values.level);
      const payload: any = {
        typeName: values.typeName,
        typeId: values.level,
        levelScope: selectedLevel?.levelType,
        term: values.term,
        time: values.time.format('YYYY-MM-DD'),
        fontSize: Number(values.fontSize || 24),
        isTeam: values.isTeam,
        ratingEnabled: values.ratingEnabled !== false,
        updateUserId: user.userId,
        urlId: templateFileId,
        templateSchema: templateSchemaPayload(),
      };
      const url = isEditMode ? '/api/certificate/grant/template/update' : '/api/certificate/grant/template/add';
      if (isEditMode) payload.certificateTypeId = editRecord.certificateTypeId;
      const res = await axios.post(url, payload);
      if (res.data.code !== 200) throw new Error(res.data.msg || '保存失败');
      message.success(isEditMode ? '模板已更新' : '模板已创建');
      setTemplateModalOpen(false);
      fetchTemplates();
    } catch (error: any) {
      message.error(axios.isAxiosError(error)
        ? error.response?.data?.msg || '模板保存失败，请稍后重试'
        : error.message || '模板保存失败');
    } finally {
      setTemplateSaving(false);
    }
  };

  const previewTemplate = async () => {
    if (!templateFileId) return message.warning('请先上传 PDF 模板');
    setTemplatePreviewing(true);
    try {
      const time = form.getFieldValue('time');
      const res = await axios.post('/api/certificate/grant/template/preview', {
        fileId: templateFileId,
        templateSchema: templateSchemaPayload(),
        isTeam: form.getFieldValue('isTeam') || 0,
        issueDate: time ? time.format('YYYY-MM-DD') : dayjs().format('YYYY-MM-DD'),
      }, { responseType: 'blob' });
      const url = URL.createObjectURL(res.data);
      window.open(url, '_blank', 'noopener,noreferrer');
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (error: any) {
      const text = error.response?.data instanceof Blob ? await error.response.data.text() : '预览生成失败';
      message.error(text || '预览生成失败');
    } finally {
      setTemplatePreviewing(false);
    }
  };

  const openGrant = (record: any) => {
    setSelectedTemplate(record);
    setImportFile(null);
    setImportPreview(null);
    setLegacyImportRows([]);
    setLegacyImportPreview(null);
    setGrantOpen(true);
  };

  const previewImport = async (file: File) => {
    setImportFile(file);
    setImportPreview(null);
    setImportLoading(true);
    try {
      const data = new FormData();
      data.append('typeId', String(selectedTemplate.certificateTypeId));
      data.append('file', file);
      const res = await axios.post('/api/certificate/grant/import/preview', data);
      if (res.data.code !== 200) throw new Error(res.data.msg || '预检失败');
      setImportPreview(res.data.data);
      if (res.data.data.valid) message.success('Excel 预检通过，请核对后确认签发');
    } catch (error: any) {
      message.error(error.message || 'Excel 预检失败');
    } finally {
      setImportLoading(false);
    }
  };

  const issueImport = async () => {
    if (!importFile || !importPreview?.valid) return;
    setIssueLoading(true);
    try {
      const data = new FormData();
      data.append('typeId', String(selectedTemplate.certificateTypeId));
      data.append('file', importFile);
      const res = await axios.post('/api/certificate/grant/import/distribute', data);
      if (res.data.code !== 200) throw new Error(res.data.msg || '签发失败');
      Modal.success({ title: '证书签发完成', content: res.data.data.message });
      setGrantOpen(false);
    } catch (error: any) {
      message.error(error.message || '证书签发失败');
    } finally {
      setIssueLoading(false);
    }
  };

  const grantLegacyTemplate = async () => {
    if (!legacyImportRows.length) return message.warning('请先上传填写好的 Excel 文件');
    setIssueLoading(true);
    try {
      const confirming = Boolean(legacyImportPreview);
      const res = await axios.post('/api/certificate/grant/distribute', {
        typeId: selectedTemplate.certificateTypeId,
        studentIds: rowsToTsv(legacyImportRows),
        preview: !confirming,
      });
      if (res.data.code !== 200) throw new Error(res.data.msg || '发放失败');
      if (!confirming) {
        setLegacyImportPreview(res.data.data);
        return;
      }
      Modal.success({ title: '旧版证书发放完成', content: res.data.data.message || String(res.data.data) });
      setGrantOpen(false);
    } catch (error: any) {
      message.error(error.message || '发放失败');
    } finally {
      setIssueLoading(false);
    }
  };

  const legacyHeaders = selectedTemplate?.isTeam === 1
    ? Array.from({ length: 10 }, (_, index) => [`队员${index + 1}姓名`, `队员${index + 1}学号`, `队员${index + 1}学校`]).flat()
    : ['姓名', '学号', '学校'];

  const selectLegacyImportFile = async (file: File) => {
    try {
      const rows = await readExcelRows(file, legacyHeaders);
      if (!rows.length) {
        message.warning('Excel 中没有待导入数据');
        return false;
      }
      setLegacyImportRows(rows);
      setLegacyImportPreview(null);
      message.success(`已读取 ${rows.length} 条数据，请上传校验`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'Excel 读取失败');
    }
    return false;
  };

  const openListManage = (record: any) => {
    setSelectedTemplate(record);
    setListManageOpen(true);
    setListSearchText('');
    setSelectedGrantIds([]);
    fetchIssuedList(record.certificateTypeId);
  };

  const fetchIssuedList = async (typeId: number) => {
    setIssuedLoading(true);
    try {
      const res = await axios.get('/api/certificate/grant/record/list', { params: { typeId } });
      setIssuedList(res.data.code === 200 ? res.data.data || [] : []);
      return res.data.code === 200;
    } catch {
      setIssuedList([]);
      return false;
    } finally {
      setIssuedLoading(false);
    }
  };

  const revoke = async (grantId: number) => {
    const res = await axios.post(`/api/certificate/grant/revoke/${grantId}`);
    if (res.data.code !== 200) return message.error(res.data.msg || '撤回失败');
    message.success(res.data.data || '撤回成功');
    fetchIssuedList(selectedTemplate.certificateTypeId);
  };

  const batchRevoke = async () => {
    const res = await axios.post('/api/certificate/grant/revoke/batch', selectedGrantIds);
    if (res.data.code !== 200) return message.error(res.data.msg || '批量撤回失败');
    message.success(res.data.data);
    setSelectedGrantIds([]);
    fetchIssuedList(selectedTemplate.certificateTypeId);
  };

  const filteredIssuedList = useMemo(() => {
    const keyword = listSearchText.trim();
    if (!keyword) return issuedList;
    return issuedList.filter(item => [item.userAccount, item.realName, item.customName, item.certificateNo, item.levelScope, item.status]
      .some(value => String(value || '').includes(keyword)));
  }, [issuedList, listSearchText]);

  const templateColumns = [
    { title: 'ID', dataIndex: 'certificateTypeId', width: 64 },
    { title: '证书名称', dataIndex: 'typeName', width: 210 },
    { title: '等级大类', render: (_: any, record: any) => <Tag color="blue">{getLevelInfo(record)}</Tag> },
    { title: '类型', dataIndex: 'isTeam', width: 90, render: (value: number) => value === 1 ? <Tag color="purple">团队</Tag> : <Tag color="green">个人</Tag> },
    { title: 'Rating积分', dataIndex: 'ratingEnabled', width: 110, render: (value: boolean) => value === false ? <Tag>不参与</Tag> : <Tag color="gold">参与</Tag> },
    { title: '所属学期', dataIndex: 'term', render: getTermName },
    { title: '落款日期', dataIndex: 'time', render: (value: string) => value ? dayjs(value).format('YYYY-MM-DD') : '-' },
    { title: '模板版本', dataIndex: 'templateSchema', render: (value: string) => value ? <Tag color="cyan">占位符模板</Tag> : <Tag>旧版表单域</Tag> },
    {
      title: '操作', width: 270, render: (_: any, record: any) => (
        <Space size="small">
          <Button type="primary" ghost size="small" icon={<UsergroupAddOutlined />} onClick={() => openGrant(record)}>发放</Button>
          <Button size="small" icon={<UnorderedListOutlined />} onClick={() => openListManage(record)}>名单</Button>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>编辑</Button>
        </Space>
      ),
    },
  ];

  const issuedColumns = [
    { title: '证书编号', dataIndex: 'certificateNo', width: 180, render: (value: string) => value || <Tag>旧证书无编号</Tag> },
    { title: '队伍/姓名', dataIndex: 'teamName', render: (value: string, record: any) => value || record.customName || record.realName },
    { title: '获奖人学号', dataIndex: 'userAccount', ellipsis: true },
    { title: '等级大类', dataIndex: 'levelScope', render: (value: string) => value ? <Tag color="blue">{displayLevelScope(value)}</Tag> : '-' },
    { title: '奖项', dataIndex: 'awardName', render: (value: string) => value || '-' },
    { title: '落款日期', dataIndex: 'issueDate', render: (value: string) => value || '-' },
    { title: '状态', dataIndex: 'status', render: displayIssueStatus },
    { title: '发放时间', dataIndex: 'createTime', render: (value: string) => value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-' },
    {
      title: '操作', width: 90, render: (_: any, record: any) => (
        record.status === 'revoked' ? <Tag>不可操作</Tag> : (
          <Popconfirm title="撤回后该编号会显示为无效，确定继续吗？" onConfirm={() => revoke(record.certificateDownloadId)}>
            <Button danger size="small" icon={<DeleteOutlined />}>撤回</Button>
          </Popconfirm>
        )
      ),
    },
  ];

  const importColumns = [
    { title: 'Excel行', dataIndex: 'rowNumber', width: 80 },
    { title: '队伍名称', dataIndex: 'teamName', render: (value: string) => value || '-' },
    { title: '获奖人', dataIndex: 'members', render: (members: any[]) => members.map(item => `${item.name}（${item.studentId}${item.school ? `·${item.school}` : ''}）`).join('、') },
    { title: '奖项', dataIndex: 'awardName' },
    { title: '编号', dataIndex: 'certificateNo' },
  ];

  return (
    <Card
      title="证书发放管理"
      extra={(
        <Space wrap>
          <Select placeholder="筛选学期" style={{ width: 150 }} allowClear onChange={setFilterTerm} options={terms.map(item => ({ label: item.termName, value: item.termId }))} />
          <Input.Search placeholder="搜索证书名称" allowClear onSearch={fetchTemplates} onChange={event => setSearchKeyword(event.target.value)} style={{ width: 210 }} />
          <Button loading={identityLoading} onClick={() => openIdentityReview()}>历史归属确认</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新建模板</Button>
        </Space>
      )}
    >
      <Table dataSource={templates} columns={templateColumns} rowKey="certificateTypeId" loading={loading} scroll={{ x: 1100 }} />

      <Modal
        title={isEditMode ? '编辑证书模板' : '新建证书模板'}
        open={templateModalOpen}
        onCancel={() => setTemplateModalOpen(false)}
        footer={null}
        width={1180}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" onFinish={saveTemplate}>
          <Row gutter={16}>
            <Col span={8}><Form.Item name="typeName" label="证书名称" rules={[{ required: true, message: '请输入证书名称' }]}><Input /></Form.Item></Col>
            <Col span={6}><Form.Item name="level" label="等级大类" rules={[{ required: true, message: '请选择等级大类' }]}><Select options={levelOptions.map(item => ({ label: item.label, value: item.value }))} placeholder="选择国家级/省级/校（市）级/院级" /></Form.Item></Col>
            <Col span={5}><Form.Item name="term" label="所属学期" rules={[{ required: true, message: '请选择学期' }]}><Select options={terms.map(item => ({ label: item.termName, value: item.termId }))} /></Form.Item></Col>
            <Col span={5}><Form.Item name="time" label="落款日期" rules={[{ required: true, message: '请选择日期' }]}><DatePicker style={{ width: '100%' }} /></Form.Item></Col>
          </Row>
          <Row gutter={16} align="middle">
            <Col span={6}>
              <Form.Item name="isTeam" label="证书类型" rules={[{ required: true }]}>
                <Select onChange={onCertificateKindChange} options={[{ label: '个人证书', value: 0 }, { label: '团队证书', value: 1 }]} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label={isEditMode ? '更换 PDF 底图（可选）' : 'PDF 底图'} required={!isEditMode}>
                <Upload
                  action="/api/file/upload"
                  accept=".pdf"
                  maxCount={1}
                  withCredentials
                  data={{ uploaderId: user.userId, uploadType: 'certificate_template' }}
                  onChange={({ file }) => {
                    if (file.status === 'done') {
                      const response = file.response;
                      if (response?.code === 200) setTemplateFileId(Number(response.data));
                      else message.error(response?.msg || 'PDF 上传失败');
                    } else if (file.status === 'error') {
                      message.error(file.response?.msg || 'PDF 上传失败');
                    }
                  }}
                >
                  <Button icon={<FilePdfOutlined />}>{templateFileId ? '重新上传 PDF' : '上传 PDF 模板'}</Button>
                </Upload>
                <div style={{ color: '#8c8c8c', fontSize: 12, marginTop: 6 }}>证书模板 PDF 必须且只能包含 1 页。</div>
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item
                name="ratingEnabled"
                label="参与 Rating 积分"
                valuePropName="checked"
                tooltip="关闭后，该模板下已发放和后续发放的证书均不计入 Rating。"
              >
                <Switch checkedChildren="参与" unCheckedChildren="不参与" />
              </Form.Item>
            </Col>
            <Col span={6} style={{ textAlign: 'right' }}>
              <Space>
                <Button icon={<EyeOutlined />} loading={templatePreviewing} onClick={previewTemplate}>生成示例预览</Button>
                <Button type="primary" htmlType="submit" loading={templateSaving}>保存模板</Button>
              </Space>
            </Col>
          </Row>
          <CertificateTemplateDesigner
            fileId={templateFileId}
            blocks={templateBlocks}
            onChange={setTemplateBlocks}
            qrCode={templateQrCode}
            onQrCodeChange={setTemplateQrCode}
            isTeam={watchedIsTeam}
          />
        </Form>
      </Modal>

      <Modal
        title={`批量发放：${selectedTemplate?.typeName || ''}`}
        open={grantOpen}
        onCancel={() => setGrantOpen(false)}
        footer={null}
        width={1050}
        destroyOnHidden
      >
        {selectedTemplate?.templateSchema ? (
          <>
            <Alert
              type="info"
              showIcon
              message="一行对应一张证书；团队人数可在 1–10 人之间变化"
              description="个人表头：姓名 学号 学校 奖项 编号；团队表头：队伍名称 队员1姓名 队员1学号 队员1学校 …… 奖项 编号。同一证书模板下，同一个人或队伍可以获得多个不同奖项，同一奖项只能有一条记录；个人按“学校 + 学号”识别，队伍按队员身份组合识别，不受队员顺序影响。系统按“学校 + 学号”匹配站内用户，未匹配到账号的获奖人也会以未登录身份保存，注册后自动关联。系统先预检整份工作簿，存在任何错误时不会写入数据库。编号按文本读取并全库唯一。"
              style={{ marginBottom: 16 }}
            />
            <Space style={{ marginBottom: 16 }}>
              <Button icon={<DownloadOutlined />} onClick={() => window.open('/api/certificate/grant/import/template/personal', '_blank')}>下载个人模板</Button>
              <Button icon={<DownloadOutlined />} onClick={() => window.open('/api/certificate/grant/import/template/team', '_blank')}>下载团队模板</Button>
            </Space>
            <Upload.Dragger
              accept=".xlsx,.xls"
              maxCount={1}
              beforeUpload={(file) => { previewImport(file); return false; }}
              onRemove={() => { setImportFile(null); setImportPreview(null); }}
              disabled={importLoading || issueLoading}
            >
              <p className="ant-upload-drag-icon"><FileExcelOutlined /></p>
              <p className="ant-upload-text">点击或拖入 Excel 发放名单</p>
              <p className="ant-upload-hint">建议直接使用对应模板填写；奖项和编号会写入每张证书的签发记录</p>
            </Upload.Dragger>

            {importLoading && <Alert type="info" showIcon message="正在预检工作簿……" style={{ marginTop: 16 }} />}
            {importPreview && (
              <div style={{ marginTop: 18 }}>
                <Row gutter={16} style={{ marginBottom: 16 }}>
                  <Col span={6}><Statistic title="证书数量" value={importPreview.certificateCount} /></Col>
                  <Col span={6}><Statistic title="获奖人数" value={importPreview.recipientCount} /></Col>
                  <Col span={12}>
                    <Alert type={importPreview.valid ? 'success' : 'error'} showIcon message={importPreview.valid ? '预检通过，可以签发' : `发现 ${importPreview.errors.length} 个问题，尚未写入数据库`} />
                  </Col>
                </Row>
                {importPreview.errors.length > 0 && (
                  <List
                    size="small"
                    bordered
                    header="需要修正的问题"
                    dataSource={importPreview.errors}
                    renderItem={item => <List.Item><Tag color="red">第 {item.rowNumber} 行</Tag><b>{item.columnName}</b>：{item.message}</List.Item>}
                    style={{ maxHeight: 220, overflowY: 'auto', marginBottom: 16 }}
                  />
                )}
                {importPreview.rows.length > 0 && (
                  <Table size="small" rowKey="rowNumber" columns={importColumns} dataSource={importPreview.rows} pagination={{ pageSize: 8 }} scroll={{ x: 850 }} />
                )}
                <Popconfirm
                  title={`确认签发 ${importPreview.certificateCount} 张证书吗？`}
                  description="签发后编号立即进入真伪查询库。"
                  disabled={!importPreview.valid}
                  onConfirm={issueImport}
                >
                  <Button type="primary" size="large" block disabled={!importPreview.valid} loading={issueLoading}>确认签发并写入验真库</Button>
                </Popconfirm>
              </div>
            )}
          </>
        ) : (
          <>
            <Alert type="warning" showIcon message="这是旧版 PDF 表单域模板" description="旧模板可以继续按原方式发放和下载，但不会生成可查验编号。建议先编辑模板并配置占位符，再使用 Excel 发放。" style={{ marginBottom: 16 }} />
            <Space wrap>
              <Button icon={<DownloadOutlined />} onClick={() => downloadExcelTemplate({
                fileName: selectedTemplate?.isTeam === 1 ? '旧版团队证书发放模板.xlsx' : '旧版个人证书发放模板.xlsx',
                headers: legacyHeaders,
                examples: selectedTemplate?.isTeam === 1
                  ? [["张三", "20240001", "北京电子科技学院", "李四", "20240002", "北京电子科技学院"]]
                  : [["张三", "20240001", "北京电子科技学院"]],
              })}>下载 Excel 模板</Button>
              <Upload accept=".xlsx,.xls" maxCount={1} beforeUpload={(file) => selectLegacyImportFile(file as File)} onRemove={() => { setLegacyImportRows([]); setLegacyImportPreview(null); }}>
                <Button icon={<FileExcelOutlined />}>选择填写好的 Excel</Button>
              </Upload>
            </Space>
            {legacyImportRows.length > 0 && <div style={{ marginTop: 8, color: '#666' }}>已读取 {legacyImportRows.length} 条数据</div>}
            {legacyImportPreview && <ImportValidationPreview data={legacyImportPreview} />}
            <Button type="primary" block size="large" loading={issueLoading} onClick={grantLegacyTemplate} style={{ marginTop: 16 }}>{legacyImportPreview ? '确认发放可用数据' : '上传并校验'}</Button>
          </>
        )}
      </Modal>

      <Modal title={`发放名单：${selectedTemplate?.typeName || ''}`} open={listManageOpen} onCancel={() => setListManageOpen(false)} footer={null} width={1050} destroyOnHidden>
        <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between' }}>
          <Input placeholder="搜索编号、姓名或学号" prefix={<SearchOutlined />} value={listSearchText} onChange={event => setListSearchText(event.target.value)} allowClear style={{ width: 300 }} />
          <Space>
            {selectedGrantIds.length > 0 && <Popconfirm title={`确定撤回选中的 ${selectedGrantIds.length} 张证书吗？`} onConfirm={batchRevoke}><Button danger icon={<DeleteOutlined />}>批量撤回</Button></Popconfirm>}
            <Button loading={identityLoading} onClick={() => openIdentityReview(selectedTemplate.certificateTypeId)}>历史归属确认</Button>
            <Button icon={<ReloadOutlined />} onClick={() => fetchIssuedList(selectedTemplate.certificateTypeId)}>刷新</Button>
          </Space>
        </div>
        <Table
          dataSource={filteredIssuedList}
          columns={issuedColumns}
          rowKey="certificateDownloadId"
          loading={issuedLoading}
          size="small"
          scroll={{ x: 1100 }}
          rowSelection={{
            selectedRowKeys: selectedGrantIds,
            onChange: setSelectedGrantIds,
            getCheckboxProps: (record: any) => ({ disabled: record.status === 'revoked' }),
          }}
        />
      </Modal>
      <Modal title="历史证书及申请材料归属确认" open={identityReviewOpen} onCancel={() => { if (!identitySaving) setIdentityReviewOpen(false); }} onOk={confirmIdentity} confirmLoading={identitySaving} okButtonProps={{ disabled: identityRows.length === 0 }} cancelButtonProps={{ disabled: identitySaving }} closable={!identitySaving} maskClosable={!identitySaving} okText="已核实材料，确认归属" destroyOnHidden>
        <Alert type="warning" showIcon description={`包含${identityReviewTypeId === undefined ? '全部' : '当前模板的'}官方证书和所有已审核通过的证书申请材料。请先核对原始获奖名单及实名材料；缺失学校或身份不匹配的记录不会自动进入个人证书、荣誉墙和评分。确认操作会记录审计。`} style={{ marginBottom: 16 }} />
        {identityRows.length === 0 && <Alert type="info" description="当前没有需要确认归属的记录。" style={{ marginBottom: 16 }} />}
        <Form form={identityForm} layout="vertical" disabled={identitySaving}>
          <Form.Item name="recordKey" label="待确认的获奖记录" rules={[{ required: true, message: '请选择待确认记录' }]}>
            <Select options={identityRows.map(row => ({ value: row.recordKey, label: `${row.context || row.recordKey} · ${row.realName || '缺少姓名'} · ${row.userAccount || '缺少学号'} · ${row.schoolName || '缺少学校'}` }))} onChange={recordKey => {
              const row = identityRows.find(item => item.recordKey === recordKey);
              identityForm.setFieldsValue({ userId: undefined, school: row?.schoolName || '', numberId: row?.userAccount || '', realName: row?.realName || '' });
            }} />
          </Form.Item>
          <Form.Item name="userId" label="已实名审核通过的用户编号" rules={[{ required: true }]}><InputNumber min={1} precision={0} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="school" label="学校完整名称" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="numberId" label="学号" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="realName" label="实名姓名" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default CertificateGrantManager;
