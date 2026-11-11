"use client";

import { Button, Card, Divider, Skeleton, Tag, Typography } from 'antd';
import { ArrowLeftOutlined, ReadOutlined, TrophyOutlined } from '@ant-design/icons';
import axios from 'axios';
import { useParams, useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import ActivityLayout from '../../ContestNav';
import MarkdownContent from '../../../components/content/MarkdownContent';

const { Title, Paragraph } = Typography;

interface ContestCategoryDetail {
  name: string;
  intro?: string;
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

export default function ActivityIntroDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [data, setData] = useState<ContestCategoryDetail | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    axios.get(`/api/contest/category/detail/${id}`).then(res => setData(res.data.data || null)).finally(() => setLoading(false));
  }, [id]);

  const level = levelConfig[data?.level || ''] || { text: data?.level || '赛事', color: '#7890a2' };

  return (
    <ActivityLayout>
      <section className="activity-content">
        <Button className="bits-ghost-button" icon={<ArrowLeftOutlined />} onClick={() => router.push('/contest/intro')} style={{ marginBottom: 16 }}>返回介绍列表</Button>
        <Skeleton loading={loading} active paragraph={{ rows: 8 }}>
          {data && (
            <Card className="content-main-card intro-content-card" variant="borderless" style={{ width: 'min(980px, 100%)', margin: '0 auto' }}>
              <div style={{ display: 'flex', gap: 24, alignItems: 'center', marginBottom: 24, flexWrap: 'wrap' }}>
                <div className="contest-logo-box detail-logo">
                  {data.logoUrl ? <img src={`/api${data.logoUrl}`} alt={data.name} /> : <TrophyOutlined />}
                </div>
                <div>
                  <Tag color={level.color}>{level.text}赛事</Tag>
                  <Title level={2} style={{ margin: '8px 0 4px 0' }}>{data.name}</Title>
                  <Paragraph style={{ margin: 0, color: '#688194' }}>本页面为赛事介绍页。报名与榜单请通过主导航进入对应模块。</Paragraph>
                </div>
              </div>
              <Divider style={{ margin: '0 0 24px 0' }} />
              <div className="center-readable">
                <MarkdownContent content={data.intro} />
              </div>
            </Card>
          )}
        </Skeleton>
      </section>
    </ActivityLayout>
  );
}
