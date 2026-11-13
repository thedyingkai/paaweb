"use client";

import { Badge, Card, Col, Empty, Input, List, Progress, Row, Skeleton, Space, Tabs, Tag, Typography } from 'antd';
import { CalendarOutlined, ClockCircleOutlined, SearchOutlined, TrophyOutlined } from '@ant-design/icons';
import axios from 'axios';
import dayjs from 'dayjs';
import { eventSignupPeriod, eventSignupCount } from '@/utils/eventSignup';
import { useRouter } from 'next/navigation';
import { useEffect, useMemo, useState } from 'react';
import ActivityLayout from '../ContestNav';

const { Title, Text } = Typography;

interface EventItem {
    signupMode?: string;
  eventId: number;
  name: string;
  categoryName?: string;
  isTeam?: number;
  signupStart: string;
  signupEnd: string | null;
  eventStart: string;
  eventEnd: string;
  signupCount: number;
  maxSignup: number | null;
}

const getEventStatus = (item: EventItem) => {
  const now = dayjs();
  if (item.signupMode === 'roster' && now.isBefore(item.eventStart)) return { text: '名单已确定', color: 'blue', status: 'waiting', order: 3 };
  if (item.signupMode === 'roster') return now.isBefore(item.eventEnd) ? { text: '正在比赛中', color: 'red', status: 'ongoing', order: 4 } : { text: '已结束', color: 'default', status: 'ended', order: 5 };
  if (now.isBefore(item.signupStart)) return { text: '报名未开始', color: 'orange', status: 'pending', order: 2 };
  if (!item.signupEnd || now.isBefore(item.signupEnd)) return { text: '报名进行中', color: 'green', status: 'registering', order: 1 };
  if (now.isBefore(item.eventStart)) return { text: '等待开赛', color: 'blue', status: 'waiting', order: 3 };
  if (now.isBefore(item.eventEnd)) return { text: '正在比赛中', color: 'red', status: 'ongoing', order: 4 };
  return { text: '已结束', color: 'default', status: 'ended', order: 5 };
};

export default function ActivitySignupPage() {
  const router = useRouter();
  const [keyword, setKeyword] = useState('');
  const [activeTab, setActiveTab] = useState('all');
  const [list, setList] = useState<EventItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    axios.get('/api/event/list').then(res => {
      const data = [...(res.data.data || [])].sort((a, b) => getEventStatus(a).order - getEventStatus(b).order);
      setList(data);
    }).finally(() => setLoading(false));
  }, []);

  const filtered = useMemo(() => list.filter(item => {
    const hit = !keyword || item.name.toLowerCase().includes(keyword.toLowerCase()) || item.categoryName?.toLowerCase().includes(keyword.toLowerCase());
    const status = getEventStatus(item).status;
    const tabHit = activeTab === 'all' || activeTab === status || (activeTab === 'ended' && status === 'ended');
    return hit && tabHit;
  }), [activeTab, keyword, list]);

  return (
    <ActivityLayout>
      <section className="activity-content">
        <div className="activity-toolbar">
          <Title level={3}>比赛报名</Title>
          <Input className="bits-input" prefix={<SearchOutlined />} allowClear placeholder="搜索比赛名称" value={keyword} onChange={event => setKeyword(event.target.value)} />
        </div>
        <Tabs activeKey={activeTab} onChange={setActiveTab} items={[{ key: 'all', label: '全部赛事' }, { key: 'registering', label: '正在报名' }, { key: 'pending', label: '即将开始' }, { key: 'ended', label: '历史赛事' }]} />
        <Skeleton loading={loading} active paragraph={{ rows: 8 }}>
          {filtered.length ? (
            <List dataSource={filtered} renderItem={item => {
              const status = getEventStatus(item);
              const percent = item.maxSignup != null && item.maxSignup > 0 ? Math.min(100, Math.round((item.signupCount / item.maxSignup) * 100)) : 0;
              return (
                <List.Item style={{ width: '100%', padding: '12px 0' }}>
                  <div style={{ width: '100%' }}>
                    <Badge.Ribbon text={status.text} color={status.color}>
                      <Card className="bits-card activity-list-card uniform-activity-card" variant="borderless" hoverable onClick={() => router.push(`/contest/signup/${item.eventId}`)}>
                        <Row gutter={[18, 14]} align="middle">
                          <Col xs={24} md={18}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8, flexWrap: 'wrap' }}>
                              <Text strong className="activity-list-title" style={{ margin: 0, fontSize: 18, color: '#17212b' }}>
                                {item.name}
                              </Text>
                              <Tag color={item.isTeam ? 'purple' : 'blue'} style={{ margin: 0 }}>{item.isTeam ? '团队赛' : '个人赛'}</Tag>
                              {item.categoryName && <Tag style={{ margin: 0 }}>{item.categoryName}</Tag>}
                            </div>
                            <Space className="activity-time-row" direction="vertical" style={{ display: 'flex', marginTop: 0 }}>
                              <span><ClockCircleOutlined /> {item.signupMode === 'roster' ? '参赛：按确定名单导入，不开放自主报名' : `报名：${eventSignupPeriod(item.signupStart, item.signupEnd)}`}</span>
                              <span><CalendarOutlined /> 比赛：{dayjs(item.eventStart).format('YYYY-MM-DD HH:mm')} ~ {dayjs(item.eventEnd).format('YYYY-MM-DD HH:mm')}</span>
                            </Space>
                          </Col>
                          <Col xs={24} md={6}>
                            <div className="signup-progress"><span>{eventSignupCount(item.signupCount,item.maxSignup)}</span>{item.maxSignup != null && <Progress percent={percent} showInfo={false} />}</div>
                          </Col>
                        </Row>
                      </Card>
                    </Badge.Ribbon>
                  </div>
                </List.Item>
              );
            }} />
          ) : <Empty description="暂无相关赛事" />}
        </Skeleton>
      </section>
    </ActivityLayout>
  );
}
