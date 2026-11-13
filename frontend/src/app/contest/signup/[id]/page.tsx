"use client";

/* eslint-disable react-hooks/set-state-in-effect */

import {
  Alert,
  Button,
  Card,
  Descriptions,
  Divider,
  Image,
  Form,
  Modal,
  Popconfirm,
  Result,
  Select,
  Skeleton,
  Space,
  Switch,
  Tag,
  Typography,
  message
} from 'antd';
import {
  ArrowLeftOutlined,
  DownloadOutlined,
  PaperClipOutlined,
  SmileOutlined,
  TeamOutlined,
  TrophyOutlined
} from '@ant-design/icons';
import axios from 'axios';
import dayjs from 'dayjs';
import { eventSignupPeriod, eventSignupCount } from '@/utils/eventSignup';
import { useParams, useRouter } from 'next/navigation';
import { useEffect, useMemo, useState } from 'react';
import AdvisorInput, { advisorText } from '@/app/components/AdvisorInput';
import { readStoredUser } from '@/utils/auth';
import { contestTypeText } from '@/utils/contestDisplay';
import ActivityLayout from '../../ContestNav';
import MarkdownContent from '../../../components/content/MarkdownContent';
import SignupIdPhoto from './SignupIdPhoto';

const { Title } = Typography;

const asText = (value: unknown, fallback = '-') => {
  if (Array.isArray(value)) return value.filter(Boolean).join('、') || fallback;
  if (value === null || value === undefined || value === '') return fallback;
  return String(value);
};

const signupTypeText = (value: unknown) => {
  if (value === 'team') return '团队赛';
  if (value === 'personal') return '个人赛';
  return asText(value);
};

const groupText = (value: unknown, isTeam?: boolean) => {
  const text = asText(value, '');
  if (!text) return '-';
  const map: Record<string, string> = {
    official: isTeam ? '正式队伍' : '正式参赛',
    star: isTeam ? '打星队伍' : '打星参赛',
    girl: '女生队伍',
    '0': '否',
    '1': '是',
  };
  return map[text] || text;
};

const previewMembers = (previewData: Record<string, unknown> | null) => {
  const members = previewData?.members;
  return Array.isArray(members) ? members as Record<string, unknown>[] : [];
};

interface EventDetail {
  signupMode?: string;
  eventId: number;
  name: string;
  type?: string;
  isTeam?: number;
  termName?: string;
  categoryName?: string;
  signupStart: string;
  signupEnd: string | null;
  eventStart: string;
  eventEnd: string;
  signupCount: number;
  maxSignup: number | null;
  minMember?: number;
  maxMember?: number;
  description?: string;
  attachments?: { fileId: string; name: string }[];
  admissionTicketOpen?: boolean;
}

export default function SignupDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const user = readStoredUser();
  const [form] = Form.useForm();
  const [data, setData] = useState<EventDetail | null>(null);
  const [signupInfo, setSignupInfo] = useState<Record<string, unknown> | null>(null);
  const [myTeams, setMyTeams] = useState<Record<string, unknown>[]>([]);
  const [signupOpen, setSignupOpen] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewData, setPreviewData] = useState<Record<string, unknown> | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [now, setNow] = useState(() => dayjs());

  useEffect(() => {
    const timer = window.setInterval(() => setNow(dayjs()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  const refresh = async () => {
    const res = await axios.get(`/api/event/detail/${id}`);
    const detail = res.data.data || null;
    setData(detail);
    if (user && detail) {
      const signupRes = await axios.get('/api/event/my-signup-detail', { params: { eventId: detail.eventId } }).catch(() => null);
      setSignupInfo(signupRes?.data?.data || null);
    }
  };

  useEffect(() => {
    refresh().finally(() => setLoading(false));
  }, [id, user?.userId]);

  const status = useMemo(() => {
    if (!data) return null;
    if (data.signupMode === 'roster') return <Tag color="blue">{now.isBefore(data.eventStart) ? '名单已确定' : now.isBefore(data.eventEnd) ? '正在比赛中' : '比赛已结束'}</Tag>;
    if (now.isBefore(data.signupStart)) return <Tag color="orange">报名未开始</Tag>;
    if (!data.signupEnd || now.isBefore(data.signupEnd)) return <Tag color="green">报名进行中</Tag>;
    if (now.isBefore(data.eventStart)) return <Tag color="blue">等待开赛</Tag>;
    if (now.isBefore(data.eventEnd)) return <Tag color="red">正在比赛中</Tag>;
    return <Tag>已结束</Tag>;
  }, [data, now]);

  const openSignup = async () => {
    if (!user) return router.push('/login');
    if (user.accountStatus !== 'normal') return message.error('账号状态异常，未进行认证或受限');
    form.setFieldsValue({ isRanking: true, xcpcType: 'official' });
    if (data?.isTeam === 1) {
      const res = await axios.get('/api/team/my-teams');
      const teams = (res.data.data || []).filter((team: Record<string, unknown>) => Number(team.leaderId) === Number(user.userId));
      if (!teams.length) return Modal.warning({ title: '需要创建队伍', content: '本场比赛为团队赛，您需要作为队长创建一个队伍才能报名。' });
      setMyTeams(teams);
    }
    setSignupOpen(true);
  };

  const buildPayload = async () => {
    const values = await form.validateFields();
    let teamType = values.xcpcType || 'official';
    let isRanking = values.isRanking ? 1 : 0;
    if (data?.type === 'GPLT') teamType = values.gpltGroup;
    if (data?.type === 'LANQIAO') teamType = values.lanqiaoGroup;
    if (!['GPLT', 'LANQIAO'].includes(data?.type || '')) isRanking = ['official', 'girl'].includes(teamType) ? 1 : 0;
    return { eventId: data?.eventId, userId: user?.userId, teamId: values.teamId, advisor: advisorText(values.advisor), isRanking, teamType };
  };

  const previewSignup = async () => {
    setSubmitting(true);
    try {
      const payload = await buildPayload();
      const res = await axios.post('/api/event/signup-preview', payload);
      if (res.data.code === 200) {
        setPreviewData(res.data.data);
        setPreviewOpen(true);
      } else message.error(res.data.msg || '预览失败');
    } finally {
      setSubmitting(false);
    }
  };

  const confirmSignup = async () => {
    setSubmitting(true);
    try {
      const payload = await buildPayload();
      const res = await axios.post('/api/event/signup', payload);
      if (res.data.code === 200) {
        message.success('报名成功');
        setSignupOpen(false);
        setPreviewOpen(false);
        await refresh();
      } else message.error(res.data.msg || '报名失败');
    } finally {
      setSubmitting(false);
    }
  };

  const cancelSignup = async () => {
    if (!data || !user) return;
    if (data.signupEnd && !dayjs().isBefore(data.signupEnd)) {
      setNow(dayjs());
      return message.warning('报名已结束，无法取消报名');
    }
    const res = await axios.post('/api/event/cancel', { eventId: data.eventId, userId: user.userId });
    if (res.data.code === 200) {
      message.success('已取消报名');
      setSignupInfo(null);
      await refresh();
    } else message.error(res.data.msg || '取消失败');
  };

  const canSignup = data && data.signupMode !== 'roster' && !now.isBefore(data.signupStart) && (!data.signupEnd || now.isBefore(data.signupEnd)) && (data.maxSignup == null || data.signupCount < data.maxSignup);
  const signupEnded = !!data?.signupEnd && !now.isBefore(data.signupEnd);
  const eventEnded = data ? !now.isBefore(data.eventEnd) : false;
  const signupStatus = String(signupInfo?.status || 'none');
  const signed = signupStatus === 'approved' || signupStatus === 'pending';
  const myMemberInfo = previewMembers(signupInfo).find(member => String(member.userId) === String(user?.userId));
  const signupButtonText = data
    ? signupEnded
      ? '已结束'
      : data.maxSignup != null && data.signupCount >= data.maxSignup
        ? '名额已满'
        : '立即报名'
    : '立即报名';
  const credentialList = Array.isArray(signupInfo?.memberCredentials) ? signupInfo.memberCredentials : [];
  const hasAdmissionTicketInfo = Boolean(data?.admissionTicketOpen) && !eventEnded && signupStatus === 'approved' && (
    Boolean(signupInfo?.contestAccount && signupInfo?.contestPassword && signupInfo?.seatNumber)
    || credentialList.some((item) => {
      const credential = item as Record<string, unknown>;
      return Boolean(credential.contestAccount && credential.contestPassword && credential.seatNumber);
    })
  );

  const downloadAdmissionTicket = async () => {
    if (!data) return;
    try {
      const response = await axios.get('/api/event/admission-ticket', {
        params: { eventId: data.eventId },
        responseType: 'blob',
      });
      const url = window.URL.createObjectURL(response.data);
      const link = document.createElement('a');
      link.href = url;
      link.download = `${data.name}_参赛证明.pdf`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.setTimeout(() => window.URL.revokeObjectURL(url), 30_000);
    } catch (error: unknown) {
      const blob = (error as { response?: { data?: Blob } })?.response?.data;
      const text = blob instanceof Blob ? await blob.text() : '';
      message.error(text || '参赛证明下载失败');
    }
  };

  return (
    <ActivityLayout>
      <section className="activity-content">
        <Button className="bits-ghost-button" icon={<ArrowLeftOutlined />} onClick={() => router.push('/contest/signup')} style={{ marginBottom: 16 }}>返回赛事报名</Button>
        <Skeleton loading={loading} active paragraph={{ rows: 8 }}>
          {data && (
            <Card className="content-main-card intro-content-card" variant="borderless" style={{ width: 'min(980px, 100%)', margin: '0 auto' }}>
              <div className="activity-detail-heading" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16, flexWrap: 'wrap', gap: 12 }}>
                <Title level={2} style={{ margin: 0 }}>{data.name}</Title>
                <Space><Button className="bits-ghost-button" icon={<TeamOutlined />} onClick={() => router.push(`/contest/signup/${id}/list`)}>报名名单公示</Button>{status}</Space>
              </div>
              <Space wrap className="activity-meta-row" style={{ marginBottom: 20 }}>
                <Tag color={data.isTeam ? 'purple' : 'blue'}>{data.isTeam ? '团队赛' : '个人赛'}</Tag>
                {data.type && <Tag color="cyan">{contestTypeText(data.type)}</Tag>}
                <span><TrophyOutlined /> 分类：{data.categoryName || '校内赛'}</span>
                <span><TeamOutlined /> 报名：{eventSignupCount(data.signupCount,data.maxSignup)}</span>
              </Space>
              
              <Divider style={{ margin: '0 0 24px 0' }} />
              
              <Descriptions bordered column={1} style={{ marginBottom: 24 }}>
                <Descriptions.Item label="参赛方式">{data.signupMode === 'roster' ? '按确定名单导入，不开放自主报名' : eventSignupPeriod(data.signupStart, data.signupEnd)}</Descriptions.Item>
                <Descriptions.Item label="比赛时间">{dayjs(data.eventStart).format('YYYY-MM-DD HH:mm')} 至 {dayjs(data.eventEnd).format('YYYY-MM-DD HH:mm')}</Descriptions.Item>
                {data.isTeam === 1 && <Descriptions.Item label="队伍人数限制">{data.minMember} - {data.maxMember} 人</Descriptions.Item>}
              </Descriptions>
              
              <div className="center-readable">
                <Title level={4} style={{ marginTop: 24, marginBottom: 16 }}>赛事详情</Title>
                <MarkdownContent content={data.description} />
              </div>
              
              {!!data.attachments?.length && (
                <section className="attachment-panel" style={{ marginTop: 24 }}>
                  <strong><PaperClipOutlined /> 附件</strong>
                  {data.attachments.map(file => <Button key={file.fileId} type="link" icon={<DownloadOutlined />} href={`/api/file/download/${file.fileId}`} target="_blank" rel="noopener noreferrer">{file.name}</Button>)}
                </section>
              )}
              
              <Divider style={{ margin: '24px 0' }} />
              
              <div className="signup-action-zone">
                {!user ? <Button className="bits-primary-button" onClick={() => router.push('/login')}>{data.signupMode === 'roster' ? '登录查看本人参赛信息' : '请登录后报名'}</Button> : signed ? (
                  <Result
                    status="success"
                    icon={<SmileOutlined />}
                    title={signupStatus === 'approved' ? (data.signupMode === 'roster' ? '您已在确定参赛名单中' : '您已成功报名') : '报名审核中'}
                    subTitle={<Space wrap>{signupInfo?.seatNumber ? <span>座位：{String(signupInfo.seatNumber)}</span> : null}{signupInfo?.teamTypeDisplay ? <Tag>{groupText(signupInfo.teamTypeDisplay)}</Tag> : null}{signupInfo?.teamName ? <Tag>{String(signupInfo.teamName)}</Tag> : null}{signupInfo?.advisor ? <span>指导老师：{String(signupInfo.advisor)}</span> : null}</Space>}
                    extra={
                      <Space wrap>
                        {hasAdmissionTicketInfo && <Button type="primary" icon={<DownloadOutlined />} onClick={downloadAdmissionTicket}>下载参赛证明 PDF</Button>}
                        {data.signupMode !== 'roster' && (signupEnded ? <Button disabled>报名已结束</Button> : <Popconfirm title="确定取消报名？" onConfirm={cancelSignup}><Button danger>取消报名</Button></Popconfirm>)}
                      </Space>
                    }
                  />
                ) : (
                  <>
                    {data.signupMode === 'roster' ? <Alert showIcon type="info" message={user.accountStatus === 'normal' ? '当前实名身份未匹配到确定参赛名单' : '请先完成实名核验以关联参赛名单'} description="名单由赛事管理员统一导入，名单外不能报名。如信息有误，请联系赛事管理员。" /> : <>{signupStatus === 'rejected' && <Alert type="error" showIcon message="报名已拒绝，可重新提交报名。" />}
                    <Button className="bits-primary-button" disabled={!canSignup} onClick={openSignup}>{signupButtonText}</Button></>}
                  </>
                )}
              </div>
              {user?.userId && signed && (
                <SignupIdPhoto
                  key={user.userId}
                  userId={user.userId}
                  photoUrl={myMemberInfo?.idPhotoUrl ? String(myMemberInfo.idPhotoUrl) : undefined}
                  onUploaded={refresh}
                />
              )}
            </Card>
          )}
        </Skeleton>
      </section>
      <Modal title="填写报名信息" open={signupOpen} onCancel={() => setSignupOpen(false)} onOk={previewSignup} confirmLoading={submitting} okText="预览报名">
        <Form form={form} layout="vertical">
          {data?.isTeam === 1 && <Form.Item name="teamId" label="参赛队伍" rules={[{ required: true }]}><Select options={myTeams.map(team => ({ value: team.teamId as number, label: String(team.teamName) }))} /></Form.Item>}
          {data?.type === 'GPLT' ? <Form.Item name="gpltGroup" label="参赛组别" rules={[{ required: true }]}><Select options={['星芒初绽', '长风破浪', '凌霄问鼎'].map(value => ({ value, label: value }))} /></Form.Item> : data?.type === 'LANQIAO' ? <Form.Item name="lanqiaoGroup" label="参赛组别" rules={[{ required: true }]}><Select options={['C/C++程序设计', 'Python程序设计', 'Java软件开发'].map(value => ({ value, label: value }))} /></Form.Item> : <Form.Item name="xcpcType" label="参赛类型" rules={[{ required: true }]}><Select options={[{ value: 'official', label: '正式参赛' }, { value: 'girl', label: '女生队伍' }, { value: 'star', label: '打星参赛' }]} /></Form.Item>}
          <Form.Item name="advisor" label="指导老师"><AdvisorInput /></Form.Item>
          <Form.Item name="isRanking" label="参与排名" valuePropName="checked"><Switch /></Form.Item>
        </Form>
      </Modal>
      <Modal title="报名信息预览" open={previewOpen} onCancel={() => setPreviewOpen(false)} onOk={confirmSignup} confirmLoading={submitting} okText="确认提交">
        {previewData && (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Descriptions bordered size="small" column={1}>
              <Descriptions.Item label="报名类型">{signupTypeText(previewData.signupType)}</Descriptions.Item>
              {previewData.teamName ? <Descriptions.Item label="参赛队伍">{asText(previewData.teamName)}</Descriptions.Item> : null}
              <Descriptions.Item label="参赛组别">{groupText(previewData.teamTypeDisplay ?? previewData.teamType, previewData.signupType === 'team')}</Descriptions.Item>
              <Descriptions.Item label="参与排名">{Number(previewData.isRanking) === 0 ? '否' : '是'}</Descriptions.Item>
              <Descriptions.Item label="指导老师">{asText(previewData.advisor)}</Descriptions.Item>
            </Descriptions>

            <div>
              <Typography.Text strong>参赛成员</Typography.Text>
              <Space direction="vertical" size={8} style={{ width: '100%', marginTop: 8 }}>
                {previewMembers(previewData).map((member, index) => (
                  <Card key={`${asText(member.numberId, String(index))}-${index}`} size="small" variant="borderless" className="signup-preview-member-card">
                    <div className="signup-preview-member-row">
                      <div className="signup-preview-member-info">
                        <Space wrap>
                          <Typography.Text strong>{asText(member.realName)}</Typography.Text>
                          {member.isCaptain ? <Tag color="blue">队长</Tag> : null}
                        </Space>
                        <span>学校：{asText(member.universityName)}</span>
                        <span>学院：{asText(member.schoolName)}</span>
                        <span>专业：{asText(member.majorName)}</span>
                        <span>学号：{asText(member.numberId)}</span>
                      </div>
                      <div className="signup-preview-id-photo">
                        {member.idPhotoUrl ? (
                          <Image src={String(member.idPhotoUrl)} alt={`${asText(member.realName)}证件照`} width={78} height={104} style={{ objectFit: 'cover' }} />
                        ) : (
                          <span>未上传</span>
                        )}
                      </div>
                    </div>
                  </Card>
                ))}
                {previewMembers(previewData).length === 0 && <Typography.Text type="secondary">暂无成员信息</Typography.Text>}
              </Space>
            </div>
          </Space>
        )}
      </Modal>
    </ActivityLayout>
  );
}
