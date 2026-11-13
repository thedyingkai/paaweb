"use client";
/* eslint-disable react-hooks/set-state-in-effect */

import { Suspense, useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Card, List, Typography, Spin, Empty, Tag, Space, Tabs, Avatar, Input, Button } from 'antd';
import {
  TrophyOutlined,
  FileTextOutlined,
  ReadOutlined,
  UserOutlined,
  FormOutlined,
  BarChartOutlined,
  ArrowRightOutlined,
  SearchOutlined
} from '@ant-design/icons';
import axios from 'axios';
import { userDisplayName, type UserSearchResult } from '@/types/user';
import SiteHeader from '../components/SiteHeader';

const { Title, Text } = Typography;

interface SearchNoticeItem {
  infoId: number;
  type?: 'notice' | 'news' | 'publicity' | string;
  title: string;
  createTime: string;
}

interface SearchContestCategoryItem {
  categoryId: number;
  name: string;
  description?: string;
}

interface SearchEventItem {
  boardId?: number;
  actualStartTime?: string;
  actualEndTime?: string;
  eventId: number;
  name: string;
  status: number;
  type: string;
  signupStart: string;
  signupEnd: string;
  signupMode?: string;
  eventStart: string;
  eventEnd: string;
}

interface SearchTrainingItem {
  trainId: number;
  name: string;
}

type SearchUserItem = UserSearchResult;

interface GlobalSearchResult {
  notices: SearchNoticeItem[];
  contestIntros: SearchContestCategoryItem[];
  contestSignups: SearchEventItem[];
  contestBoards: SearchEventItem[];
  trainings: SearchTrainingItem[];
  users: SearchUserItem[];
}

const searchGlobal = async (keyword: string): Promise<GlobalSearchResult> => {
  const response = await axios.get('/api/common/search', {
    params: { keyword }
  });
  return response.data.data;
};

const SearchPageContent = () => {
  const searchParams = useSearchParams();
  const keyword = searchParams.get('keyword') || '';
  const router = useRouter();

  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<GlobalSearchResult | null>(null);
  const [inputKeyword, setInputKeyword] = useState(keyword);

  const fetchData = async (val: string) => {
    setLoading(true);
    try {
      const res = await searchGlobal(val);
      setData(res);
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    document.title = "全站搜索";
    if (keyword) {
      fetchData(keyword);
    } else {
      setData(null);
    }
  }, [keyword]);

  const handleSearchSubmit = () => {
    const nextKeyword = inputKeyword.trim();
    if (nextKeyword) {
      router.push(`/search?keyword=${encodeURIComponent(nextKeyword)}`);
    }
  };

  const getNoticeRoute = (item: SearchNoticeItem) => {
    if (item.type === 'news') return `/news/${item.infoId}`;
    if (item.type === 'publicity') return `/publicity/${item.infoId}`;
    return `/notice/${item.infoId}`;
  };

  const highlightKeyword = (text: string) => {
    if (!keyword || !text) return text;
    try {
      const parts = text.split(new RegExp(`(${keyword})`, 'gi'));
      return (
        <span>
          {parts.map((part, i) =>
            part.toLowerCase() === keyword.toLowerCase() ? (
              <span key={i} style={{ color: '#1890ff', fontWeight: 'bold' }}>{part}</span>
            ) : (
              part
            )
          )}
        </span>
      );
    } catch {
      return text;
    }
  };

  const renderContestIntroItem = (item: SearchContestCategoryItem) => (
    <List.Item onClick={() => router.push(`/contest/intro/${item.categoryId}`)} style={{ cursor: 'pointer' }}>
      <List.Item.Meta
        avatar={<Avatar style={{ backgroundColor: '#1890ff' }} icon={<TrophyOutlined />} />}
        title={highlightKeyword(item.name)}
        description={<Text type="secondary" style={{ fontSize: 12 }}>查看赛事详情</Text>}
      />
      <ArrowRightOutlined style={{ color: '#ccc' }} />
    </List.Item>
  );

  const getStatusTag = (item: SearchEventItem, type: 'signup' | 'board') => {
    const now = new Date().getTime();

    if (type === 'signup' && item.signupMode === 'roster') return <Tag color="blue">固定名单参赛</Tag>;
    if (type === 'signup') {
      const start = item.signupStart ? new Date(item.signupStart).getTime() : 0;
      const end = item.signupEnd ? new Date(item.signupEnd).getTime() : Infinity;

      if (now < start) {
        return <Tag color="orange">报名未开始</Tag>;
      } else if (now >= end) {
        return <Tag color="default">报名已截止</Tag>;
      } else {
        return <Tag color="green">报名进行中</Tag>;
      }
    } else {
      const startTime = item.actualStartTime || item.eventStart;
      const start = startTime ? new Date(startTime).getTime() : 0;
      const endTime = item.actualEndTime || item.eventEnd;
      const end = endTime ? new Date(endTime).getTime() : 0;

      if (now < start) {
        return <Tag color="orange">比赛未开始</Tag>;
      } else if (now >= end) {
        return <Tag color="blue">比赛已结束</Tag>;
      } else {
        return <Tag color="processing" icon={<Spin size="small" />}> 比赛进行中</Tag>;
      }
    }
  };

  const renderEventItem = (item: SearchEventItem, type: 'signup' | 'board') => {
    let url = '';
    let icon = null;
    let label = '';

    if (type === 'signup') {
      url = `/contest/signup/${item.eventId}`;
      icon = <FormOutlined />;
      label = '前往报名';
    } else if (type === 'board') {
      url = `/contest/board/${item.boardId}`;
      icon = <BarChartOutlined />;
      label = '查看外榜';
    }

    return (
      <List.Item onClick={() => router.push(url)} style={{ cursor: 'pointer' }}>
        <List.Item.Meta
          avatar={<Avatar style={{ backgroundColor: '#1890ff' }} icon={icon} />}
          title={highlightKeyword(item.name)}
          description={
            <Space size="small">
              {getStatusTag(item, type)}
              <Text type="secondary" style={{ fontSize: 12 }}>{label}</Text>
            </Space>
          }
        />
        <ArrowRightOutlined style={{ color: '#ccc' }} />
      </List.Item>
    );
  };

  const renderNoticeItem = (item: SearchNoticeItem) => (
    <List.Item onClick={() => router.push(getNoticeRoute(item))} style={{ cursor: 'pointer' }}>
      <List.Item.Meta
        avatar={<FileTextOutlined style={{ fontSize: 24, color: '#1890ff' }} />}
        title={highlightKeyword(item.title)}
        description={item.createTime}
      />
    </List.Item>
  );

  const renderTrainingItem = (item: SearchTrainingItem) => (
    <List.Item onClick={() => router.push(`/training/${item.trainId}`)} style={{ cursor: 'pointer' }}>
      <List.Item.Meta
        avatar={<ReadOutlined style={{ fontSize: 24, color: '#52c41a' }} />}
        title={highlightKeyword(item.name)}
      />
    </List.Item>
  );

  const renderUserItem = (item: SearchUserItem) => (
    <List.Item onClick={() => router.push(`/profile/${item.userId}`)} style={{ cursor: 'pointer' }}>
      <List.Item.Meta
        avatar={
          <Avatar
            size={48}
            icon={<UserOutlined />}
            style={{ backgroundColor: '#faad14' }}
            src={item.avatarUrl}
          />
        }
        title={
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ fontWeight: 'bold', fontSize: '16px' }}>{highlightKeyword(userDisplayName(item))}</span>
            <Text type="secondary" style={{ fontSize: '13px' }}>#{item.userId}</Text>
          </div>
        }
        description={
          <div style={{ marginTop: 4 }}>
            <div style={{ color: '#333', fontWeight: 500, marginBottom: 2 }}>
              {item.realName ? highlightKeyword(item.realName) : '查看个人主页'}
            </div>
          </div>
        }
      />
    </List.Item>
  );

  const hasResults = data && (
    (data.contestIntros && data.contestIntros.length > 0) ||
    (data.contestSignups && data.contestSignups.length > 0) ||
    (data.contestBoards && data.contestBoards.length > 0) ||
    (data.notices && data.notices.length > 0) ||
    (data.trainings && data.trainings.length > 0) ||
    (data.users && data.users.length > 0)
  );

  const items = [
    {
      key: 'all',
      label: '全部结果',
      children: (
        <Space direction="vertical" style={{ width: '100%' }} size="large">
          {data?.contestIntros && data.contestIntros.length > 0 && (
            <Card className="replica-blue-card" title={<><TrophyOutlined /> 赛事库</>} size="small">
              <List dataSource={data.contestIntros} renderItem={renderContestIntroItem} />
            </Card>
          )}

          {data?.contestSignups && data.contestSignups.length > 0 && (
            <Card className="replica-blue-card" title={<><FormOutlined /> 比赛报名</>} size="small">
              <List dataSource={data.contestSignups} renderItem={(item) => renderEventItem(item, 'signup')} />
            </Card>
          )}

          {data?.contestBoards && data.contestBoards.length > 0 && (
            <Card className="replica-blue-card" title={<><BarChartOutlined /> 比赛外榜</>} size="small">
              <List dataSource={data.contestBoards} renderItem={(item) => renderEventItem(item, 'board')} />
            </Card>
          )}

          {data?.notices && data.notices.length > 0 && (
            <Card className="replica-blue-card" title={<><FileTextOutlined /> 通知公告</>} size="small">
              <List dataSource={data.notices} renderItem={renderNoticeItem} />
            </Card>
          )}

          {data?.trainings && data.trainings.length > 0 && (
            <Card className="replica-blue-card" title={<><ReadOutlined /> 日常培训</>} size="small">
              <List dataSource={data.trainings} renderItem={renderTrainingItem} />
            </Card>
          )}

          {data?.users && data.users.length > 0 && (
            <Card className="replica-blue-card" title={<><UserOutlined /> 用户</>} size="small">
              <List dataSource={data.users} renderItem={renderUserItem} />
            </Card>
          )}

          {!loading && !hasResults && (
            <Empty description="暂无相关搜索结果" />
          )}
        </Space>
      ),
    }
  ];

  return (
    <>
      <SiteHeader />
      <main className="search-fullscreen-shell">
        <section className="search-fullscreen-panel">
          <Title className="search-fullscreen-title">全站搜索</Title>
          <div className="search-fullscreen-bar">
            <Input
              size="large"
              prefix={<SearchOutlined style={{ color: '#8aa6ba' }} />}
              allowClear
              autoFocus
              placeholder="搜索全站..."
              value={inputKeyword}
              onChange={(event) => setInputKeyword(event.target.value)}
              onPressEnter={handleSearchSubmit}
            />
            <Button type="primary" size="large" icon={<SearchOutlined />} onClick={handleSearchSubmit}>
              搜索
            </Button>
          </div>
        </section>
        <div className="replica-page-panel" style={{ maxWidth: 1000, margin: '20px auto', padding: '0 20px' }}>
          <div style={{ marginBottom: 24 }}>
            <Title level={3}>搜索结果: &quot;{keyword}&quot;</Title>
          </div>

          {loading ? (
            <div style={{ textAlign: 'center', padding: 50 }}><Spin size="large" /></div>
          ) : (
            <Tabs defaultActiveKey="all" items={items} />
          )}
        </div>
      </main>
    </>
  );
};

const SearchPage = () => (
  <Suspense fallback={null}>
    <SearchPageContent />
  </Suspense>
);

export default SearchPage;
