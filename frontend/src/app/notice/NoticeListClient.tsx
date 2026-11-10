"use client";

import { Card, Empty, Input, List, Skeleton, Tabs, Tag, Typography, Divider } from 'antd';
import {
  EyeOutlined,
  FileTextOutlined,
  NotificationOutlined,
  SearchOutlined,
  SoundOutlined,
  UserOutlined
} from '@ant-design/icons';
import axios from 'axios';
import dayjs from 'dayjs';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import SiteHeader from '../components/SiteHeader';
import PageLoader from '../components/PageLoader';
import Link from 'next/link';

const { Title, Paragraph, Text } = Typography;

interface NoticeItem {
  infoId: number | string;
  title: string;
  content?: string;
  createTime?: string;
  publisherName?: string;
  viewCount?: number;
}

const noticeTabs = [
  { key: 'notice', label: '通知', icon: <NotificationOutlined />, color: '#1677ff' },
  { key: 'news', label: '新闻', icon: <SoundOutlined />, color: '#13c2c2' },
  { key: 'publicity', label: '公示', icon: <FileTextOutlined />, color: '#fa8c16' }
];

const stripMarkdown = (value?: string) => (value || '').replace(/[#*_`>\-[\]()]/g, '').replace(/\s+/g, ' ').trim();

interface NoticeListClientProps {
  type: 'notice' | 'news' | 'publicity';
}

const NoticeListClient = ({ type: defaultType }: NoticeListClientProps) => {
  const router = useRouter();
  const [routeType, setRouteType] = useState<'notice' | 'news' | 'publicity'>(defaultType);

  useEffect(() => {
    setRouteType(defaultType);
  }, [defaultType]);

  const [keyword, setKeyword] = useState('');
  const [debouncedKeyword, setDebouncedKeyword] = useState('');
  const [list, setList] = useState<NoticeItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedKeyword(keyword), 450);
    return () => window.clearTimeout(timer);
  }, [keyword]);

  useEffect(() => {
    const fetchList = async () => {
      setLoading(true);
      try {
        const res = await axios.get<{ code: number; data?: { records?: NoticeItem[] } }>('/api/notice/list', {
          params: { type: routeType, keyword: debouncedKeyword || undefined }
        });
        const getTimestamp = (dateStr?: string) => {
          if (!dateStr) return 0;
          const d = new Date(dateStr);
          return isNaN(d.getTime()) ? 0 : d.getTime();
        };
        const records = [...(res.data.data?.records || [])].sort((a, b) => (
          getTimestamp(b.createTime) - getTimestamp(a.createTime)
        ));
        setList(records);
      } finally {
        setLoading(false);
      }
    };

    fetchList().catch(() => setLoading(false));
  }, [routeType, debouncedKeyword]);

  const active = noticeTabs.find(tab => tab.key === routeType) || noticeTabs[0];

  return (
    <>
      <PageLoader loading={loading} />
      <SiteHeader />
      <main className="content-page-shell">
        <section className="content-hero notice-hero content-hero-980">
          <Tag className="bits-pill">NEWSROOM</Tag>
          <Paragraph style={{ marginTop: 12 }}>通知、新闻、公示集中发布，重要信息不再散落。</Paragraph>
        </section>

        <Card className="bits-card notice-list-card" variant="borderless" style={{ width: 'min(980px, 100%)', margin: '0 auto' }}>
          <div style={{ textAlign: 'center', marginBottom: 16 }}>
            <Tabs
              centered
              activeKey={routeType}
              onChange={(key) => {
                setRouteType(key as 'notice' | 'news' | 'publicity');
                router.push(key === 'notice' ? '/notice' : '/' + key);
              }}
              items={noticeTabs.map(tab => ({
                key: tab.key,
                label: <span>{tab.icon} {tab.label}</span>
              }))}
              style={{ marginBottom: 4 }}
            />
          </div>
          <Divider style={{ margin: '0 0 24px 0' }} />

          <div className="notice-toolbar" style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 16 }}>
            <Input
              className="bits-input notice-search"
              prefix={<SearchOutlined />}
              allowClear
              placeholder="搜索标题或关键词"
              value={keyword}
              onChange={event => setKeyword(event.target.value)}
              onPressEnter={event => setDebouncedKeyword(event.currentTarget.value)}
              style={{ width: 280 }}
            />
          </div>

          <Skeleton loading={loading} active paragraph={{ rows: 8 }}>
            {list.length === 0 ? (
              <Empty description="暂无内容" />
            ) : (
              <List
                className="notice-list"
                pagination={{ pageSize: 10 }}
                dataSource={list}
                renderItem={item => (
                  <List.Item
                    key={item.infoId}
                    className="notice-row"
                    onClick={() => router.push(routeType === 'notice' ? `/notice/${item.infoId}` : `/${routeType}/${item.infoId}`)}
                  >
                    <div className="notice-row-body">
                      <div className="notice-row-title">
                        <Tag color={active.color}>{active.label}</Tag>
                        <Link href={routeType === 'notice' ? `/notice/${item.infoId}` : `/${routeType}/${item.infoId}`} onClick={(e) => e.stopPropagation()} style={{ color: 'inherit', textDecoration: 'none' }}>
                          <strong>{item.title}</strong>
                        </Link>
                      </div>
                      <p>{stripMarkdown(item.content).slice(0, 128) || '暂无摘要'}</p>
                      <div className="notice-meta">
                        <Text><UserOutlined /> {item.publisherName || '校队'}</Text>
                        <Text>{dayjs(item.createTime).isValid() ? dayjs(item.createTime).format('YYYY-MM-DD') : '时间未知'}</Text>
                        <Text><EyeOutlined /> {item.viewCount || 0}</Text>
                      </div>
                    </div>
                  </List.Item>
                )}
              />
            )}
          </Skeleton>
        </Card>
      </main>
    </>
  );
};

export default NoticeListClient;
