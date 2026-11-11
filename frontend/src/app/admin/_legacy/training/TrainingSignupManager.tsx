"use client";

import React, { useEffect, useState } from 'react';
import { Table, Card, Button, Tag, Space, Modal, Input, message, Statistic, Breadcrumb, Tooltip, Row, Col, Select, Upload } from 'antd';
import { UploadOutlined, CheckCircleOutlined, StopOutlined, ArrowLeftOutlined, ReloadOutlined, SearchOutlined, DownloadOutlined, FileExcelOutlined } from '@ant-design/icons';
import { useParams, useNavigate } from '@/app/admin/_legacy/router';
import axios from 'axios';
import dayjs from 'dayjs';
import ImportPreview, { ImportPreviewData } from '../components/ImportPreview';
import { downloadExcelTemplate, readExcelRows, rowsToTsv } from '@/utils/excelImport';

const IMPORT_HEADERS = ['学校', '学号', '姓名'];

const { TextArea } = Input;
const { Option } = Select;

const TrainingSignupManager: React.FC = () => {
    const { trainId } = useParams();
    const navigate = useNavigate();
    
    const [list, setList] = useState<any[]>([]);
    const [loading, setLoading] = useState(false);
    const [trainingInfo, setTrainingInfo] = useState<any>({});
    
    const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
    const [keyword, setKeyword] = useState(''); 
    const [statusFilter, setStatusFilter] = useState('all'); 
    const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);

    const [isImportModalOpen, setIsImportModalOpen] = useState(false);
    const [importRows, setImportRows] = useState<string[][]>([]);
    const [importLoading, setImportLoading] = useState(false);
    
    const [importResult, setImportResult] = useState<ImportPreviewData | null>(null);

    useEffect(() => {
        fetchTrainingInfo();
        fetchSignups(1, 10);
    }, [trainId]);

    const fetchTrainingInfo = async () => {
        const res = await axios.get(`/api/training/detail/${trainId}`);
        if (res.data.code === 200) setTrainingInfo(res.data.data || {});
    };

    const fetchSignups = async (current: number, size: number, overrideStatus?: string) => {
        setLoading(true);
        try {
            const res = await axios.get(`/api/training/admin/signups`, {
                params: { trainId, current, size, keyword, status: overrideStatus ?? statusFilter }
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
        } finally {
            setLoading(false);
        }
    };

    const handleTableChange = (page: any) => fetchSignups(page.current, page.pageSize);

    const onSearch = () => {
        setPagination(prev => ({ ...prev, current: 1 }));
        fetchSignups(1, pagination.pageSize);
    };

    const [rejectReason, setRejectReason] = useState('');
    const [isRejectModalOpen, setIsRejectModalOpen] = useState(false);
    const [rejectTarget, setRejectTarget] = useState<{type: 'single' | 'batch', id?: number} | null>(null);

    const handleBatchUpdate = async (status: string) => {
        if (selectedRowKeys.length === 0) return message.warning('请先选择用户');
        
        if (status === 'rejected') {
            setRejectTarget({ type: 'batch' });
            setRejectReason('');
            setIsRejectModalOpen(true);
            return;
        }

        Modal.confirm({
            title: `确定要批量设为 ${status === 'approved' ? '通过' : '拒绝'} 吗？`,
            content: `共选中 ${selectedRowKeys.length} 人`,
            onOk: async () => {
                try {
                    const res = await axios.post('/api/training/admin/batch-update-status', {
                        signupIds: selectedRowKeys,
                        status
                    });
                    if (res.data.code === 200) {
                        message.success('批量操作成功');
                        fetchSignups(pagination.current, pagination.pageSize);
                        fetchTrainingInfo(); 
                        setSelectedRowKeys([]);
                    }
                } catch(e) { message.error('批量操作失败'); }
            }
        });
    };

    const updateStatus = async (signupId: number, status: string) => {
        if (status === 'rejected') {
            setRejectTarget({ type: 'single', id: signupId });
            setRejectReason('');
            setIsRejectModalOpen(true);
            return;
        }

        try {
            const res = await axios.post('/api/training/admin/update-status', { signupId, status });
            if (res.data.code === 200) {
                message.success('状态已更新');
                fetchSignups(pagination.current, pagination.pageSize);
                fetchTrainingInfo();
            } else {
                message.error(res.data.msg);
            }
        } catch (e) { message.error('操作失败'); }
    };

    const handleConfirmReject = async () => {
        if (!rejectTarget) return;
        
        try {
            if (rejectTarget.type === 'single') {
                const res = await axios.post('/api/training/admin/update-status', { 
                    signupId: rejectTarget.id, 
                    status: 'rejected',
                    reason: rejectReason
                });
                if (res.data.code === 200) {
                    message.success('已拒绝报名');
                    fetchSignups(pagination.current, pagination.pageSize);
                    fetchTrainingInfo();
                } else {
                    message.error(res.data.msg);
                }
            } else {
                const res = await axios.post('/api/training/admin/batch-update-status', {
                    signupIds: selectedRowKeys,
                    status: 'rejected',
                    reason: rejectReason
                });
                if (res.data.code === 200) {
                    message.success('批量拒绝成功');
                    fetchSignups(pagination.current, pagination.pageSize);
                    fetchTrainingInfo();
                    setSelectedRowKeys([]);
                } else {
                    message.error(res.data.msg);
                }
            }
            setIsRejectModalOpen(false);
        } catch (e) {
            message.error('操作失败');
        }
    };

    const handleBatchImport = async () => {
        if (!importRows.length) return message.warning('请先上传填写好的 Excel 文件');
        setImportLoading(true);
        try {
            const confirming = Boolean(importResult);
            const res = await axios.post('/api/training/admin/batch-import', {
                trainId,
                content: rowsToTsv(importRows),
                preview: !confirming,
            });
            if (res.data.code === 200) {
                const result = res.data.data;
                if (!confirming) {
                    setImportResult(result);
                    return;
                }
                message.success(`导入完成：成功 ${result.success} 条，失败 ${result.failure} 条`);
                setIsImportModalOpen(false);
                setImportRows([]);
                setImportResult(null);
                fetchSignups(1, 10);
                fetchTrainingInfo();
            } else {
                message.error(res.data.msg);
            }
        } catch (e) { message.error('导入请求失败'); } 
        finally { setImportLoading(false); }
    };

    const selectImportFile = async (file: File) => {
        try {
            const rows = await readExcelRows(file, IMPORT_HEADERS);
            if (!rows.length) {
                message.warning('Excel 中没有待导入数据');
                return false;
            }
            setImportRows(rows);
            setImportResult(null);
            message.success(`已读取 ${rows.length} 条数据，请上传校验`);
        } catch (error) {
            message.error(error instanceof Error ? error.message : 'Excel 读取失败');
        }
        return false;
    };

    const closeImport = () => {
        setIsImportModalOpen(false);
        setImportRows([]);
        setImportResult(null);
    };

    const [exportLoading, setExportLoading] = useState(false);

    const handleExport = () => {
        setExportLoading(true);
        axios({
            url: `/api/training/admin/export?trainId=${trainId}`,
            method: 'GET',
            responseType: 'blob',
        }).then((response) => {
            const url = window.URL.createObjectURL(new Blob([response.data]));
            const link = document.createElement('a');
            link.href = url;
            link.setAttribute('download', `${trainingInfo.name || '培训'}_报名名单.xlsx`); 
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            message.success('导出成功');
        }).catch(() => message.error('导出失败')).finally(() => setExportLoading(false));
    };

    const columns = [
        { title: '学校', dataIndex: 'universityName', key: 'uni', width: 150, ellipsis: true },
        { title: '姓名', dataIndex: 'realName', key: 'name', width: 100 },
        { title: '学号/工号', dataIndex: 'numberId', key: 'number', width: 140 },
        { title: '学院', dataIndex: 'schoolName', key: 'school', width: 160, ellipsis: true },
        { title: '专业', dataIndex: 'majorName', key: 'major', width: 160, ellipsis: true },
        { title: '行政班级', dataIndex: 'className', key: 'class', width: 130 },
        { 
            title: '报名时间', 
            dataIndex: 'createTime', 
            key: 'time', 
            width: 160,
            render: (text: string) => dayjs(text).format('YYYY-MM-DD HH:mm')
        },
        { 
            title: '状态', 
            dataIndex: 'status', 
            key: 'status',
            width: 100,
            render: (status: string) => {
                const map: any = { 
                    approved: {color:'green', text:'已报名'}, 
                    rejected: {color:'red', text:'已拒绝'}, 
                    cancelled: {color:'default', text:'已取消'}, 
                    pending: {color:'gold', text:'待审核'} 
                };
                const item = map[status] || {color:'default', text:status};
                return <Tag color={item.color}>{item.text}</Tag>;
            }
        },
        {
            title: '操作',
            key: 'action',
            fixed: 'right' as 'right',
            width: 120,
            render: (_: any, record: any) => (
                <Space size={2}>
                    {record.status !== 'approved' && record.status !== 'rejected' && (
                        <>
                            <Tooltip title="通过报名">
                                <Button type="primary" size="small" icon={<CheckCircleOutlined />} onClick={() => updateStatus(record.signupId, 'approved')} style={{ backgroundColor: '#52c41a', borderColor: '#52c41a' }} />
                            </Tooltip>
                            <Tooltip title="拒绝报名">
                                <Button type="primary" danger size="small" icon={<StopOutlined />} onClick={() => updateStatus(record.signupId, 'rejected')} />
                            </Tooltip>
                        </>
                    )}
                    {record.status === 'approved' && (
                        <Tooltip title="拒绝报名">
                             <Button danger size="small" icon={<StopOutlined />} onClick={() => updateStatus(record.signupId, 'rejected')}>拒绝</Button>
                        </Tooltip>
                    )}
                     {record.status === 'rejected' && (
                        <Tooltip title="恢复报名">
                             <Button size="small" icon={<ReloadOutlined />} onClick={() => updateStatus(record.signupId, 'approved')}>恢复</Button>
                        </Tooltip>
                    )}
                </Space>
            )
        }
    ];

    return (
        <div>
             <Breadcrumb items={[{ title: '后台管理' }, { title: '培训管理', href: '/admin/training' }, { title: '报名名单' }]} style={{ marginBottom: 16 }} />

            <Card>
                <div style={{ marginBottom: 20 }}>
                    <Row justify="space-between" align="middle" gutter={[16, 16]}>
                        <Col>
                            <Space size="large">
                                <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/admin/training')}>返回</Button>
                                <span style={{ fontSize: 18, fontWeight: 'bold' }}>{trainingInfo.name}</span>
                            </Space>
                        </Col>
                        <Col>
                             <Space>
                                <Button icon={<ReloadOutlined />} onClick={() => {fetchTrainingInfo(); fetchSignups(pagination.current, pagination.pageSize)}}>刷新</Button>
                                <Button type="primary" icon={<UploadOutlined />} onClick={() => {
                                    setIsImportModalOpen(true);
                                    setImportResult(null); 
                                    setImportRows([]);
                                }}>批量导入</Button>
                                <Button icon={<FileExcelOutlined />} onClick={handleExport} loading={exportLoading}>导出名单</Button>
                             </Space>
                        </Col>
                    </Row>
                </div>

                <div style={{ background: '#f6ffed', border: '1px solid #b7eb8f', padding: '15px 24px', borderRadius: 6, marginBottom: 20, display:'flex', gap: 60 }}>
                    <Statistic title="最大名额" value={trainingInfo.maxCount || 0} />
                    <Statistic 
                        title="报名成功(已占用)" 
                        value={trainingInfo.signupCount || 0} 
                        valueStyle={{ color: '#3f8600', fontWeight: 'bold' }} 
                    />
                    <Statistic title="剩余名额" value={Math.max(0, (trainingInfo.maxCount || 0) - (trainingInfo.signupCount || 0))} />
                </div>

                <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', flexWrap: 'wrap', gap: 10 }}>
                    <Space>
                        <Select 
                            value={statusFilter} 
                            onChange={(val) => {
                                setStatusFilter(val);
                                setPagination(prev => ({ ...prev, current: 1 }));
                                fetchSignups(1, pagination.pageSize, val);
                            }} 
                            style={{ width: 120 }}
                        >
                            <Option value="all">全部状态</Option>
                            <Option value="approved">已报名</Option>
                            <Option value="cancelled">已取消</Option>
                            <Option value="rejected">已拒绝</Option>
                            <Option value="pending">待审核</Option>
                        </Select>

                        <Input 
                            placeholder="搜索姓名或学号" 
                            value={keyword} 
                            onChange={e => setKeyword(e.target.value)} 
                            onPressEnter={onSearch}
                            style={{ width: 250 }} 
                            prefix={<SearchOutlined />}
                            allowClear
                        />
                        <Button type="primary" onClick={onSearch}>搜索</Button>
                    </Space>

                    <Space>
                        <span style={{ color: '#888', marginRight: 8 }}>已选 {selectedRowKeys.length} 人</span>
                        <Button disabled={selectedRowKeys.length === 0} onClick={() => handleBatchUpdate('approved')} style={{ color: '#52c41a', borderColor: '#52c41a' }}>批量通过</Button>
                        <Button danger disabled={selectedRowKeys.length === 0} onClick={() => handleBatchUpdate('rejected')}>批量拒绝</Button>
                    </Space>
                </div>

                <Table 
                    columns={columns} 
                    dataSource={list} 
                    rowKey="signupId"
                    pagination={pagination}
                    loading={loading}
                    onChange={handleTableChange}
                    scroll={{ x: 1300 }} 
                    rowSelection={{
                        selectedRowKeys,
                        onChange: (keys) => setSelectedRowKeys(keys)
                    }}
                />
            </Card>

            <Modal 
                title="批量导入报名名单" 
                open={isImportModalOpen} 
                onCancel={closeImport}
                footer={[
                    <Button key="cancel" onClick={closeImport}>关闭</Button>,
                    <Button key="import" type="primary" loading={importLoading} onClick={handleBatchImport}>{importResult ? '确认导入可用数据' : '上传并校验'}</Button>
                ]}
                width={760}
            >
                <div style={{ marginBottom: 15, color: '#666', background:'#fffbe6', padding: 10, borderRadius: 4, border: '1px solid #ffe58f' }}>
                    <p><strong>格式要求：</strong>学校、学号、姓名</p>
                    <p style={{marginBottom: 0}}>学校请填写实名认证中的学校（大学）全称。下载模板填写后再上传。</p>
                </div>
                <Space wrap>
                    <Button icon={<DownloadOutlined />} onClick={() => downloadExcelTemplate({ fileName: '培训报名导入模板.xlsx', headers: IMPORT_HEADERS, examples: [['北京电子科技学院', '20230001', '张三']] })}>下载 Excel 模板</Button>
                    <Upload accept=".xlsx,.xls" maxCount={1} beforeUpload={(file) => selectImportFile(file as File)} onRemove={() => { setImportRows([]); setImportResult(null); }}>
                        <Button icon={<UploadOutlined />}>选择填写好的 Excel</Button>
                    </Upload>
                </Space>
                {importRows.length > 0 && <div style={{ marginTop: 8, color: '#666' }}>已读取 {importRows.length} 条数据</div>}
                {importResult && <ImportPreview data={importResult} />}
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
                    {rejectTarget?.type === 'batch' 
                        ? `将批量拒绝 ${selectedRowKeys.length} 名用户的报名。` 
                        : '将拒绝该用户的报名。'}
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

export default TrainingSignupManager;
