"use client";

import React, { useEffect, useState, useMemo, useRef } from 'react';
import { Form, Input, Button, DatePicker, InputNumber, Select, message, Card, Spin, Row, Col, Divider, Alert, Upload, Modal, Avatar } from 'antd';
import { MdEditor, type ExposeParam } from '@/app/components/content/SecureMarkdownEditor';
import { ArrowLeftOutlined, SearchOutlined, UploadOutlined, UserAddOutlined } from '@ant-design/icons';
import { useNavigate, useParams } from '@/app/admin/_legacy/router';
import axios from 'axios';
import { userCardHtml, userSearchLabel } from '@/types/user';
import dayjs from 'dayjs';

const { Option } = Select;
const { RangePicker } = DatePicker;
const DEFAULT_PUBLISHER_NAME = 'ACM 校队';

// 手写防抖函数
const debounce = <F extends (...args: any[]) => void>(func: F, wait: number) => {
    let timeout: ReturnType<typeof setTimeout> | null = null;
    return function (this: any, ...args: Parameters<F>) {
        if (timeout) clearTimeout(timeout);
        timeout = setTimeout(() => {
            func.apply(this, args);
        }, wait);
    };
};

const TrainingPublish: React.FC = () => {
    const [form] = Form.useForm();
    const navigate = useNavigate();
    const { id } = useParams();
    
    const [loading, setLoading] = useState(false);
    const [description, setDescription] = useState('');
    
    // Ref 和 插入用户相关状态
    const editorRef = useRef<ExposeParam>(null);
    const [isUserModalOpen, setIsUserModalOpen] = useState(false);
    const [fetchingUser, setFetchingUser] = useState(false);
    const [userOptionsForInsert, setUserOptionsForInsert] = useState<any[]>([]);

    const [fileList, setFileList] = useState<any[]>([]);
    
    const [terms, setTerms] = useState<any[]>([]);
    const [teacherOptions, setTeacherOptions] = useState<any[]>([]);
    const [fetchingTeacher, setFetchingTeacher] = useState(false);

    const userStr = localStorage.getItem('user_info');
    const user = useMemo(() => userStr ? JSON.parse(userStr) : null, [userStr]);

    useEffect(() => {
        const fetchTerms = async () => {
            try {
                const res = await axios.get('/api/common/terms');
                if (res.data.code === 200) {
                    const rawTerms = res.data.data || [];
                    rawTerms.sort((a: any, b: any) => dayjs(b.startTime).valueOf() - dayjs(a.startTime).valueOf());
                    setTerms(rawTerms);
                }
            } catch (e) { console.error('获取学期失败', e); }
        };

        const fetchTrainingDetail = async (trainId: string) => {
            setLoading(true);
            try {
                const res = await axios.get(`/api/training/detail/${trainId}`);
                if (res.data.code === 200) {
                    const data = res.data.data;
                    const teachers = data.teachers?.length
                        ? data.teachers
                        : data.teacherId
                            ? [{ teacherId: data.teacherId, teacherName: data.teacherName, teacherAccount: data.teacherAccount }]
                            : [];
                    form.setFieldsValue({
                        name: data.name,
                        termId: data.termId,
                        teacherIds: teachers.map((teacher: any) => teacher.teacherId),
                        publisherName: data.publisherName || DEFAULT_PUBLISHER_NAME,
                        maxCount: data.maxCount,
                        signupTime: [dayjs(data.signupStart), dayjs(data.signupEnd)],
                        trainTime: [dayjs(data.trainStart), dayjs(data.trainEnd)],
                    });
                    setDescription(data.description);
                    setTeacherOptions(teachers.map((teacher: any) => ({
                        value: teacher.teacherId,
                        label: `${teacher.teacherName} (${teacher.teacherAccount || '讲师'})`
                    })));
                    
                    if(data.attachments) {
                        setFileList(data.attachments.map((f:any) => ({
                            uid: f.fileId, 
                            name: f.name, 
                            status: 'done', 
                            url: `/api${f.url}`,
                            response: { data: f.fileId }
                        })));
                    }
                }
            } catch (e) {
                message.error('加载详情失败');
            } finally {
                setLoading(false);
            }
        };

        const adminRoles = ['stu_admin', 'department', 'super_admin', 'supper_admin', 'admin'];
        if (!user || !adminRoles.includes(user.role)) {
            message.error('无权访问此页面');
            navigate('/home');
            return;
        }

        fetchTerms();

        if (id) {
            fetchTrainingDetail(id);
        }
    }, [id, user, navigate, form]); // Added form dependency

    // 讲师搜索
    const handleTeacherSearch = useMemo(() => {
        const loadOptions = async (value: string) => {
            if (!value) {
                return;
            }
            setFetchingTeacher(true);
            try {
                const res = await axios.get(`/api/user/search?keyword=${encodeURIComponent(value)}`);
                if (res.data.code === 200) {
                    const options = res.data.data.map((u: any) => ({
                        label: userSearchLabel(u),
                        value: u.userId,
                    }));
                    setTeacherOptions(current => {
                        const merged = new Map(current.map(option => [option.value, option]));
                        options.forEach((option: any) => merged.set(option.value, option));
                        return Array.from(merged.values());
                    });
                }
            } finally {
                setFetchingTeacher(false);
            }
        };
        return debounce(loadOptions, 800);
    }, []);

    // 插入用户的搜索
    const handleUserInsertSearch = useMemo(() => {
        const loadUsers = async (value: string) => {
            if (!value) {
                setUserOptionsForInsert([]);
                return;
            }
            setFetchingUser(true);
            try {
                const res = await axios.get(`/api/user/search?keyword=${encodeURIComponent(value)}`);
                if (res.data.code === 200) {
                    setUserOptionsForInsert(res.data.data);
                }
            } finally {
                setFetchingUser(false);
            }
        };
        return debounce(loadUsers, 500);
    }, []);

    // 插入名片动作
    const onSelectUserToInsert = (userId: string) => {
        const targetUser = userOptionsForInsert.find(u => String(u.userId) === userId);
        if (targetUser) {
            const tag = userCardHtml(targetUser);
            
            editorRef.current?.insert(() => {
                return {
                    targetValue: tag,
                    select: false,
                    deviationStart: 0,
                    deviationEnd: 0
                };
            });
            
            setIsUserModalOpen(false);
            setUserOptionsForInsert([]);
        }
    };

    const uploadProps = {
      name: 'file',
      action: '/api/file/upload',
      data: { uploaderId: user?.userId },
      beforeUpload: (file: any) => {
          const isLt50M = file.size / 1024 / 1024 < 50;
          if (!isLt50M) {
              message.error('文件大小不能超过 50MB!');
              return Upload.LIST_IGNORE;
          }
          return true;
      },
      onChange(info: any) {
          let newFileList = [...info.fileList];
          if (info.file.status === 'done') {
              message.success(`${info.file.name} 上传成功`);
          } else if (info.file.status === 'error') {
              message.error(`${info.file.name} 上传失败`);
          }
          setFileList(newFileList);
      }
    };

    const onFinish = async (values: any) => {
        if (!description) return message.error('请输入课程介绍');
        
        setLoading(true);
        try {
            const validFileIds = fileList
              .filter(f => f.status === 'done' && f.response && f.response.data)
              .map(f => f.response.data);

            const payload = {
                trainId: id,
                name: values.name,
                termId: values.termId,
                teacherIds: values.teacherIds,
                publisherName: values.publisherName,
                description: description,
                maxCount: values.maxCount,
                signupStart: values.signupTime[0].format('YYYY-MM-DD HH:mm:ss'),
                signupEnd: values.signupTime[1].format('YYYY-MM-DD HH:mm:ss'),
                trainStart: values.trainTime[0].format('YYYY-MM-DD HH:mm:ss'),
                trainEnd: values.trainTime[1].format('YYYY-MM-DD HH:mm:ss'),
                attachmentIds: validFileIds,
                notifyParticipants: !!values.notifyParticipants
            };

            const url = id ? '/api/training/update' : '/api/training/publish';
            const res = await axios.post(url, payload);

            if (res.data.code === 200) {
                message.success(id ? '修改成功' : '发布成功');
                navigate('/admin/training');
            } else {
                message.error(res.data.msg);
            }
        } catch (e) {
            message.error('提交失败，请检查网络');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div style={{ maxWidth: 1200, margin: '20px auto', padding: '0 20px' }}>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/admin/training')} style={{ marginBottom: 16 }}>
                返回列表
            </Button>
            
            <Card title={id ? "编辑培训信息" : "发布新培训"}>
                {!id && (
                    <Alert 
                        message="发布提示" 
                        description="指定授课讲师后，系统将自动向这些用户发送邮件通知。"
                        type="info" 
                        showIcon 
                        style={{ marginBottom: 24 }}
                    />
                )}
                
                <Form form={form} layout="vertical" initialValues={{ publisherName: DEFAULT_PUBLISHER_NAME }} onFinish={onFinish}>
                    <Row gutter={24}>
                        <Col span={16}>
                            <Form.Item name="name" label="培训/讲座标题" rules={[{ required: true, message: '请输入标题' }]}>
                                <Input placeholder="例如：2026年春季 ICPC 基础算法集训" size="large" />
                            </Form.Item>
                        </Col>
                        <Col span={8}>
                             <Form.Item name="termId" label="所属学期" rules={[{ required: true, message: '请选择学期' }]}>
                                <Select placeholder="选择学期" size="large">
                                    {terms.map(t => (
                                        <Option key={t.termId} value={t.termId}>{t.termName}</Option>
                                    ))}
                                </Select>
                            </Form.Item>
                        </Col>
                    </Row>

                    <Row gutter={24}>
                        <Col span={12}>
                            <Form.Item name="teacherIds" label="授课讲师（可多选）" rules={[{ required: true, type: 'array', min: 1, message: '请至少指定一名讲师' }]}>
                                <Select
                                    mode="multiple"
                                    showSearch
                                    size="large"
                                    placeholder="输入昵称搜索，可选择多名"
                                    filterOption={false}
                                    onSearch={handleTeacherSearch}
                                    notFoundContent={fetchingTeacher ? <Spin size="small" /> : null}
                                    options={teacherOptions}
                                    suffixIcon={<SearchOutlined />}
                                />
                            </Form.Item>
                        </Col>
                        <Col span={12}>
                            <Form.Item name="publisherName" label="发布者" rules={[{ required: true, whitespace: true, message: '请输入发布者' }, { max: 100, message: '发布者不能超过100字' }]}>
                                <Input placeholder={DEFAULT_PUBLISHER_NAME} size="large" />
                            </Form.Item>
                        </Col>
                    </Row>

                    <Row gutter={24}>
                        <Col span={12}>
                            <Form.Item name="maxCount" label="最大报名人数" rules={[{ required: true }]}>
                                <InputNumber min={1} max={1000} style={{ width: '100%' }} size="large" />
                            </Form.Item>
                        </Col>
                    </Row>

                    <Row gutter={24}>
                        <Col span={12}>
                            <Form.Item name="signupTime" label="报名起止时间" rules={[{ required: true }]}>
                                <RangePicker showTime format="YYYY-MM-DD HH:mm" style={{ width: '100%' }} size="large" />
                            </Form.Item>
                        </Col>
                        <Col span={12}>
                             <Form.Item name="trainTime" label="课程起止时间" rules={[{ required: true }]}>
                                <RangePicker showTime format="YYYY-MM-DD HH:mm" style={{ width: '100%' }} size="large" />
                            </Form.Item>
                        </Col>
                    </Row>
                    {id && (
                        <Form.Item
                            name="notifyParticipants"
                            label="邮件通知"
                            initialValue={false}
                            extra="仅当课程时间或报名时间发生变化时生效；选择“发送”后会邮件通知已报名同学。"
                        >
                            <Select size="large" style={{ maxWidth: 260 }}>
                                <Option value={false}>不发送</Option>
                                <Option value={true}>发送给已报名同学</Option>
                            </Select>
                        </Form.Item>
                    )}

                    {/* 🔴 修改位置：将插入按钮放在附件上传右侧 */}
                    <Form.Item label="附件上传 (课件、资料等)">
                        <div style={{ display: 'flex', gap: '12px' }}>
                            <Upload {...uploadProps} fileList={fileList}>
                                <Button icon={<UploadOutlined />}>点击上传附件</Button>
                            </Upload>
                            <Button icon={<UserAddOutlined />} onClick={() => setIsUserModalOpen(true)}>
                                插入用户名片
                            </Button>
                        </div>
                    </Form.Item>

                    <Divider titlePlacement="left">课程详细介绍</Divider>
                    <Form.Item required>
                        <MdEditor ref={editorRef} modelValue={description} onChange={setDescription} style={{ height: '400px' }} />
                    </Form.Item>

                    <Form.Item>
                        <Button type="primary" htmlType="submit" size="large" loading={loading} block>
                            {id ? "保存修改" : "确认发布"}
                        </Button>
                    </Form.Item>
                </Form>

                {/* 插入用户弹窗 */}
                <Modal title="插入用户名片" open={isUserModalOpen} onCancel={() => setIsUserModalOpen(false)} footer={null} destroyOnClose>
                    <Select
                        showSearch
                        placeholder="输入昵称动态检索..."
                        filterOption={false}
                        onSearch={handleUserInsertSearch}
                        onChange={onSelectUserToInsert}
                        notFoundContent={fetchingUser ? <Spin size="small" /> : null}
                        style={{ width: '100%' }}
                    >
                        {userOptionsForInsert.map(u => (
                            <Select.Option key={u.userId} value={String(u.userId)}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                                    <Avatar size="small" src={u.avatarUrl} />
                                    <span>{userSearchLabel(u)}</span>
                                </div>
                            </Select.Option>
                        ))}
                    </Select>
                </Modal>
            </Card>
        </div>
    );
};

export default TrainingPublish;
