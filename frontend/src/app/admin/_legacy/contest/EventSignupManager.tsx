"use client";

import React, { useEffect, useState } from 'react';
import { Table, Card, Button, Tag, Space, Modal, Input, message, Statistic, Breadcrumb, Row, Col, Alert, Select, Radio, Popconfirm, Typography, Tooltip, Upload } from 'antd';
import { ArrowLeftOutlined, ReloadOutlined, SearchOutlined, FileExcelOutlined, UploadOutlined, CheckCircleOutlined, StopOutlined, ImportOutlined, DownloadOutlined } from '@ant-design/icons';
import { useParams, useNavigate } from '@/app/admin/_legacy/router';
import axios from 'axios';
import dayjs from 'dayjs';
import ImportPreview, { ImportPreviewData } from '../components/ImportPreview';
import { downloadExcelTemplate, readExcelRows, rowsToTsv } from '@/utils/excelImport';

const { TextArea } = Input;
const { Option } = Select;
const { Text } = Typography;

const EventSignupManager: React.FC = () => {
    const { eventId } = useParams();
    const navigate = useNavigate();
    
    const [list, setList] = useState<any[]>([]);
    const [loading, setLoading] = useState(false);
    const [eventInfo, setEventInfo] = useState<any>({});
    const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
    const [keyword, setKeyword] = useState(''); 
    const [statusFilter, setStatusFilter] = useState('all'); 
    const [teamTypeFilter, setTeamTypeFilter] = useState('all');
    const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
    const [viewType, setViewType] = useState('summary'); 
    const [isImportOpen, setIsImportOpen] = useState(false);
    const [importRows, setImportRows] = useState<string[][]>([]);
    const [importPreview, setImportPreview] = useState<ImportPreviewData | null>(null);
    const [isCredImportOpen, setIsCredImportOpen] = useState(false);
    const [credImportRows, setCredImportRows] = useState<string[][]>([]);
    const [credImportPreview, setCredImportPreview] = useState<ImportPreviewData | null>(null);
    const [isRuleModalOpen, setIsRuleModalOpen] = useState(false);
    const [admissionTicketRules, setAdmissionTicketRules] = useState('');
    const [ruleSaving, setRuleSaving] = useState(false);

    const [rejectReason, setRejectReason] = useState('');
    const [isRejectModalOpen, setIsRejectModalOpen] = useState(false);
    const [rejectTarget, setRejectTarget] = useState<{ids: any[]} | null>(null);

    useEffect(() => { if(eventId) fetchEventInfo(); }, [eventId]);

    // 监听筛选条件变化，自动刷新
    useEffect(() => { 
        if (eventInfo.eventId) fetchSignups(1, pagination.pageSize); 
    }, [eventInfo.eventId, viewType, statusFilter, teamTypeFilter]);

    // 监听搜索关键词，防抖刷新
    useEffect(() => {
        if (!eventInfo.eventId) return;
        const timer = setTimeout(() => {
            fetchSignups(1, pagination.pageSize);
        }, 500);
        return () => clearTimeout(timer);
    }, [keyword]);

    const fetchEventInfo = async () => {
        try {
            const res = await axios.get(`/api/event/detail/${eventId}`);
            if (res.data.code === 200) setEventInfo(res.data.data);
        } catch (e) { message.error('获取赛事信息失败'); }
    };

    const fetchSignups = async (current: number, size: number) => {
        setLoading(true);
        try {
            const res = await axios.get(`/api/event/admin/signups`, {
                params: { eventId, current, size, keyword, status: statusFilter, teamType: teamTypeFilter, viewType }
            });
            if (res.data.code === 200) {
                setList(res.data.data.records);
                setPagination({
                    current: res.data.data.current,
                    pageSize: res.data.data.size,
                    total: res.data.data.total
                });
                setSelectedRowKeys([]);
            }
        } finally { setLoading(false); }
    };

    const handleBatchUpdate = async (status: string, ids?: any[]) => {
        const targetIds = ids || selectedRowKeys;
        if (targetIds.length === 0) return message.warning('请先选择记录');

        if (status === 'rejected') {
            setRejectTarget({ ids: targetIds });
            setRejectReason('');
            setIsRejectModalOpen(true);
            return;
        }

        try {
            const res = await axios.post('/api/event/admin/batch-update-status', { eventId, signupIds: targetIds, status });
            if (res.data.code !== 200) return message.error(res.data.msg || '操作失败');
            message.success('操作成功');
            fetchSignups(pagination.current, pagination.pageSize);
            fetchEventInfo();
            if(!ids) setSelectedRowKeys([]);
        } catch (e) { message.error('操作失败'); }
    };

    const handleConfirmReject = async () => {
        if (!rejectTarget) return;
        try {
            const res = await axios.post('/api/event/admin/batch-update-status', {
                eventId, 
                signupIds: rejectTarget.ids, 
                status: 'rejected',
                reason: rejectReason
            });
            if (res.data.code !== 200) return message.error(res.data.msg || '操作失败');
            message.success('操作成功');
            fetchSignups(pagination.current, pagination.pageSize);
            fetchEventInfo();
            setSelectedRowKeys([]);
            setIsRejectModalOpen(false);
        } catch (e) { message.error('操作失败'); }
    };

    const [importLoading, setImportLoading] = useState(false);
    const handleImport = async () => {
        if(!importRows.length) return message.warning('请先上传填写好的 Excel 文件');
        setImportLoading(true);
        try {
            const confirming = Boolean(importPreview);
            const res = await axios.post('/api/event/admin/batch-import', { eventId, content: rowsToTsv(importRows), preview: !confirming });
            if(res.data.code === 200) {
                if (!confirming) {
                    setImportPreview(res.data.data);
                    return;
                }
                message.success(`成功:${res.data.data.success}, 失败:${res.data.data.failure}`);
                setIsImportOpen(false);
                setImportRows([]);
                setImportPreview(null);
                fetchSignups(1, 10);
            } else { message.error(res.data.msg); }
        } catch (e) { message.error('导入请求失败'); } finally { setImportLoading(false); }
    };

    const handleImportCredentials = async () => {
        if(!credImportRows.length) return message.warning('请先上传填写好的 Excel 文件');
        setImportLoading(true);
        try {
            const confirming = Boolean(credImportPreview);
            const res = await axios.post('/api/event/admin/import-credentials', { eventId, content: rowsToTsv(credImportRows), preview: !confirming });
            if(res.data.code === 200) {
                if (!confirming) {
                    setCredImportPreview(res.data.data);
                    return;
                }
                message.success(`导入完成：成功 ${res.data.data.success} 条，失败 ${res.data.data.failure} 条`);
                setIsCredImportOpen(false);
                setCredImportRows([]);
                setCredImportPreview(null);
                fetchSignups(pagination.current, pagination.pageSize); 
            } else { message.error(res.data.msg); }
        } catch (e) { message.error('请求失败'); } finally { setImportLoading(false); }
    };

    const signupGroups = eventInfo.type === 'GPLT'
        ? ['星芒初绽', '长风破浪', '凌霄问鼎']
        : eventInfo.type === 'LANQIAO'
            ? ['C/C++程序设计', 'Python程序设计', 'Java软件开发']
            : ['正式参赛', '女生队伍', '打星参赛'];
    const signupHeaders = eventInfo.isTeam === 1
        ? ['队伍名称', '队伍类型', '是否参与排名', '指导老师']
        : ['学校', '学号', '姓名', '参赛组别', '是否参与排名', '指导老师'];
    const signupTemplateHeaders = signupHeaders.map(header =>
        header === '队伍类型' || header === '参赛组别'
            ? `${header}（${signupGroups.join('、')}）`
            : header === '是否参与排名' ? '是否参与排名（是/否）' : header);
    const credentialHeaders = eventInfo.isTeam === 1
        ? ['队伍名称', '学校', '成员学号', '账号', '密码', '座位号']
        : ['学校', '学号', '账号', '密码', '座位号'];

    const selectExcel = async (file: File, kind: 'signup' | 'credential') => {
        try {
            const rows = await readExcelRows(file, kind === 'signup' ? signupHeaders : credentialHeaders, kind === 'signup' ? signupTemplateHeaders : undefined);
            if (!rows.length) {
                message.warning('Excel 中没有待导入数据');
                return false;
            }
            if (kind === 'signup') {
                setImportRows(rows);
                setImportPreview(null);
            } else {
                setCredImportRows(rows);
                setCredImportPreview(null);
            }
            message.success(`已读取 ${rows.length} 条数据，请上传校验`);
        } catch (error) {
            message.error(error instanceof Error ? error.message : 'Excel 读取失败');
        }
        return false;
    };

    const closeSignupImport = () => { setIsImportOpen(false); setImportRows([]); setImportPreview(null); };
    const closeCredentialImport = () => { setIsCredImportOpen(false); setCredImportRows([]); setCredImportPreview(null); };

    const handleToggleAdmissionTicketOpen = async () => {
        const nextOpen = !eventInfo.admissionTicketOpen;
        try {
            const res = await axios.post('/api/event/admin/admission-ticket-open', {
                eventId,
                open: nextOpen
            });
            if (res.data.code === 200) {
                message.success(res.data.data || (nextOpen ? '已开放参赛证明下载' : '已关闭参赛证明下载'));
                setEventInfo((prev: any) => ({ ...prev, admissionTicketOpen: nextOpen }));
                fetchEventInfo();
            } else {
                message.error(res.data.msg || '操作失败');
            }
        } catch (e) {
            message.error('操作失败');
        }
    };

    const openRuleModal = () => {
        setAdmissionTicketRules(eventInfo.admissionTicketRules || '');
        setIsRuleModalOpen(true);
    };

    const handleSaveAdmissionTicketRules = async () => {
        setRuleSaving(true);
        try {
            const res = await axios.post('/api/event/admin/admission-ticket-rules', {
                eventId,
                rules: admissionTicketRules
            });
            if (res.data.code === 200) {
                message.success(res.data.data || '保存成功');
                setIsRuleModalOpen(false);
                setEventInfo((prev: any) => ({ ...prev, admissionTicketRules }));
                fetchEventInfo();
            } else {
                message.error(res.data.msg || '保存失败');
            }
        } catch (e) {
            message.error('保存失败');
        } finally {
            setRuleSaving(false);
        }
    };

    const [exportLoading, setExportLoading] = useState(false);
    const handleExport = () => {
        setExportLoading(true);
        axios({
            url: `/api/event/admin/export?eventId=${eventId}&viewType=${viewType}&keyword=${keyword || ''}&status=${statusFilter === 'all' ? '' : statusFilter}&teamType=${teamTypeFilter === 'all' ? '' : teamTypeFilter}`,
            method: 'GET',
            responseType: 'blob',
        }).then((response) => {
            const url = window.URL.createObjectURL(new Blob([response.data]));
            const link = document.createElement('a');
            link.href = url;
            link.setAttribute('download', `${eventInfo.name}_${viewType}_名单.xlsx`);
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            message.success('导出成功');
        }).catch(() => message.error('导出失败')).finally(() => setExportLoading(false));
    };

    const columns: any[] = [
        { title: '学院', dataIndex: 'schoolName', width: 140, fixed: 'left' },
    ];

    if (eventInfo.isTeam === 1) {
        columns.push({ title: '队伍名称', dataIndex: 'teamName', width: 150, fixed: 'left' });
        
        columns.push({
            title: '队伍类型',
            dataIndex: 'teamType',
            width: 120,
            render: (t: string) => {
                // XCPC团队赛若为空默认为正式队伍
                let type = t;
                if (!type && !['LANQIAO', 'GPLT'].includes(eventInfo.type)) {
                    type = 'official';
                }

                if(type === 'star') return <Tag color="orange">打星队伍</Tag>;
                if(type === 'official') return <Tag color="blue">正式队伍</Tag>;
                if(type === 'girl') return <Tag color="magenta">女生队伍</Tag>;
                if (['星芒初绽', '长风破浪', '凌霄问鼎'].includes(type)) {
                    return <Tag color="purple">{type}</Tag>;
                }
                return <Tag>{type || '-'}</Tag>;
            }
        });

        if (viewType === 'detail') {
            columns.push(
                { title: '姓名', dataIndex: 'realName', width: 100 },
                { title: '性别', dataIndex: 'gender', width: 60 },
                { title: '学号', dataIndex: 'numberId', width: 120 },
                { title: '专业', dataIndex: 'majorName', width: 140 },
                { title: '年级', dataIndex: 'gradeYear', width: 80 }, // 🔴 新增
                { title: '班级', dataIndex: 'className', width: 120 }
            );
        } else {
            columns.push(
                { title: '队伍成员', dataIndex: 'membersContent', render: (t:string) => <div style={{whiteSpace:'pre-wrap', fontSize: 12}}>{t}</div>, width: 200 }
            );
        }
    } else {
        columns.push(
            { title: '姓名', dataIndex: 'realName', width: 100, fixed: 'left' },
            { title: '性别', dataIndex: 'gender', width: 60 },
            { title: '学号', dataIndex: 'numberId', width: 120 },
            { title: '专业', dataIndex: 'majorName', width: 140 },
            { title: '年级', dataIndex: 'gradeYear', width: 80 }, // 🔴 新增
            { title: '班级', dataIndex: 'className', width: 120 }
        );
        
        columns.push({
            title: '参赛组别',
            dataIndex: 'teamType',
            width: 130,
            render: (t: string) => {
                // XCPC个人赛若为空默认为正式参赛
                let type = t;
                if (!type && !['LANQIAO', 'GPLT'].includes(eventInfo.type)) {
                    type = 'official';
                }
                
                if (!type) return <span style={{color:'#ccc'}}>-</span>;
                if(type === 'star') return <Tag color="orange">打星参赛</Tag>;
                if(type === 'official') return <Tag color="blue">正式参赛</Tag>;
                if(type === 'girl') return <Tag color="magenta">女生队伍</Tag>;
                if (['C/C++程序设计', 'Python程序设计', 'Java软件开发'].includes(type)) {
                    return <Tag color="geekblue">{type}</Tag>;
                }
                return <Tag>{type}</Tag>;
            }
        });
    }

    columns.push(
        { title: '指导老师', dataIndex: 'advisor', width: 100 },
        { 
            title: '参与排名', 
            dataIndex: 'isRanking', 
            width: 90, 
            render: (v: number) => v === 0 ? <Tag>否</Tag> : <Tag color="green">是</Tag> 
        },
        { 
            title: '比赛账号', 
            dataIndex: 'contestAccount', 
            width: 140,
            render: (t:any) => t ? <Text copyable style={{fontSize: 13}}>{t}</Text> : <span style={{color:'#ccc'}}>-</span> 
        },
        { 
            title: '密码', 
            dataIndex: 'contestPassword', 
            width: 100,
            render: (t:any) => t ? <Tooltip title={t}><Text copyable={{text:t}} style={{fontSize: 13}}>******</Text></Tooltip> : <span style={{color:'#ccc'}}>-</span> 
        },
        { 
            title: '座位', 
            dataIndex: 'seatNumber', 
            width: 80 
        },
        { title: '报名时间', dataIndex: 'signupTime', render: (t:string) => dayjs(t).format('MM-DD HH:mm'), width: 120 },
        { 
            title: '状态', 
            dataIndex: 'status', 
            width: 90,
            fixed: 'right',
            render: (s:string) => {
                const map:any = { approved: {c:'green',t:'已报名'}, rejected: {c:'red',t:'已拒绝'}, pending: {c:'gold',t:'待审核'}, cancelled: {c:'default',t:'已取消'} };
                return <Tag color={map[s]?.c}>{map[s]?.t}</Tag>;
            } 
        },
        {
            title: '操作',
            key: 'action',
            fixed: 'right',
            width: 140,
            render: (_: any, record: any) => (
                <Space size="small">
                    {record.status !== 'approved' && (
                        <Popconfirm title="通过？" onConfirm={() => handleBatchUpdate('approved', [record.signupId])}>
                            <Button type="link" size="small" icon={<CheckCircleOutlined />} style={{color: '#52c41a'}}>通过</Button>
                        </Popconfirm>
                    )}
                    {record.status !== 'rejected' && (
                        <Button type="link" size="small" danger icon={<StopOutlined />} onClick={() => handleBatchUpdate('rejected', [record.signupId])}>拒绝</Button>
                    )}
                </Space>
            )
        }
    );

    return (
        <div>
            <Breadcrumb items={[{ title: '后台' }, { title: '赛事管理', href: '/admin/contest' }, { title: '报名名单' }]} style={{ marginBottom: 16 }} />
            <Card>
                <div style={{ marginBottom: 16, display:'flex', justifyContent:'space-between' }}>
                    <Space>
                        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/admin/contest')}>返回</Button>
                        <h3 style={{margin:0}}>{eventInfo.name}</h3>
                    </Space>
                    <Space>
                        <Button onClick={() => {fetchEventInfo(); fetchSignups(1,10)}} icon={<ReloadOutlined />}>刷新</Button>
                        
                        <Button icon={<ImportOutlined />} onClick={() => setIsCredImportOpen(true)}>导入参赛证明信息</Button>
                        <Button onClick={openRuleModal}>参赛选手须知</Button>
                        <Popconfirm
                            title={eventInfo.admissionTicketOpen ? '确认关闭参赛证明下载？' : '确认开放参赛证明下载？'}
                            description={eventInfo.admissionTicketOpen ? '关闭后，参赛用户将不能继续下载参赛证明。' : '开放后，已通过且已导入考室信息的用户可下载参赛证明 PDF。'}
                            onConfirm={handleToggleAdmissionTicketOpen}
                        >
                            <Button type={eventInfo.admissionTicketOpen ? 'default' : 'primary'} danger={eventInfo.admissionTicketOpen}>
                                {eventInfo.admissionTicketOpen ? '关闭参赛证明下载' : '开放参赛证明下载'}
                            </Button>
                        </Popconfirm>
                        
                        <Button icon={<FileExcelOutlined />} onClick={handleExport} loading={exportLoading}>导出Excel</Button>
                        <Button icon={<UploadOutlined />} onClick={() => setIsImportOpen(true)}>批量报名</Button>
                    </Space>
                </div>
                
                <Row gutter={16} style={{marginBottom: 16, background: '#f6ffed', padding: '12px', border: '1px solid #b7eb8f', borderRadius: 6}}>
                    <Col span={6}><Statistic title="最大名额" value={eventInfo.maxSignup} suffix={eventInfo.isTeam?"队":"人"} /></Col>
                    <Col span={6}><Statistic title="已报名" value={eventInfo.signupCount} valueStyle={{ color: '#3f8600' }} suffix={eventInfo.isTeam?"队":"人"} /></Col>
                    <Col span={6}><Statistic title="剩余名额" value={Math.max(0, (eventInfo.maxSignup||0) - (eventInfo.signupCount||0))} /></Col>
                    <Col span={6}><Statistic title="参赛证明下载" value={eventInfo.admissionTicketOpen ? '已开放' : '未开放'} valueStyle={{ color: eventInfo.admissionTicketOpen ? '#3f8600' : '#999' }} /></Col>
                </Row>

                <div style={{ marginBottom: 16, display:'flex', justifyContent:'space-between', alignItems:'center' }}>
                    <Space>
                        <Select value={statusFilter} onChange={val => setStatusFilter(val)} style={{width: 120}}>
                            <Option value="all">全部状态</Option>
                            <Option value="approved">已报名</Option>
                            <Option value="pending">待审核</Option>
                            <Option value="rejected">已拒绝</Option>
                            <Option value="cancelled">已取消</Option>
                        </Select>
                        <Select value={teamTypeFilter} onChange={val => setTeamTypeFilter(val)} style={{width: 140}}>
                            <Option value="all">全部组别</Option>
                            {eventInfo.type === 'GPLT' ? (
                                <>
                                    <Option value="星芒初绽">星芒初绽</Option>
                                    <Option value="长风破浪">长风破浪</Option>
                                    <Option value="凌霄问鼎">凌霄问鼎</Option>
                                </>
                            ) : eventInfo.type === 'LANQIAO' ? (
                                <>
                                    <Option value="C/C++程序设计">C/C++</Option>
                                    <Option value="Python程序设计">Python</Option>
                                    <Option value="Java软件开发">Java</Option>
                                </>
                            ) : (
                                <>
                                    <Option value="official">正式参赛</Option>
                                    <Option value="girl">女生队伍</Option>
                                    <Option value="star">打星参赛</Option>
                                </>
                            )}
                        </Select>
                        <Input placeholder="搜索姓名/学号/队名" value={keyword} onChange={e=>setKeyword(e.target.value)} style={{width: 200}} />
                        <Button type="primary" onClick={()=>{}} icon={<SearchOutlined />}>搜索</Button>
                    </Space>

                    <Space>
                        {eventInfo.isTeam === 1 && (
                            <Radio.Group value={viewType} onChange={e => setViewType(e.target.value)} buttonStyle="solid">
                                <Radio.Button value="summary">按队伍</Radio.Button>
                                <Radio.Button value="detail">按成员</Radio.Button>
                            </Radio.Group>
                        )}
                        <Button type="primary" ghost disabled={selectedRowKeys.length === 0} onClick={() => handleBatchUpdate('approved')}>批量通过</Button>
                        
                        <Button danger disabled={selectedRowKeys.length === 0} onClick={() => handleBatchUpdate('rejected')}>批量拒绝</Button>
                    </Space>
                </div>

                <Table 
                    rowKey={(record) => (viewType === 'detail' && record.numberId) ? `${record.signupId}_${record.numberId}` : record.signupId}
                    columns={columns}  
                    dataSource={list} 
                    loading={loading}
                    pagination={{
                        ...pagination, 
                        showSizeChanger: true, 
                        showQuickJumper: true,
                        showTotal: (total) => `共 ${total} 条`,
                        onChange: (page, size) => fetchSignups(page, size)
                    }}
                    rowSelection={{ selectedRowKeys, onChange: setSelectedRowKeys }}
                    scroll={{ x: 1600 }}
                />
            </Card>

            <Modal title="批量导入报名" open={isImportOpen} onCancel={closeSignupImport} onOk={handleImport} okText={importPreview ? '确认导入可用数据' : '上传并校验'} confirmLoading={importLoading} width={760}>
                <Alert 
                    message="导入格式说明"
                    description={
                        <div style={{fontSize: 12}}>
                            <p><strong>个人赛：</strong>学校 &emsp; 学号 &emsp; 姓名 &emsp; [参赛组别] &emsp; [是否参与排名(是/否)] &emsp; [指导老师]</p>
                            <p><strong>团队赛：</strong>队伍名称 &emsp; [队伍类型] &emsp; [是否参与排名(是/否)] &emsp; [指导老师]</p>
                            <p><strong>当前赛制组别：</strong>{signupGroups.join('、')}。{['GPLT', 'LANQIAO'].includes(eventInfo.type) ? '组别必填。' : '留空默认正式参赛；正式参赛的全女生队伍（或女生个人）自动归为女生队伍。'}</p>
                            <p style={{color: '#999'}}>* 个人赛学校请填写实名认证中的学校（大学）全称；团队赛需保证队伍已存在。请下载对应赛事模板填写后上传。</p>
                        </div>
                    }
                    type="info" 
                    showIcon 
                    style={{marginBottom:10}} 
                />
                <Space wrap>
                    <Button icon={<DownloadOutlined />} onClick={() => downloadExcelTemplate({
                        fileName: eventInfo.isTeam === 1 ? '团队赛报名导入模板.xlsx' : '个人赛报名导入模板.xlsx',
                        headers: signupTemplateHeaders,
                        examples: eventInfo.isTeam === 1 ? [['示例队伍', signupGroups[0], '是', '李老师']] : [['北京电子科技学院', '20230001', '张三', signupGroups[0], '是', '王老师']],
                    })}>下载 Excel 模板</Button>
                    <Upload accept=".xlsx,.xls" maxCount={1} beforeUpload={(file) => selectExcel(file as File, 'signup')} onRemove={() => { setImportRows([]); setImportPreview(null); }}>
                        <Button icon={<UploadOutlined />}>选择填写好的 Excel</Button>
                    </Upload>
                </Space>
                {importRows.length > 0 && <div style={{ marginTop: 8, color: '#666' }}>已读取 {importRows.length} 条数据</div>}
                {importPreview && <ImportPreview data={importPreview} />}
            </Modal>

            <Modal
                title="导入参赛证明信息"
                open={isCredImportOpen} 
                onCancel={closeCredentialImport}
                onOk={handleImportCredentials}
                okText={credImportPreview ? '确认导入可用数据' : '上传并校验'}
                confirmLoading={importLoading}
                width={760}
            >
                <Alert 
                    message="数据格式说明"
                    description={
                        <div style={{fontSize: 12}}>
                            <p><strong>个人赛：</strong>学校 &emsp; 学号 &emsp; 账号 &emsp; 密码 &emsp; 座位号</p>
                            <p><strong>团队赛 (共享账号)：</strong>队伍名称，学校和成员学号留空，再填写账号、密码、座位号</p>
                            <p><strong>团队赛 (一人一号)：</strong>队伍名称 &emsp; 学校 &emsp; 成员学号 &emsp; 账号 &emsp; 密码 &emsp; 座位号</p>
                            <p style={{color: '#999'}}>* 学校需与用户实名认证中的学校（大学）全称一致。请下载模板填写后上传。</p>
                        </div>
                    }
                    type="info" 
                    showIcon 
                    style={{marginBottom:16}} 
                />
                <Space wrap>
                    <Button icon={<DownloadOutlined />} onClick={() => downloadExcelTemplate({
                        fileName: eventInfo.isTeam === 1 ? '团队赛参赛凭证导入模板.xlsx' : '个人赛参赛凭证导入模板.xlsx',
                        headers: credentialHeaders,
                        examples: eventInfo.isTeam === 1 ? [['示例队伍', '北京电子科技学院', '20230001', 'contest01', 'password', 'A01']] : [['北京电子科技学院', '20230001', 'contest01', 'password', 'A01']],
                    })}>下载 Excel 模板</Button>
                    <Upload accept=".xlsx,.xls" maxCount={1} beforeUpload={(file) => selectExcel(file as File, 'credential')} onRemove={() => { setCredImportRows([]); setCredImportPreview(null); }}>
                        <Button icon={<UploadOutlined />}>选择填写好的 Excel</Button>
                    </Upload>
                </Space>
                {credImportRows.length > 0 && <div style={{ marginTop: 8, color: '#666' }}>已读取 {credImportRows.length} 条数据</div>}
                {credImportPreview && <ImportPreview data={credImportPreview} />}
            </Modal>

            <Modal
                title="参赛选手须知设置"
                open={isRuleModalOpen}
                onCancel={() => setIsRuleModalOpen(false)}
                onOk={handleSaveAdmissionTicketRules}
                confirmLoading={ruleSaving}
                okText="保存"
                width={700}
            >
                <Alert
                    message="参赛选手须知"
                    description="这里填写的内容会显示在参赛证明表格下方，支持多行文本；留空时使用系统默认须知。"
                    type="info"
                    showIcon
                    style={{ marginBottom: 12 }}
                />
                <TextArea
                    rows={8}
                    value={admissionTicketRules}
                    onChange={e => setAdmissionTicketRules(e.target.value)}
                    placeholder="请输入参赛选手须知，例如：比赛期间不得切屏；迟到 15 分钟不得入场；请携带学生证或身份证。"
                />
            </Modal>

            <Modal
                title="拒绝报名"
                open={isRejectModalOpen}
                onCancel={() => setIsRejectModalOpen(false)}
                onOk={handleConfirmReject}
                okText="确认拒绝"
                okType="danger"
            >
                <div style={{ marginBottom: 12, color: '#666' }}>
                    将拒绝 {rejectTarget?.ids?.length || 0} 个报名记录。
                    请填写拒绝原因，系统将通过邮件通知用户。
                </div>
                <TextArea
                    rows={4}
                    value={rejectReason}
                    onChange={e => setRejectReason(e.target.value)}
                    placeholder="请输入拒绝原因（必填）"
                />
            </Modal>
        </div>
    );
};
export default EventSignupManager;
