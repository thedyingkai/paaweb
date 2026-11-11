"use client";

import React, { useEffect, useState } from 'react';
import { Card, Button, Image, Input, message, Tag, Row, Col, Typography, Empty, Spin, Tabs, Table, Modal, Descriptions, List, Badge, Space, Alert } from 'antd';
import { CheckCircleOutlined, CloseCircleOutlined, ReloadOutlined, SearchOutlined, EyeOutlined, AuditOutlined, LeftOutlined, RightOutlined } from '@ant-design/icons';
import axios from 'axios';
import dayjs from 'dayjs';
import useAdminPermissions from '@/hooks/useAdminPermissions';

const { TextArea } = Input;
const { Title, Text } = Typography;

interface PendingUser {
    userId: number;
    realName: string;
    userAccount: string;
    numberId: string;
    universityName: string;
    schoolName: string;
    majorName: string;
    className: string;
    studentCardUrl: string;
    accountStatus: string;
    registerTime: string;
}

interface AuthAuditRecord {
    auditId: number;
    applicantId: number;
    realName: string;     
    numberId: string;     
    universityName: string;
    status: string;       
    auditorName: string; 
    createTime: string;   
    comments: string;
    studentCardUrl: string; 
}

interface AuditLog {
    auditId: number;
    status: string;
    comments: string;
    auditorName: string; 
    createTime: string;
}

const AuthAudit: React.FC = () => {
    const hasPermission = useAdminPermissions();
    const canAudit = hasPermission('manage_user') && hasPermission('manage_user_status');
    const [activeTab, setActiveTab] = useState('pending');
    const [loading, setLoading] = useState(false);
    const [pendingQueue, setPendingQueue] = useState<PendingUser[]>([]); 
    const [currentIndex, setCurrentIndex] = useState(0);
    const [sidebarSearch, setSidebarSearch] = useState('');
    const [currentUser, setCurrentUser] = useState<PendingUser | null>(null);
    const [comment, setComment] = useState('');
    const [submitting, setSubmitting] = useState(false);
    const [listData, setListData] = useState<any[]>([]);
    const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
    const [keyword, setKeyword] = useState('');
    const [viewModalOpen, setViewModalOpen] = useState(false);
    const [viewUser, setViewUser] = useState<any | null>(null);
    const [latestAudit, setLatestAudit] = useState<AuditLog | null>(null);
    const [detailLoading, setDetailLoading] = useState(false);
    const [adminId, setAdminId] = useState<number>(0);
    const [imageLoading, setImageLoading] = useState(false);
    const [viewImageLoading, setViewImageLoading] = useState(false);
    useEffect(() => {
        const userInfo = localStorage.getItem('user_info');
        if (userInfo) {
            const u = JSON.parse(userInfo);
            setAdminId(u.userId);
        }
        setPagination(p => ({ ...p, current: 1 }));
        setKeyword('');
    }, [activeTab]);

    useEffect(() => {
        fetchData();
    }, [activeTab, pagination.current]); 

    const fetchData = () => {
        if (activeTab === 'pending') {
            fetchPendingQueue(); 
        } else {
            fetchHistoryList(pagination.current);
        }
    };

    const fetchPendingQueue = async () => {
        setLoading(true);
        setComment('');
        // Do not keep showing the previous applicant while the next queue item
        // (and especially its private proof image) is being resolved.
        setCurrentUser(null);
        setImageLoading(false);
        try {
            const res = await axios.get('/api/admin/user/auth-list', {
                params: { current: 1, size: 100, status: 'pending' } 
            });
            if (res.data.code === 200) {
                const records = res.data.data.records;
                setPendingQueue(records);
                if (records.length > 0) {
                    setCurrentUser(records[0]);
                    setCurrentIndex(0);
                    setImageLoading(Boolean(records[0].studentCardUrl));
                } else {
                    setCurrentUser(null);
                    setImageLoading(false);
                }
            }
        } catch (e) {
            message.error('获取待审核数据失败');
        } finally {
            setLoading(false);
        }
    };

    const handleSwitchUser = (index: number) => {
        if (index >= 0 && index < pendingQueue.length) {
            const nextUser = pendingQueue[index];
            setCurrentIndex(index);
            setCurrentUser(nextUser);
            setImageLoading(Boolean(nextUser.studentCardUrl));
            setComment('');
        }
    };

    const fetchHistoryList = async (page: number) => {
        setLoading(true);
        try {
            const res = await axios.get('/api/admin/user/auth-history-list', {
                params: { 
                    current: page, 
                    size: pagination.pageSize, 
                    status: activeTab, 
                    keyword 
                }
            });
            if (res.data.code === 200) {
                setListData(res.data.data.records);
                setPagination({
                    current: res.data.data.current,
                    pageSize: res.data.data.size,
                    total: res.data.data.total
                });
            }
        } catch (e) {
            message.error('加载列表失败');
        } finally {
            setLoading(false);
        }
    };

    const fetchLatestAuditInfo = async (userId: number) => {
        setDetailLoading(true);
        setLatestAudit(null);
        try {
            const res = await axios.get('/api/admin/user/latest-audit', {
                params: { userId }
            });
            if (res.data.code === 200) {
                setLatestAudit(res.data.data);
            }
        } catch (e) {
            message.error('加载审核详情失败');
        } finally {
            setDetailLoading(false);
        }
    };

    const handleSearch = () => {
        setPagination({ ...pagination, current: 1 });
        fetchHistoryList(1);
    };

    const handleAudit = async (pass: boolean) => {
        if (!canAudit) { message.error('没有审核用户状态的权限'); return; }
        // 驳回时必须填写意见
        if (!pass && !comment.trim()) {
            message.warning('驳回时必须输入审核意见');
            return;
        }
        
        if (!currentUser) return;

        // 如果是审核通过且没有填写意见，自动填充为默认文案
        const finalComment = (pass && !comment.trim()) ? '信息无误，审核通过。' : comment;

        setSubmitting(true);
        try {
            const res = await axios.post('/api/admin/user/audit-auth', {
                userId: currentUser.userId,
                pass: pass,
                reason: finalComment,
                operatorId: adminId
            });

            if (res.data.code === 200) {
                message.success(pass ? '已通过' : '已驳回');
                await fetchPendingQueue(); 
                setSubmitting(false);
            } else {
                message.error(res.data.msg);
                setSubmitting(false);
            }
        } catch (e) {
            message.error('操作失败');
            setSubmitting(false);
        }
    };

    const handleOpenView = (record: any) => {
        const targetUserId = record.applicantId || record.userId;
        const displayUser = {
            userId: targetUserId,
            realName: record.realName,
            numberId: record.numberId,
            userAccount: record.userAccount || 'N/A',
            universityName: record.universityName,
            schoolName: record.schoolName || '-',
            majorName: record.majorName || '-',
            studentCardUrl: record.studentCardUrl || '', 
        };

        setViewUser(displayUser);
        setViewImageLoading(Boolean(displayUser.studentCardUrl));
        setViewModalOpen(true);
        
        if (record.auditId) {
            setLatestAudit({
                auditId: record.auditId,
                status: record.status,
                comments: record.comments,
                auditorName: record.auditorName,
                createTime: record.createTime
            });
            setDetailLoading(false);
        } else {
            fetchLatestAuditInfo(targetUserId);
        }
    };

    const renderWorkbench = () => {
        if (loading && !currentUser) return <div style={{ textAlign: 'center', padding: 100 }}><Spin size="large" tip="加载任务中..." /></div>;
        if (!currentUser) {
            return (
                <Empty description="暂无待审核申请" image={Empty.PRESENTED_IMAGE_SIMPLE}>
                    <Button icon={<ReloadOutlined />} onClick={fetchPendingQueue}>刷新</Button>
                </Empty>
            );
        }

        const filteredPending = pendingQueue.filter(u => 
            u.realName.includes(sidebarSearch) || u.numberId.includes(sidebarSearch)
        );

        return (
            <Row gutter={24}>
                <Col span={6} style={{ borderRight: '1px solid #f0f0f0' }}>
                    <div style={{ marginBottom: 12 }}>
                        <Input 
                            prefix={<SearchOutlined />} 
                            placeholder="快速查询待审核用户..." 
                            onChange={e => setSidebarSearch(e.target.value)}
                            allowClear 
                        />
                    </div>
                    <List
                        size="small"
                        bordered
                        dataSource={filteredPending}
                        style={{ maxHeight: '600px', overflowY: 'auto' }}
                        renderItem={(item) => {
                            const queueIndex = pendingQueue.findIndex(p => p.userId === item.userId);
                            return (
                                <List.Item 
                                    onClick={() => handleSwitchUser(queueIndex)}
                                    style={{ 
                                        cursor: 'pointer', 
                                        backgroundColor: currentUser.userId === item.userId ? '#e6f7ff' : 'transparent' 
                                    }}
                                >
                                    <List.Item.Meta
                                        title={<Text strong>{item.realName}</Text>}
                                        description={item.numberId}
                                    />
                                </List.Item>
                            );
                        }}
                    />
                </Col>

                <Col span={18}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20, padding: '12px 20px', background: '#fafafa', borderRadius: 8 }}>
                        <Space size="large">
                            <Badge count={pendingQueue.length} overflowCount={999}>
                                <Title level={5} style={{ margin: 0 }}>待审核队列</Title>
                            </Badge>
                            <Text type="secondary">当前展示：{currentIndex + 1} / {pendingQueue.length}</Text>
                        </Space>
                        <Space>
                            <Button 
                                icon={<LeftOutlined />} 
                                onClick={() => handleSwitchUser(currentIndex - 1)}
                                disabled={currentIndex === 0}
                            >
                                上一条
                            </Button>
                            <Button 
                                icon={<RightOutlined />} 
                                onClick={() => handleSwitchUser(currentIndex + 1)}
                                disabled={currentIndex === pendingQueue.length - 1}
                            >
                                下一条
                            </Button>
                        </Space>
                    </div>

                    <Row gutter={24}>
                        <Col span={12} style={{ borderRight: '1px solid #f0f0f0', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                            <Title level={5}>提交的证明材料</Title>
                            <div style={{ 
                                width: '100%', height: 450, background: '#fafafa', 
                                display: 'flex', justifyContent: 'center', alignItems: 'center',
                                border: '1px dashed #d9d9d9', borderRadius: 8, overflow: 'hidden', position: 'relative'
                            }}>
                                {currentUser.studentCardUrl ? (
                                    <>
                                        {imageLoading && <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1 }}><Spin tip="正在加载材料..." /></div>}
                                        <Image
                                            key={currentUser.userId}
                                            src={currentUser.studentCardUrl}
                                            onLoad={() => setImageLoading(false)}
                                            onError={() => setImageLoading(false)}
                                            style={{ maxHeight: 450, maxWidth: '100%', opacity: imageLoading ? 0 : 1, transition: 'opacity 0.2s' }}
                                        />
                                    </>
                                ) : (
                                    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无证明材料" />
                                )}
                            </div>
                        </Col>
                        <Col span={12} style={{ paddingLeft: 24 }}>
                            <Title level={5}>申请人信息</Title>
                            <Card size="small" style={{ marginBottom: 24, background: '#f9f9f9' }}>
                                <p><Text type="secondary">真实姓名：</Text> <Text strong style={{ fontSize: 16 }}>{currentUser.realName}</Text></p>
                                <p><Text type="secondary">学号/工号：</Text> <Text code>{currentUser.numberId}</Text></p>
                                <p><Text type="secondary">用户账号：</Text> {currentUser.userAccount}</p>
                                <p><Text type="secondary">所属学校：</Text> {currentUser.universityName}</p>
                                <p><Text type="secondary">所属学院：</Text> {currentUser.schoolName}</p>
                                <p><Text type="secondary">专业班级：</Text> {currentUser.majorName} {currentUser.className}</p>
                            </Card>

                            <Title level={5}>审核处理</Title>
                            <div style={{ marginBottom: 8 }}><Text type="danger">* </Text>审核意见 (驳回必填):</div>
                            <TextArea
                                rows={4}
                                value={comment}
                                onChange={e => setComment(e.target.value)}
                                placeholder="请输入审核意见"
                                style={{ marginBottom: 24 }}
                            />
                            <Row gutter={16}>
                                <Col span={12}><Button danger block size="large" icon={<CloseCircleOutlined />} onClick={() => handleAudit(false)} loading={submitting} disabled={!canAudit}>驳回申请</Button></Col>
                                <Col span={12}><Button type="primary" block size="large" icon={<CheckCircleOutlined />} onClick={() => handleAudit(true)} loading={submitting} disabled={!canAudit} style={{ backgroundColor: '#52c41a', borderColor: '#52c41a' }}>审核通过</Button></Col>
                            </Row>
                        </Col>
                    </Row>
                </Col>
            </Row>
        );
    };

    const renderList = () => {
        const columns = [
            { title: '记录ID', dataIndex: 'auditId', width: 80 },
            { title: '姓名', dataIndex: 'realName', width: 100 },
            { title: '学号/工号', dataIndex: 'numberId', width: 140 },
            { title: '学校', dataIndex: 'universityName', width: 140, ellipsis: true },
            { 
                title: '审核结果', 
                dataIndex: 'status', 
                width: 90,
                render: (status: string) => {
                    if (status === 'approved') return <Tag color="success">通过</Tag>;
                    if (status === 'rejected') return <Tag color="error">驳回</Tag>;
                    return <Tag color="default">{status}</Tag>;
                }
            },
            { title: '审核员', dataIndex: 'auditorName', width: 130, render: (t:any) => <Tag color="blue">{t}</Tag> },
            { 
                title: '审核时间', 
                dataIndex: 'createTime', 
                width: 145,
                render: (t: string) => dayjs(t).format('YYYY-MM-DD HH:mm')
            },
            { title: '审核意见', dataIndex: 'comments', ellipsis: true },
            { 
                title: '操作', 
                key: 'action',
                width: 120,
                render: (_: any, record: any) => (
                    <Button type="link" icon={<EyeOutlined />} onClick={() => handleOpenView(record)}>
                        详情
                    </Button>
                )
            }
        ];

        return (
            <div>
                <div style={{ marginBottom: 16, display: 'flex', gap: 10 }}>
                    <Input placeholder="搜索姓名、学号" value={keyword} onChange={e => setKeyword(e.target.value)} onPressEnter={handleSearch} style={{ width: 300 }} allowClear />
                    <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>搜索</Button>
                    <Button icon={<ReloadOutlined />} onClick={() => fetchHistoryList(1)}>刷新</Button>
                </div>
                <Table 
                    columns={columns} 
                    dataSource={listData} 
                    rowKey="auditId"
                    loading={loading}
                    pagination={{ ...pagination, onChange: (page) => { setPagination(p => ({...p, current: page})); } }}
                />
            </div>
        );
    };

    return (
        <Card title="学生身份认证审核">
            <Alert style={{ marginBottom: 20 }} type="info" showIcon title="通过审核只确认学生身份" description="请核对学生卡上的学校、姓名和学号。审核通过后，普通参赛者可以报名校赛、维护个人档案；校队资格需另到“校队成员”名单中绑定账号并设置任期。" />
            <Tabs 
                activeKey={activeTab} 
                onChange={(key) => setActiveTab(key)}
                type="card"
                items={[
                    { key: 'pending', label: '待审核 (工作台)' },
                    { key: 'approved', label: '已通过记录' },
                    { key: 'rejected', label: '已驳回记录' },
                    { key: 'all', label: '全部记录' },
                ]}
                style={{ marginBottom: 20 }}
            />

            {activeTab === 'pending' ? renderWorkbench() : renderList()}

            <Modal
                title="认证详情"
                open={viewModalOpen}
                footer={null}
                onCancel={() => setViewModalOpen(false)}
                width={700}
            >
                {viewUser && (
                    <Row gutter={24}>
                        <Col span={10}>
                            {viewUser.studentCardUrl ? (
                                <div style={{ position: 'relative', minHeight: 200, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                                    {viewImageLoading && <Spin tip="正在加载图片..." />}
                                    <Image
                                        key={viewUser.userId}
                                        src={viewUser.studentCardUrl}
                                        onLoad={() => setViewImageLoading(false)}
                                        onError={() => setViewImageLoading(false)}
                                        style={{ maxWidth: '100%', borderRadius: 8, opacity: viewImageLoading ? 0 : 1 }}
                                    />
                                </div>
                            ) : (
                                <div style={{height: 200, background: '#f0f0f0', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#999', borderRadius: 8}}>
                                    暂无图片预览
                                </div>
                            )}
                            <div style={{ marginTop: 16 }}>
                                <p><strong>姓名：</strong>{viewUser.realName}</p>
                                <p><strong>学校：</strong>{viewUser.universityName}</p>
                                <p><strong>专业：</strong>{viewUser.majorName}</p>
                                <p><strong>学号：</strong>{viewUser.numberId}</p>
                                <p><strong>账号：</strong>{viewUser.userAccount}</p>
                            </div>
                        </Col>
                        <Col span={1} style={{ borderRight: '1px solid #f0f0f0' }}></Col>
                        <Col span={13}>
                            <Title level={5}><AuditOutlined /> 审核记录详情</Title>
                            {detailLoading ? <Spin /> : (
                                latestAudit ? (
                                    <div style={{ marginTop: 20 }}>
                                        <Descriptions column={1} bordered>
                                            <Descriptions.Item label="审核状态">
                                                {latestAudit.status === 'approved' ? 
                                                    <Tag color="green" icon={<CheckCircleOutlined />}>通过</Tag> : 
                                                    <Tag color="red" icon={<CloseCircleOutlined />}>驳回</Tag>
                                                }
                                            </Descriptions.Item>
                                            <Descriptions.Item label="审核员">
                                                <Tag color="blue">{latestAudit.auditorName}</Tag>
                                            </Descriptions.Item>
                                            <Descriptions.Item label="审核时间">
                                                {dayjs(latestAudit.createTime).format('YYYY-MM-DD HH:mm:ss')}
                                            </Descriptions.Item>
                                            <Descriptions.Item label="审核意见">
                                                {latestAudit.comments}
                                            </Descriptions.Item>
                                        </Descriptions>
                                    </div>
                                ) : (
                                    <Empty description="暂无审核记录" />
                                )
                            )}
                        </Col>
                    </Row>
                )}
            </Modal>
        </Card>
    );
};

export default AuthAudit;
