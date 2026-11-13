"use client";

import React, { useEffect, useState, useMemo, useRef } from 'react';
import { Form, Input, Button, DatePicker, InputNumber, Select, message, Card, Spin, Row, Col, Upload, Modal, Avatar, Radio, Space } from 'antd';
import { MdEditor, type ExposeParam } from '@/app/components/content/SecureMarkdownEditor';
import { ArrowLeftOutlined, UploadOutlined, UserAddOutlined, UserOutlined, MinusOutlined } from '@ant-design/icons';
import { useNavigate, useParams } from '@/app/admin/_legacy/router';
import axios from 'axios';
import { userCardHtml, userSearchLabel } from '@/types/user';
import dayjs from 'dayjs';

const { Option } = Select;
const { RangePicker } = DatePicker;

const EventPublish: React.FC = () => {
    const [form] = Form.useForm();
    const noSignupDeadline = Form.useWatch('noSignupDeadline', form);
    const navigate = useNavigate();
    const { id } = useParams();
    
    const [loading, setLoading] = useState(false);
    const [description, setDescription] = useState('');
    const [isTeam, setIsTeam] = useState(0); 
    
    const editorRef = useRef<ExposeParam>(null);
    const [isUserModalOpen, setIsUserModalOpen] = useState(false);
    const [userOptions, setUserOptions] = useState<any[]>([]);
    const [fileList, setFileList] = useState<any[]>([]);
    
    const [terms, setTerms] = useState<any[]>([]);
    const [categories, setCategories] = useState<any[]>([]);

    const userStr = localStorage.getItem('user_info');
    const user = useMemo(() => userStr ? JSON.parse(userStr) : null, [userStr]);

    useEffect(() => {
        if (!user) {
            message.error('请先登录');
            navigate('/login');
            return;
        }
        fetchOptions();
        if (id) fetchEventDetail(id);
    }, [id, user]);

    const fetchOptions = async () => {
        try {
            const [termRes, catRes] = await Promise.all([
                axios.get('/api/common/terms'),
                axios.get('/api/contest/category/list', { params: { size: 100 } })
            ]);

            if (termRes.data.code === 200) {
                const rawTerms = termRes.data.data || [];
                rawTerms.sort((a: any, b: any) => dayjs(b.startTime).valueOf() - dayjs(a.startTime).valueOf());
                setTerms(rawTerms);
            }
            if (catRes.data.code === 200) {
                const pageData = catRes.data.data;
                const categoryList = (pageData && pageData.records) ? pageData.records : [];
                setCategories(categoryList);
            }
        } catch (e) { 
            console.error("获取选项失败", e);
        }
    };

    const fetchEventDetail = async (eventId: string) => {
        setLoading(true);
        try {
            const res = await axios.get(`/api/event/detail/${eventId}`);
            if (res.data.code === 200) {
                const data = res.data.data;
                setIsTeam(data.isTeam);
                
                const min = data.minMember || 1;
                const max = data.maxMember || 1;

                form.setFieldsValue({
                    name: data.name,
                    termId: data.termId,
                    categoryId: data.categoryId,
                    isTeam: data.isTeam,
                    signupMode: data.signupMode || 'open',
                    rankType: data.type || 'XCPC', 
                    maxSignup: data.maxSignup,
                    minMember: min,
                    maxMember: max,
                    signupStart: dayjs(data.signupStart),
                    signupEnd: data.signupEnd ? dayjs(data.signupEnd) : null,
                    noSignupDeadline: !data.signupEnd,
                    eventTime: [dayjs(data.eventStart), dayjs(data.eventEnd)],
                });
                setDescription(data.description);
                if(data.attachments) {
                    setFileList(data.attachments.map((f:any) => ({
                        uid: f.fileId, name: f.name, status: 'done', url: `/api${f.url}`, response: { data: f.fileId }
                    })));
                }
            }
        } catch(e) {
            message.error("加载赛事详情失败");
        } finally { 
            setLoading(false); 
        }
    };

    const handleSearchUser = async (val: string) => {
        if(!val) return;
        try {
            const res = await axios.get(`/api/user/search?keyword=${val}`);
            if(res.data.code === 200) {
                setUserOptions(res.data.data || []);
            }
        } catch(e) { console.error(e); }
    };

    const insertUserCard = (userId: string) => {
        const u = userOptions.find(it => String(it.userId) === userId);
        if(u) {
             const tag = userCardHtml(u);
             editorRef.current?.insert(() => ({ targetValue: tag, select: false, deviationStart: 0, deviationEnd: 0 }));
             setIsUserModalOpen(false);
        }
    };

    const uploadProps = {
      name: 'file',
      action: '/api/file/upload',
      data: { uploaderId: user?.userId },
      onChange(info: any) {
          let newFileList = [...info.fileList];
          if (info.file.status === 'done') message.success(`${info.file.name} 上传成功`);
          setFileList(newFileList);
      }
    };

    const onFinish = async (values: any) => {
        if (!description) return message.error('请输入赛事详情');
        
        if (values.isTeam === 1) {
            if (values.minMember > values.maxMember) {
                return message.error('团队最小人数不能大于最大人数');
            }
        }

        setLoading(true);
        try {
            const validFileIds = fileList
              .filter(f => f.status === 'done' && f.response)
              .map(f => f.response.data);

            const payload = {
                eventId: id, 
                name: values.name,
                termId: values.termId,
                categoryId: values.categoryId,
                isTeam: values.isTeam,
                type: values.rankType,
                signupMode: values.signupMode || 'open',
                minMember: values.isTeam === 1 ? values.minMember : 1,
                maxMember: values.isTeam === 1 ? values.maxMember : 1,
                maxSignup: values.maxSignup ?? null,
                signupStart: values.signupStart.format('YYYY-MM-DD HH:mm:ss'),
                signupEnd: values.noSignupDeadline ? null : values.signupEnd.format('YYYY-MM-DD HH:mm:ss'),
                eventStart: values.eventTime[0].format('YYYY-MM-DD HH:mm:ss'),
                eventEnd: values.eventTime[1].format('YYYY-MM-DD HH:mm:ss'),
                description: description,
                attachmentIds: validFileIds,
                operatorId: user.userId,
                notifyParticipants: !!values.notifyParticipants
            };

            const url = id ? '/api/event/update' : '/api/event/publish';
            const res = await axios.post(url, payload);

            if (res.data.code === 200) {
                message.success(id ? '修改成功' : '发布成功');
                navigate('/admin/contest');
            } else {
                message.error(res.data.msg);
            }
        } catch (e) {
            message.error('提交失败');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div style={{ maxWidth: 1200, margin: '20px auto', padding: '0 20px' }}>
             <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/admin/contest')} style={{ marginBottom: 16 }}>返回列表</Button>
             <Card title={id ? "编辑赛事" : "发布新赛事"}>
                <Form 
                    form={form} 
                    layout="vertical" 
                    onFinish={onFinish} 
                    initialValues={{ signupMode: 'open',
                        isTeam: 0, 
                        rankType: 'XCPC',
                        maxSignup: 100,
                        noSignupDeadline: false,
                        minMember: 1, 
                        maxMember: 3
                    }}
                >
                    <Form.Item name="signupMode" label="参赛方式">
                        <Radio.Group options={[{label:'自主报名',value:'open'},{label:'按确定名单导入',value:'roster'}]} />
                    </Form.Item>
                    <Row gutter={24}>
                        <Col span={12}>
                            <Form.Item name="name" label="赛事名称" rules={[{ required: true }]}>
                                <Input size="large" placeholder="如：2026年秋季校赛" />
                            </Form.Item>
                        </Col>
                        <Col span={6}>
                             <Form.Item name="termId" label="所属学期" rules={[{ required: true }]}>
                                <Select size="large" placeholder="选择学期">
                                    {(terms || []).map(t => <Option key={t.termId} value={t.termId}>{t.termName}</Option>)}
                                </Select>
                            </Form.Item>
                        </Col>
                        <Col span={6}>
                             <Form.Item name="categoryId" label="赛事分类" rules={[{ required: true }]}>
                                <Select size="large" placeholder="选择分类">
                                    {(categories || []).map(c => <Option key={c.categoryId} value={c.categoryId}>{c.name}</Option>)}
                                </Select>
                            </Form.Item>
                        </Col>
                    </Row>
                    <Row gutter={24}>
                        <Col span={6}>
                             <Form.Item name="isTeam" label="参赛形式" rules={[{ required: true }]}>
                                <Radio.Group 
                                    buttonStyle="solid" 
                                    size="large" 
                                    onChange={e => setIsTeam(e.target.value)}
                                    disabled={!!id}
                                >
                                    <Radio.Button value={0}>个人赛</Radio.Button>
                                    <Radio.Button value={1}>团队赛</Radio.Button>
                                </Radio.Group>
                            </Form.Item>
                        </Col>
                        
                        <Col span={6}>
                            <Form.Item name="rankType" label="榜单赛制" rules={[{ required: true }]}>
                                <Select size="large">
                                    <Option value="XCPC">XCPC</Option>
                                    <Option value="LANQIAO">LANQIAO</Option>
                                    <Option value="GPLT">GPLT</Option>
                                </Select>
                            </Form.Item>
                        </Col>
                        
                        {isTeam === 1 && (
                            <Col span={6}>
                                <Form.Item label="团队人数 (Min-Max)" required style={{marginBottom: 0}}>
                                    <Space.Compact block>
                                        <Form.Item name="minMember" noStyle rules={[{ required: true }]}>
                                            <InputNumber min={1} max={10} style={{ width: '50%' }} placeholder="Min" />
                                        </Form.Item>
                                        <Button type="text" disabled style={{ cursor: 'default', borderLeft: 0, pointerEvents: 'none', background: '#fafafa' }}>
                                            <MinusOutlined />
                                        </Button>
                                        <Form.Item name="maxMember" noStyle rules={[{ required: true }]}>
                                            <InputNumber min={1} max={10} style={{ width: '50%' }} placeholder="Max" />
                                        </Form.Item>
                                    </Space.Compact>
                                </Form.Item>
                            </Col>
                        )}
                        
                        <Col span={6}>
                            <Form.Item name="maxSignup" label={isTeam === 1 ? "最大队伍数" : "最大人数"} extra="可留空，按审核确认名单统计人数。">
                                <InputNumber min={1} max={100000} placeholder="不限制名额" style={{ width: '100%' }} size="large" />
                            </Form.Item>
                        </Col>
                    </Row>
                    <Row gutter={24}>
                        <Col span={12}>
                            <Form.Item name="signupStart" label="报名开始时间" rules={[{ required: true }]}>
                                <DatePicker showTime format="YYYY-MM-DD HH:mm" style={{ width: '100%' }} size="large" />
                            </Form.Item>
                            <Form.Item name="noSignupDeadline" label="报名截止设置">
                                <Radio.Group options={[{label:'设置截止时间',value:false},{label:'不设截止时间',value:true}]} />
                            </Form.Item>
                            {!noSignupDeadline && <Form.Item name="signupEnd" label="报名截止时间" rules={[{required:true}]}>
                                <DatePicker showTime format="YYYY-MM-DD HH:mm" style={{width:'100%'}} size="large" />
                            </Form.Item>}
                        </Col>
                        <Col span={12}>
                             <Form.Item name="eventTime" label="比赛起止时间" rules={[{ required: true }]}>
                                <RangePicker showTime format="YYYY-MM-DD HH:mm" style={{ width: '100%' }} size="large" />
                            </Form.Item>
                        </Col>
                    </Row>
                    {id && (
                        <Form.Item
                            name="notifyParticipants"
                            label="邮件通知"
                            initialValue={false}
                            extra="仅当比赛时间或报名时间发生变化时生效；选择“发送”后会邮件通知已报名同学。"
                        >
                            <Radio.Group buttonStyle="solid">
                                <Radio.Button value={false}>不发送</Radio.Button>
                                <Radio.Button value={true}>发送给已报名同学</Radio.Button>
                            </Radio.Group>
                        </Form.Item>
                    )}
                    
                    <Form.Item label="附件上传">
                        <Space>
                            <Upload {...uploadProps} fileList={fileList} showUploadList={false}>
                                <Button icon={<UploadOutlined />}>上传附件</Button>
                            </Upload>
                            <Button icon={<UserAddOutlined />} onClick={() => setIsUserModalOpen(true)}>插入名片</Button>
                        </Space>
                        <div style={{ marginTop: 8, maxWidth: 600 }}>
                            <Upload {...uploadProps} fileList={fileList} showUploadList={true}><span style={{ display: 'none' }} /></Upload>
                        </div>
                    </Form.Item>

                    <Form.Item label="赛事详情" required>
                        <MdEditor ref={editorRef} modelValue={description} onChange={setDescription} style={{ height: '400px' }} />
                    </Form.Item>

                    <Button type="primary" htmlType="submit" size="large" block loading={loading}>{id ? "保存修改" : "确认发布"}</Button>
                </Form>
             </Card>

             <Modal title="插入用户" open={isUserModalOpen} onCancel={() => setIsUserModalOpen(false)} footer={null}>
                <Select 
                    showSearch 
                    placeholder="搜索用户..." 
                    onSearch={handleSearchUser} 
                    onChange={insertUserCard} 
                    style={{width:'100%'}} 
                    filterOption={false}
                    notFoundContent={loading ? <Spin size="small" /> : null}
                >
                    {(userOptions || []).map(u => (
                        <Option key={u.userId} value={String(u.userId)}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                                <Avatar size="small" src={u.avatarUrl} icon={!u.avatarUrl && <UserOutlined />} />
                                <span>{userSearchLabel(u)}</span>
                            </div>
                        </Option>
                    ))}
                </Select>
             </Modal>
        </div>
    );
};
export default EventPublish;
