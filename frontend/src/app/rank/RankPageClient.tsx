"use client";

import {
  Alert,
  Avatar,
  Button,
  Card,
  Col,
  Empty,
  Row,
  Select,
  Skeleton,
  Space,
  Statistic,
  Table,
  Tag,
  Tooltip,
  Typography
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  CrownOutlined,
  FieldTimeOutlined,
  FireOutlined,
  RiseOutlined,
  TrophyOutlined,
  UserOutlined
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { useRouter } from 'next/navigation';
import { useEffect, useMemo, useState } from 'react';
import { readStoredUser } from '@/utils/auth';
import request from '@/utils/request';
import SiteHeader from '../components/SiteHeader';
import PageLoader from '../components/PageLoader';

const { Title, Paragraph, Text } = Typography;

interface LeaderboardItem {
  rank: number;
  userId: number;
  nickname: string;
  realName?: string;
  avatar?: string | null;
  bio?: string;
  role?: string;
  ratingAll: number;
  ratingContest: number;
  ratingCertificate: number;
  ratingPractice: number;
}

interface Term {
  termId: number;
  termName: string;
  startTime: string;
  endTime: string | null;
}

const roleMap: Record<string, string> = {
  supper_admin: '超级管理员',
  super_admin: '超级管理员',
  stu_admin: '学生管理员',
  school_admin: '学校管理员',
  department: '部门'
};

const normalizeAvatar = (url?: string | null) => url?.replace('/file/preview/', '/file/download/');

const RankPageClient = () => {
  const router = useRouter();
  const [data, setData] = useState<LeaderboardItem[]>([]);
  const [terms, setTerms] = useState<Term[]>([]);
  const [activeTermId, setActiveTermId] = useState<number | null>(null);
  const [selectedTerm, setSelectedTerm] = useState<number | 'current'>('current');
  const [loading, setLoading] = useState(true);
  const [termsLoading, setTermsLoading] = useState(true);
  const [termsError, setTermsError] = useState(false);
  const [boardError, setBoardError] = useState(false);
  const [termsRetry, setTermsRetry] = useState(0);
  const [boardRetry, setBoardRetry] = useState(0);

  const currentUserId = readStoredUser()?.userId;
  const myRankInfo = useMemo(
    () => data.find(item => Number(item.userId) === Number(currentUserId)),
    [currentUserId, data]
  );

  useEffect(() => {
    const controller = new AbortController();
    const fetchTerms = async () => {
      setTermsLoading(true);
      setTermsError(false);
      try {
        const res = await request.get<{ code: number; data: Term[] }>('/rating/terms', {
          signal: controller.signal,
          timeout: 15000
        });
        if (res.data.code !== 200 || !Array.isArray(res.data.data)) throw new Error('Invalid terms response');
        if (controller.signal.aborted) return;
        const termList = [...res.data.data].sort(
          (a, b) => dayjs(b.startTime).valueOf() - dayjs(a.startTime).valueOf()
        );
        setTerms(termList);

        const now = dayjs();
        const current = termList.find(term => {
          const start = dayjs(term.startTime);
          const end = term.endTime ? dayjs(term.endTime).endOf('day') : null;
          return !start.isAfter(now) && (!end || !end.isBefore(now));
        });
        setActiveTermId(current?.termId ?? null);
      } catch {
        if (!controller.signal.aborted) setTermsError(true);
      } finally {
        if (!controller.signal.aborted) setTermsLoading(false);
      }
    };

    void fetchTerms();
    return () => controller.abort();
  }, [termsRetry]);

  useEffect(() => {
    const controller = new AbortController();
    const fetchLeaderboard = async () => {
      setLoading(true);
      setBoardError(false);
      setData([]);
      try {
        const res = await request.get<{ code: number; data: LeaderboardItem[] }>('/rating/leaderboard', {
          params: selectedTerm === 'current' ? {} : { termId: selectedTerm },
          signal: controller.signal,
          timeout: 15000
        });
        if (res.data.code !== 200 || !Array.isArray(res.data.data)) throw new Error('Invalid leaderboard response');
        if (!controller.signal.aborted) setData(res.data.data);
      } catch {
        if (!controller.signal.aborted) setBoardError(true);
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    };

    void fetchLeaderboard();
    return () => controller.abort();
  }, [selectedTerm, boardRetry]);

  const renderRole = (role?: string) => {
    if (!role || !roleMap[role]) return null;
    return <Tag color={role.includes('admin') ? 'gold' : 'processing'}>{roleMap[role]}</Tag>;
  };

  const renderRank = (rank: number) => {
    if (rank === 1) return <CrownOutlined className="rank-icon rank-first" />;
    if (rank === 2) return <TrophyOutlined className="rank-icon rank-second" />;
    if (rank === 3) return <TrophyOutlined className="rank-icon rank-third" />;
    return <span className="rank-number">{rank}</span>;
  };

  const columns: ColumnsType<LeaderboardItem> = [
    {
      title: '排名',
      dataIndex: 'rank',
      width: 86,
      align: 'center',
      render: (rank: number) => renderRank(rank)
    },
    {
      title: '用户',
      width: 280,
      render: (_, record) => (
        <button className="rank-user" type="button" onClick={() => router.push(`/profile/${record.userId}`)}>
          <Avatar src={normalizeAvatar(record.avatar)} icon={<UserOutlined />} size={42} />
          <span>
            <strong>
              {record.nickname || '未命名用户'}
              {record.rank <= 3 && <FireOutlined />}
            </strong>
            <em>{record.realName || '未实名'} {renderRole(record.role)}</em>
          </span>
        </button>
      )
    },
    {
      title: <Tooltip title="总分 = 比赛分 + 奖项分 + 刷题分">总排位分</Tooltip>,
      dataIndex: 'ratingAll',
      align: 'center',
      sorter: (a, b) => a.ratingAll - b.ratingAll,
      defaultSortOrder: 'descend',
      render: (value: number) => <strong className="score-main">{value}</strong>
    },
    {
      title: '比赛',
      dataIndex: 'ratingContest',
      align: 'center',
      render: (value: number) => value || '-'
    },
    {
      title: '奖项',
      dataIndex: 'ratingCertificate',
      align: 'center',
      render: (value: number) => value || '-'
    },
    {
      title: '刷题',
      dataIndex: 'ratingPractice',
      align: 'center',
      render: (value: number) => value || '-'
    },
    {
      title: '简介',
      dataIndex: 'bio',
      ellipsis: true,
      render: (value?: string) => value || <Text type="secondary">-</Text>
    }
  ];

  return (
    <>
      <PageLoader loading={loading} />
      <SiteHeader />
      <main className="content-page-shell">
        <section className="content-hero">
          <Tag className="bits-pill">RATING BOARD</Tag>
          <Title className="content-hero-title">全站积分排行榜</Title>
          <Paragraph>汇聚校内算法竞赛成长记录，见证每一次比赛、获奖与刷题积累。</Paragraph>
        </section>

        <div className="rank-toolbar" style={{ width: 'min(1120px, 100%)', margin: '0 auto 24px', padding: '0 10px' }}>
          <Space>
            <FieldTimeOutlined />
            <Select
              aria-label="积分统计范围"
              value={selectedTerm}
              onChange={value => setSelectedTerm(value)}
              loading={termsLoading}
              options={[{ value: 'current', label: '当前积分' }, ...terms.map(term => ({
                value: term.termId,
                label: `${term.termName}${term.termId === activeTermId ? ' · 进行中' : ''}`
              }))]}
              style={{ width: 250 }}
            />
          </Space>
        </div>

        {termsError ? (
          <Alert
            type="warning"
            showIcon
            title="学期列表加载失败，仍可查看当前积分。"
            action={<Button size="small" onClick={() => setTermsRetry(value => value + 1)}>重试学期列表</Button>}
            style={{ width: 'min(1120px, 100%)', margin: '0 auto 24px' }}
          />
        ) : !termsLoading && terms.length === 0 && (
          <Alert type="info" showIcon title="尚未设置学期，当前积分按全部历史记录累计。" style={{ width: 'min(1120px, 100%)', margin: '0 auto 24px' }} />
        )}

        {myRankInfo && (
          <Card className="bits-card my-rank-card" variant="borderless" style={{ width: 'min(1120px, 100%)', margin: '0 auto 24px' }}>
            <Row gutter={[24, 18]} align="middle">
              <Col xs={24} md={8}>
                <button className="rank-user rank-user-large" type="button" onClick={() => router.push(`/profile/${myRankInfo.userId}`)}>
                  <Avatar src={normalizeAvatar(myRankInfo.avatar)} icon={<UserOutlined />} size={54} />
                  <span>
                    <strong>我的排名 #{myRankInfo.rank}</strong>
                    <em>{myRankInfo.nickname}</em>
                  </span>
                </button>
              </Col>
              <Col xs={12} md={4}><Statistic title="总积分" value={myRankInfo.ratingAll} prefix={<RiseOutlined />} /></Col>
              <Col xs={12} md={4}><Statistic title="比赛" value={myRankInfo.ratingContest} /></Col>
              <Col xs={12} md={4}><Statistic title="奖项" value={myRankInfo.ratingCertificate} /></Col>
              <Col xs={12} md={4}><Statistic title="刷题" value={myRankInfo.ratingPractice} /></Col>
            </Row>
          </Card>
        )}

        <Card className="bits-card rank-table-card" variant="borderless" style={{ width: 'min(1120px, 100%)', margin: '0 auto' }}>
          {boardError ? (
            <Alert type="error" showIcon title="积分排行榜加载失败，请重试。" action={<Button onClick={() => setBoardRetry(value => value + 1)}>重新加载排行榜</Button>} />
          ) : <Skeleton loading={loading} active paragraph={{ rows: 8 }}>
            <Table
              columns={columns}
              dataSource={data}
              rowKey="userId"
              pagination={{ pageSize: 50, showSizeChanger: false }}
              scroll={{ x: 900 }}
              locale={{ emptyText: <Empty description={selectedTerm === 'current' ? '暂无积分排名记录' : '该学期暂无积分排名记录'} /> }}
              rowClassName={record => Number(record.userId) === Number(currentUserId) ? 'is-my-rank' : ''}
            />
          </Skeleton>}
        </Card>
      </main>
    </>
  );
};

export default RankPageClient;
