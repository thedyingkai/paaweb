"use client";

/* eslint-disable react-hooks/set-state-in-effect */

import {
  Alert,
  Avatar,
  Button,
  Card,
  Col,
  DatePicker,
  Divider,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Skeleton,
  Upload,
  message
} from 'antd';
import type { UploadFile } from 'antd/es/upload/interface';
import { ArrowLeftOutlined, SearchOutlined, UploadOutlined, UserAddOutlined } from '@ant-design/icons';
import axios from 'axios';
import { userCardHtml, userSearchLabel, type UserSearchResult } from '@/types/user';
import dayjs from 'dayjs';
import { useParams, useRouter } from 'next/navigation';
import { useEffect, useMemo, useState } from 'react';
import SiteHeader from '../../components/SiteHeader';
import MarkdownContent from '../../components/content/MarkdownContent';
import { readStoredUser } from '@/utils/auth';

const { RangePicker } = DatePicker;
const DEFAULT_PUBLISHER_NAME = 'ACM 校队';

interface TermItem {
  termId: number;
  termName: string;
  startTime?: string;
}

type SearchUser = UserSearchResult;

interface TrainingTeacher {
  teacherId: number;
  teacherName: string;
  teacherAccount?: string;
}

const TrainingPublishPage = () => {
  const router = useRouter();
  const params = useParams<{ id?: string }>();
  const id = params?.id;
  const user = useMemo(() => readStoredUser(), []);
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [description, setDescription] = useState('');
  const [terms, setTerms] = useState<TermItem[]>([]);
  const [teacherOptions, setTeacherOptions] = useState<{ value: number; label: string }[]>([]);
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [userModalOpen, setUserModalOpen] = useState(false);
  const [insertUsers, setInsertUsers] = useState<SearchUser[]>([]);

  const adminRoles = useMemo(() => ['stu_admin', 'department', 'super_admin', 'supper_admin', 'admin'], []);

  useEffect(() => {
    if (!user || !adminRoles.includes(String(user.role || ''))) {
      message.error('无权访问此页面');
      router.push('/');
      return;
    }

    axios.get('/api/common/terms').then(res => {
      const termList = [...(res.data.data || [])].sort((a, b) => dayjs(b.startTime).valueOf() - dayjs(a.startTime).valueOf());
      setTerms(termList);
    });

    if (id) {
      setLoading(true);
      axios.get(`/api/training/detail/${id}`).then(res => {
        const data = res.data.data;
        if (!data) return;
        const teachers: TrainingTeacher[] = data.teachers?.length
          ? data.teachers
          : data.teacherId
            ? [{ teacherId: data.teacherId, teacherName: data.teacherName, teacherAccount: data.teacherAccount }]
            : [];
        form.setFieldsValue({
          name: data.name,
          termId: data.termId,
          teacherIds: teachers.map(item => item.teacherId),
          publisherName: data.publisherName || DEFAULT_PUBLISHER_NAME,
          maxCount: data.maxCount,
          signupTime: [dayjs(data.signupStart), dayjs(data.signupEnd)],
          trainTime: [dayjs(data.trainStart), dayjs(data.trainEnd)]
        });
        setDescription(data.description || '');
        setTeacherOptions(teachers.map(item => ({ value: item.teacherId, label: `${item.teacherName} (${item.teacherAccount || '讲师'})` })));
        setFileList((data.attachments || []).map((file: { fileId: string | number; name: string }) => ({ uid: String(file.fileId), name: file.name, status: 'done', response: { data: file.fileId } } as UploadFile)));
      }).finally(() => setLoading(false));
    }
  }, [adminRoles, form, id, router, user]);

  const searchUsers = async (keyword: string, target: 'lecturer' | 'insert') => {
    if (!keyword) return;
    const res = await axios.get('/api/user/search', { params: { keyword } });
    const users = res.data.data || [];
    if (target === 'lecturer') {
      const nextOptions = users.map((item: SearchUser) => ({ value: item.userId, label: userSearchLabel(item) }));
      setTeacherOptions(current => {
        const merged = new Map(current.map(item => [item.value, item]));
        nextOptions.forEach((item: { value: number; label: string }) => merged.set(item.value, item));
        return Array.from(merged.values());
      });
    } else {
      setInsertUsers(users);
    }
  };

  const insertUserCard = (target: SearchUser) => {
    const tag = userCardHtml(target);
    setDescription(value => `${value}${tag}`);
    setUserModalOpen(false);
  };

  const onFinish = async (values: Record<string, unknown>) => {
    if (!description.trim()) {
      message.error('请输入课程介绍');
      return;
    }
    setLoading(true);
    try {
      const signupTime = values.signupTime as dayjs.Dayjs[];
      const trainTime = values.trainTime as dayjs.Dayjs[];
      const payload = {
        trainId: id,
        name: values.name,
        termId: values.termId,
        teacherIds: values.teacherIds,
        publisherName: values.publisherName,
        description,
        maxCount: values.maxCount,
        signupStart: signupTime[0].format('YYYY-MM-DD HH:mm:ss'),
        signupEnd: signupTime[1].format('YYYY-MM-DD HH:mm:ss'),
        trainStart: trainTime[0].format('YYYY-MM-DD HH:mm:ss'),
        trainEnd: trainTime[1].format('YYYY-MM-DD HH:mm:ss'),
        attachmentIds: fileList.filter(file => file.status === 'done' && file.response?.data).map(file => file.response?.data)
      };
      const res = await axios.post(id ? '/api/training/update' : '/api/training/publish', payload);
      if (res.data.code === 200) {
        message.success(id ? '修改成功' : '发布成功');
        router.push('/training');
      } else message.error(res.data.msg || '提交失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <SiteHeader />
      <main className="content-page-shell">
        <section className="activity-content">
          <Button className="bits-ghost-button" icon={<ArrowLeftOutlined />} onClick={() => router.push('/training')}>返回培训列表</Button>
          <Card className="bits-card activity-readable-card" variant="borderless">
            <div className="activity-detail-heading"><h2>{id ? '编辑培训信息' : '发布新培训'}</h2></div>
            {!id && <Alert type="info" showIcon message="发布提示" description="指定授课讲师后，系统将自动向该用户发送邮件通知。" />}
            <Skeleton loading={loading} active paragraph={{ rows: 8 }}>
              <Form form={form} layout="vertical" initialValues={{ publisherName: DEFAULT_PUBLISHER_NAME }} onFinish={onFinish}>
                <Row gutter={18}>
                  <Col xs={24} md={16}><Form.Item name="name" label="培训/讲座标题" rules={[{ required: true }]}><Input className="bits-input" /></Form.Item></Col>
                  <Col xs={24} md={8}><Form.Item name="termId" label="所属学期" rules={[{ required: true }]}><Select className="bits-select" options={terms.map(term => ({ value: term.termId, label: term.termName }))} /></Form.Item></Col>
                  <Col xs={24} md={12}><Form.Item name="teacherIds" label="授课讲师（可多选）" rules={[{ required: true, type: 'array', min: 1, message: '请至少指定一名讲师' }]}><Select mode="multiple" showSearch filterOption={false} onSearch={value => searchUsers(value, 'lecturer')} suffixIcon={<SearchOutlined />} options={teacherOptions} placeholder="输入昵称搜索，可选择多名" /></Form.Item></Col>
                  <Col xs={24} md={12}><Form.Item name="publisherName" label="发布者" rules={[{ required: true, whitespace: true, message: '请输入发布者' }, { max: 100, message: '发布者不能超过100字' }]}><Input className="bits-input" placeholder={DEFAULT_PUBLISHER_NAME} /></Form.Item></Col>
                  <Col xs={24} md={12}><Form.Item name="maxCount" label="最大报名人数" rules={[{ required: true }]}><InputNumber min={1} max={1000} style={{ width: '100%' }} /></Form.Item></Col>
                  <Col xs={24} md={12}><Form.Item name="signupTime" label="报名起止时间" rules={[{ required: true }]}><RangePicker showTime format="YYYY-MM-DD HH:mm" style={{ width: '100%' }} /></Form.Item></Col>
                  <Col xs={24} md={12}><Form.Item name="trainTime" label="课程起止时间" rules={[{ required: true }]}><RangePicker showTime format="YYYY-MM-DD HH:mm" style={{ width: '100%' }} /></Form.Item></Col>
                </Row>
                <Form.Item label="附件与内容工具">
                  <div className="publish-tool-row">
                    <Upload
                      name="file"
                      action="/api/file/upload"
                      data={{ uploaderId: user?.userId }}
                      fileList={fileList}
                      onChange={info => setFileList(info.fileList as UploadFile[])}
                    >
                      <Button icon={<UploadOutlined />}>上传附件</Button>
                    </Upload>
                    <Button icon={<UserAddOutlined />} onClick={() => setUserModalOpen(true)}>插入用户名片</Button>
                  </div>
                </Form.Item>
                <Divider>课程详细介绍</Divider>
                <Row gutter={18}>
                  <Col xs={24} lg={12}><Input.TextArea className="bits-input markdown-editor-textarea" value={description} onChange={event => setDescription(event.target.value)} placeholder="支持 Markdown / HTML 用户名片" /></Col>
                  <Col xs={24} lg={12}><div className="markdown-preview-panel"><MarkdownContent content={description} /></div></Col>
                </Row>
                <Button className="bits-primary-button auth-submit" htmlType="submit" block loading={loading}>{id ? '保存修改' : '确认发布'}</Button>
              </Form>
            </Skeleton>
          </Card>
        </section>
      </main>
      <Modal title="插入用户名片" open={userModalOpen} onCancel={() => setUserModalOpen(false)} footer={null}>
        <Select showSearch filterOption={false} onSearch={value => searchUsers(value, 'insert')} placeholder="输入昵称动态检索" style={{ width: '100%' }}>
          {insertUsers.map(item => <Select.Option key={item.userId} value={item.userId} onClick={() => insertUserCard(item)}><Avatar size="small" src={item.avatarUrl} /> {userSearchLabel(item)}</Select.Option>)}
        </Select>
      </Modal>
    </>
  );
};

export default TrainingPublishPage;
