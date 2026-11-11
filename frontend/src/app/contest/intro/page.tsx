"use client";

import { Badge, Card, Empty, Grid, Input, List, Skeleton, Typography } from 'antd';
import { SearchOutlined, TrophyOutlined } from '@ant-design/icons';
import axios from 'axios';
import { useRouter } from 'next/navigation';
import { useEffect, useMemo, useState } from 'react';
import ActivityLayout from '../ContestNav';

const { Title } = Typography;
const { useBreakpoint } = Grid;

interface ContestCategory {
  categoryId: number;
  name: string;
  level?: string;
  logoUrl?: string;
}

const levelConfig: Record<string, { text: string; color: string }> = {
  national: { text: '国家级', color: '#f5222d' },
  provincial: { text: '省部级', color: '#fa541c' },
  city: { text: '市级', color: '#fa8c16' },
  school: { text: '校级', color: '#1677ff' },
  department: { text: '院级', color: '#52c41a' }
};

export default function ActivityIntroPage() {
  const router = useRouter();
  const screens = useBreakpoint();
  const [keyword, setKeyword] = useState('');
  const [list, setList] = useState<ContestCategory[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchList = async () => {
      setLoading(true);
      try {
        const res = await axios.get('/api/contest/category/list', { params: { size: 100, keyword, admin: false } });
        setList(res.data.data?.records || []);
      } finally {
        setLoading(false);
      }
    };
    const timer = window.setTimeout(() => fetchList().catch(() => setLoading(false)), 300);
    return () => window.clearTimeout(timer);
  }, [keyword]);

  const filtered = useMemo(() => list, [list]);

  return (
    <ActivityLayout>
      <section className="activity-content">
        <div className="activity-toolbar contest-list-toolbar">
          <div>
            <Title level={2} style={{ margin: 0 }}><TrophyOutlined /> 赛事中心</Title>
            <div style={{ color: '#888', marginTop: 8 }}>浏览校内所有算法竞赛与相关认证</div>
          </div>
          <Input.Search
            className="bits-input"
            prefix={<SearchOutlined />}
            allowClear
            enterButton
            size="large"
            placeholder="搜索赛事名称..."
            value={keyword}
            onChange={event => setKeyword(event.target.value)}
            style={{ width: screens.md ? 320 : '100%' }}
          />
        </div>
        <Skeleton loading={loading} active paragraph={{ rows: 8 }}>
          {filtered.length ? (
            <List
              grid={{ gutter: 24, xs: 1, sm: 2, md: 3, lg: 4 }}
              dataSource={filtered}
              renderItem={item => {
                const level = levelConfig[item.level || ''] || { text: item.level || '赛事', color: '#7890a2' };
                return (
                  <List.Item>
                    <Badge.Ribbon text={level.text} color={level.color}>
                      <Card
                        className="contest-list-card"
                        hoverable
                        cover={
                          <div className="contest-list-cover">
                            {item.logoUrl ? <img src={`/api${item.logoUrl}`} alt={item.name} /> : <TrophyOutlined />}
                          </div>
                        }
                        onClick={() => router.push(`/contest/intro/${item.categoryId}`)}
                        styles={{ body: { padding: 16 } }}
                      >
                        <Card.Meta
                          title={<div className="contest-list-title">{item.name}</div>}
                          description={<div className="contest-list-desc"><span>点击查看详情</span></div>}
                        />
                      </Card>
                    </Badge.Ribbon>
                  </List.Item>
                );
              }}
            />
          ) : <Empty description="暂无比赛介绍" />}
        </Skeleton>
      </section>
    </ActivityLayout>
  );
}
