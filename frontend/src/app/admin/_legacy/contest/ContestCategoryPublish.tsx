"use client";

import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Form, Input, Button, Upload, message, Card, Select, Modal, Avatar, Spin, Switch, Row, Col } from 'antd';
import { MdEditor, type ExposeParam } from '@/app/components/content/SecureMarkdownEditor';
import { ArrowLeftOutlined, UserAddOutlined, PlusOutlined } from '@ant-design/icons';
import axios from 'axios';
import { userCardHtml, userSearchLabel } from '@/types/user';
import { useNavigate, useParams } from '@/app/admin/_legacy/router';

// 防抖函数
const debounce = (func: Function, wait: number) => {
    let timeout: any;
    return function (...args: any[]) {
        clearTimeout(timeout);
        timeout = setTimeout(() => { func(...args); }, wait);
    };
};

const ContestCategoryPublish: React.FC = () => {
    const [form] = Form.useForm();
    const navigate = useNavigate();
    const { id } = useParams();
    const [content, setContent] = useState('');
    const [loading, setLoading] = useState(false);
    const [imageUrl, setImageUrl] = useState<string>();
    const [logoFileId, setLogoFileId] = useState<number>();
    const editorRef = useRef<ExposeParam>(null);
    const [isUserModalOpen, setIsUserModalOpen] = useState(false);
    const [fetchingUser, setFetchingUser] = useState(false);
    const [userOptions, setUserOptions] = useState<any[]>([]);
    const userStr = localStorage.getItem('user_info');
    const currentUser = userStr ? JSON.parse(userStr) : null;

    useEffect(() => {
        if (id) {
            axios.get(`/api/contest/category/detail/${id}`).then(res => {
                if (res.data.code === 200) {
                    const data = res.data.data;
                    form.setFieldsValue({
                        ...data,
                        status: data.status === 1
                    });
                    setContent(data.intro);
                    if (data.logoFileId && data.logoUrl) {
                        setLogoFileId(data.logoFileId);
                        setImageUrl(`/api${data.logoUrl}`);
                    }
                }
            });
        }
    }, [id, form]);

    const handleUserSearch = useCallback(debounce(async (value: string) => {
        if (!value) return;
        setFetchingUser(true);
        try {
            const res = await axios.get(`/api/user/search?keyword=${value}`);
            if (res.data.code === 200) setUserOptions(res.data.data);
        } finally {
            setFetchingUser(false);
        }
    }, 500), []);

    const onSelectUser = (userId: string) => {
        const targetUser = userOptions.find(u => String(u.userId) === userId);
        if (targetUser) {
            const tag = userCardHtml(targetUser);
            editorRef.current?.insert(() => ({ targetValue: tag, select: false, deviationStart: 0, deviationEnd: 0 }));
            setIsUserModalOpen(false);
            setUserOptions([]);
        }
    };

    const handlePublish = async (values: any) => {
        if (!content) return message.error("请输入赛事介绍");
        setLoading(true);
        
        const payload = {
            categoryId: id,
            ...values,
            intro: content,
            logoFileId: logoFileId,
            status: values.status ? 1 : 0
        };
        
        try {
            const res = await axios.post('/api/contest/category/save', payload);
            if (res.data.code === 200) {
                message.success('保存成功');
                navigate('/admin/contest/category');
            } else {
                message.error(res.data.msg);
            }
        } catch(e) {
            message.error("请求失败");
        } finally {
            setLoading(false);
        }
    };

    const uploadProps = {
        name: 'file',
        action: '/api/file/upload',
        data: { uploaderId: currentUser?.userId },
        showUploadList: false,
        onChange(info: any) {
            if (info.file.status === 'uploading') {
                return;
            }
            if (info.file.status === 'done') {
                const fileId = info.file.response.data;
                setLogoFileId(fileId);
                setImageUrl(URL.createObjectURL(info.file.originFileObj));
                message.success('Logo 上传成功');
            } else if (info.file.status === 'error') {
                message.error('Logo 上传失败');
            }
        },
    };

    const uploadButton = (
        <div style={{ border: '1px dashed #d9d9d9', borderRadius: 8, width: 120, height: 120, display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', cursor: 'pointer', background: '#fafafa' }}>
            {loading ? <Spin /> : <PlusOutlined style={{ fontSize: 24, color: '#999' }} />}
            <div style={{ marginTop: 8, color: '#666' }}>上传Logo</div>
        </div>
    );

    return (
        <div style={{ maxWidth: 1200, margin: '20px auto', padding: '0 20px' }}>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/admin/contest/category')} style={{ marginBottom: 16 }}>返回列表</Button>
            <Card title={id ? "编辑赛事" : "新增赛事库"}>
                <Form form={form} layout="vertical" onFinish={handlePublish} initialValues={{ status: true, level: 'school' }}>
                    <Row gutter={24}>
                        <Col flex="140px">
                            <Form.Item label="赛事Logo">
                                <Upload {...uploadProps} listType="picture-card" className="avatar-uploader">
                                    {imageUrl ? <img src={imageUrl} alt="logo" style={{ width: '100%', height: '100%', objectFit: 'cover', borderRadius: 8 }} /> : uploadButton}
                                </Upload>
                            </Form.Item>
                        </Col>
                        <Col flex="auto">
                            <Form.Item name="name" label="赛事名称" rules={[{ required: true }]}>
                                <Input placeholder="请输入赛事全称，如：校内程序设计天梯赛" size="large" />
                            </Form.Item>
                            <Row gutter={16}>
                                <Col span={12}>
                                    <Form.Item name="level" label="赛事级别" rules={[{ required: true }]}>
                                        <Select size="large">
                                            <Select.Option value="national">国家级</Select.Option>
                                            <Select.Option value="provincial">省部级</Select.Option>
                                            <Select.Option value="city">市级</Select.Option>
                                            <Select.Option value="school">校级</Select.Option>
                                            <Select.Option value="department">院级</Select.Option>
                                        </Select>
                                    </Form.Item>
                                </Col>
                                <Col span={12}>
                                    <Form.Item name="status" label="是否上架" valuePropName="checked">
                                        <Switch checkedChildren="上架展示" unCheckedChildren="暂存下架" />
                                    </Form.Item>
                                </Col>
                            </Row>
                        </Col>
                    </Row>

                    <Form.Item label="赛事详细介绍" required style={{ marginTop: 16 }}>
                        <div style={{ marginBottom: 12 }}>
                             <Button icon={<UserAddOutlined />} onClick={() => setIsUserModalOpen(true)}>
                                插入组委会/联系人名片
                            </Button>
                            <span style={{ marginLeft: 12, color: '#888', fontSize: 13 }}>可在正文中插入负责老师或同学的名片，点击可跳转其主页。</span>
                        </div>
                        <MdEditor ref={editorRef} modelValue={content} onChange={setContent} style={{ height: '600px' }} />
                    </Form.Item>

                    <Form.Item>
                        <Button type="primary" htmlType="submit" size="large" loading={loading} block style={{ height: 48, fontSize: 16 }}>
                            保存并发布
                        </Button>
                    </Form.Item>
                </Form>
            </Card>

            <Modal title="插入用户" open={isUserModalOpen} onCancel={() => setIsUserModalOpen(false)} footer={null} destroyOnClose>
                <Select
                    showSearch
                    placeholder="输入昵称动态检索..."
                    filterOption={false}
                    onSearch={handleUserSearch}
                    onChange={onSelectUser}
                    notFoundContent={fetchingUser ? <Spin size="small" /> : null}
                    style={{ width: '100%' }}
                >
                    {userOptions.map(u => (
                        <Select.Option key={u.userId} value={String(u.userId)}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                                <Avatar size="small" src={u.avatarUrl} />
                                <span>{userSearchLabel(u)}</span>
                            </div>
                        </Select.Option>
                    ))}
                </Select>
            </Modal>
        </div>
    );
};

export default ContestCategoryPublish;
