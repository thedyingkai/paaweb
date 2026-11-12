"use client";

import {
  Alert,
  Avatar,
  Button,
  Card,
  Empty,
  Skeleton,
  Space,
  Statistic,
  Tabs,
  Tag,
  Typography,
  Upload,
  message
} from 'antd';
import type { UploadProps } from 'antd';
import {
  BarChartOutlined,
  CameraOutlined,
  EditOutlined,
  ExclamationCircleFilled,
  GlobalOutlined,
  HistoryOutlined,
  TeamOutlined,
  TrophyOutlined,
  UserOutlined
} from '@ant-design/icons';
import axios from 'axios';
import { useRouter } from 'next/navigation';
import { useEffect, useMemo, useState } from 'react';
import SiteHeader from '../../components/SiteHeader';
import EditProfileModal from './EditProfileModal';
import ProfileDataAnalysis from './ProfileDataAnalysis';
import TeamManager from './TeamManager';
import MemberRecordSummary from '../../components/MemberRecordSummary';

const { Title, Paragraph, Text } = Typography;

interface UserProfile {
  userId: string | number;
  userName?: string;
  realName?: string;
  role?: string;
  gender?: number;
  introduce?: string;
  eduBg?: string;
  universityName?: string;
  schoolName?: string;
  majorName?: string;
  avatarUrl?: string;
  backgroundUrl?: string;
  idPhotoUrl?: string;
  idPhotoFileId?: string | number;
  idPhotoUpdatedAt?: string;
  accountStatus?: string;
  newcoderId?: string;
  luoguId?: string;
  codeforcesId?: string;
  atcoderId?: string;
  cwnuojId?: string;
  qojId?: string;
  ucupId?: string;
  rank?: number;
  rating?: number;
  email?: string;
  phone?: string;
  numberId?: string;
  avatarFileId?: string | number;
  universityId?: number;
  schoolId?: number;
  majorId?: number;
  gradeYear?: string;
  className?: string;
  isEnrolledStu?: number;
}

interface DashboardData {
  totalSolved?: number;
  last7DaysSolved?: number;
  last30DaysSolved?: number;
  lastYearSolved?: number;
  platformStats?: { name: string; count: number; color?: string }[];
}

interface RatingHistoryItem {
  contestName: string;
  termName?: string;
  rank: number;
  oldRating: number;
  newRating: number;
  date: string;
  ratingAll?: number;
  ratingContest?: number;
  ratingCertificate?: number;
  ratingPractice?: number;
}

interface ProfilePageClientProps {
  userId: string;
}

const roleMap: Record<string, string> = {
  student: '学生',
  stu_admin: '学生管理员',
  school_admin: '学校管理员',
  department: '部门',
  supper_admin: '超级管理员',
  super_admin: '超级管理员'
};

const eduMap: Record<string, string> = {
  pupil: '校外',
  junior: '校外',
  senior: '校外',
  undergraduate: '本科生',
  postgraduate: '研究生',
  department: '部门',
  'off-campus': '校外'
};

const ProfilePageClient = ({ userId }: ProfilePageClientProps) => {
  const router = useRouter();
  const [user, setUser] = useState<UserProfile | null>(null);
  const [dashboard, setDashboard] = useState<DashboardData | null>(null);
  const [history, setHistory] = useState<RatingHistoryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshKey, setRefreshKey] = useState(0);
  const [editOpen, setEditOpen] = useState(false);

  // Only the authenticated owner's response contains accountStatus.
  const isOwnProfile = Number(user?.userId) === Number(userId) && user?.accountStatus !== undefined;

  const boundAccounts = useMemo(() => {
    if (!user) return [];
    return [
      user.newcoderId && '牛客',
      user.luoguId && '洛谷',
      user.codeforcesId && 'CodeForces',
      user.atcoderId && 'AtCoder',
      user.qojId && 'QOJ',
      user.ucupId && 'UCup',
      user.cwnuojId && '学校 OJ'
    ].filter(Boolean) as string[];
  }, [user]);

  useEffect(() => {
    const fetchProfile = async () => {
      setLoading(true);
      try {
        const [profileRes, dashboardRes, historyRes] = await Promise.allSettled([
          axios.get<{ code: number; data?: UserProfile }>(`/api/user/profile/${userId}`),
          axios.get<{ code: number; data?: DashboardData }>('/api/tracker/dashboard', { params: { userId } }),
          axios.get<{ code: number; data?: RatingHistoryItem[] }>('/api/rating/history', { params: { userId } })
        ]);

        if (profileRes.status === 'fulfilled') setUser(profileRes.value.data.data || null);
        if (dashboardRes.status === 'fulfilled') setDashboard(dashboardRes.value.data.data || null);
        if (historyRes.status === 'fulfilled') setHistory(historyRes.value.data.data || []);
      } finally {
        setLoading(false);
      }
    };

    fetchProfile().catch(() => setLoading(false));
  }, [userId, refreshKey]);

  const handleBgUpload: UploadProps['beforeUpload'] = file => {
    const upload = async () => {
      if (!user) return;
      const formData = new FormData();
      formData.append('file', file);
      formData.append('uploaderId', String(user.userId));
      try {
        message.loading({ content: '正在上传封面...', key: 'bg-upload', duration: 0 });
        const uploadRes = await axios.post<{ code: number; data?: string; msg?: string }>('/api/file/upload', formData);
        if (uploadRes.data.code !== 200 || !uploadRes.data.data) {
          message.error({ content: uploadRes.data.msg || '上传失败', key: 'bg-upload' });
          return;
        }
        const updateRes = await axios.post<{ code: number; msg?: string }>('/api/user/update-bg', {
          backgroundFileId: uploadRes.data.data
        });
        if (updateRes.data.code === 200) {
          message.success({ content: '封面已更新', key: 'bg-upload' });
          setRefreshKey(key => key + 1);
        } else {
          message.error({ content: updateRes.data.msg || '更新失败', key: 'bg-upload' });
        }
      } catch {
        message.error({ content: '网络错误，请稍后重试', key: 'bg-upload' });
      }
    };

    upload();
    return false;
  };

  return (
    <>
      <SiteHeader />
      <main className="profile-shell">
        <Skeleton loading={loading} active avatar paragraph={{ rows: 8 }}>
          {user ? (
            <>
              {isOwnProfile && !user.idPhotoFileId && (
                <Alert
                  className="profile-completion-alert"
                  type="warning"
                  showIcon={false}
                  message={(
                    <div className="profile-completion-content">
                      <ExclamationCircleFilled className="profile-completion-icon" />
                      <div className="profile-completion-title">身份信息待完善</div>
                      <div className="profile-completion-description">请在编辑资料的基本资料页面上传证件照，上传后方可报名比赛。</div>
                      <Button size="small" className="bits-primary-button" onClick={() => setEditOpen(true)}>
                        去完善
                      </Button>
                    </div>
                  )}
                />
              )}

              <section className="profile-cover" style={{ backgroundImage: `url(${user.backgroundUrl || '/paa-background.svg'})` }}>
                {isOwnProfile && (
                  <Upload showUploadList={false} beforeUpload={handleBgUpload}>
                    <Button className="bits-ghost-button cover-upload" icon={<CameraOutlined />}>更换封面</Button>
                  </Upload>
                )}
              </section>

              <section className="profile-content bits-card">
                <div className="profile-identity">
                  <Avatar size={116} src={user.avatarUrl} icon={<UserOutlined />} />
                  <div className="profile-title-block">
                    <Space align="center" wrap style={{ marginBottom: 8 }}>
                      <Title level={2} style={{ margin: 0 }}>{user.userName || (isOwnProfile && user.realName) || '未命名用户'}</Title>
                      {isOwnProfile && user.gender === 1 && <Tag color="blue">男</Tag>}
                      {isOwnProfile && user.gender === 2 && <Tag color="pink">女</Tag>}
                      {isOwnProfile && user.role && <Tag color="processing">{roleMap[user.role] || user.role}</Tag>}
                    </Space>
                    <Paragraph style={{ marginBottom: 12 }}>
                      {isOwnProfile && <Text strong style={{ marginRight: 12 }}>{user.realName || '未实名'}</Text>}
                      <Text type="secondary">{user.introduce || '这个人很低调，暂时还没有留下简介。'}</Text>
                    </Paragraph>
                    {isOwnProfile && (
                      <>
                        <Space className="profile-meta" split={<span className="meta-dot" />} wrap>
                          <Text>{eduMap[user.eduBg || ''] || user.eduBg || '未填写学历'}</Text>
                          <Text>{user.universityName || '未知学校'}</Text>
                          {user.schoolName && <Text>{user.schoolName}</Text>}
                          {user.majorName && <Text>{user.majorName}</Text>}
                        </Space>
                        <div className="platform-row">
                          <GlobalOutlined />
                          {boundAccounts.length ? boundAccounts.map(item => <Tag key={item}>{item}</Tag>) : <Text type="secondary">暂未绑定平台</Text>}
                        </div>
                      </>
                    )}
                  </div>
                  <div className="profile-stats">
                    <Statistic title="全站排名" value={user.rank || '-'} prefix="#" />
                    <Statistic title="Rating 分" value={user.rating || 0} suffix="分" />
                    {isOwnProfile && (
                      <Button className="bits-primary-button" icon={<EditOutlined />} onClick={() => setEditOpen(true)}>
                        编辑资料
                      </Button>
                    )}
                  </div>
                </div>
              </section>

              <section className="profile-tabs bits-card">
                <Tabs
                  defaultActiveKey="analysis"
                  items={[
                    {
                      key: 'analysis',
                      label: <span><BarChartOutlined /> 数据分析</span>,
                      children: <ProfileDataAnalysis loading={loading} dashboard={dashboard} ratingHistory={history} />
                    },
                    {
                      key: 'honors',
                      label: <span><TrophyOutlined /> 荣誉墙</span>,
                      children: <MemberRecordSummary userId={userId} isOwnProfile={isOwnProfile} />
                    },
                    {
                      key: 'experience',
                      label: <span><HistoryOutlined /> 经历</span>,
                      children: <MemberRecordSummary userId={userId} isOwnProfile={isOwnProfile} variant="experience" />
                    },
                    {
                      key: 'team',
                      label: <span><TeamOutlined /> 团队</span>,
                      children: isOwnProfile
                        ? <TeamManager userId={Number(user.userId)} />
                        : <Empty description="团队管理信息仅本人可见" />
                    }
                  ]}
                />
              </section>
              {isOwnProfile && (
                <EditProfileModal
                  open={editOpen}
                  onCancel={() => setEditOpen(false)}
                  onSuccess={() => {
                    setEditOpen(false);
                    setRefreshKey(key => key + 1);
                  }}
                  userData={user}
                />
              )}
            </>
          ) : (
            <Card className="bits-card detail-card" variant="borderless">
              <div className="bits-empty">
                用户不存在或暂时无法访问
                <Button className="bits-primary-button" onClick={() => router.push('/')}>返回首页</Button>
              </div>
            </Card>
          )}
        </Skeleton>
      </main>
    </>
  );
};

export default ProfilePageClient;
