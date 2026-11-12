"use client";
import { useEffect, useState } from 'react';
import { Alert, Button, Empty, List, Skeleton, Space, Tag, Typography } from 'antd';
import Link from 'next/link';
import { positionStatusLabels } from '@/lib/besti-members';
import { teamError, kindNames } from '@/lib/besti';
import request from '@/utils/request';
import PersonalHonorWall from './PersonalHonorWall';

type RecordItem = {
  id: number | string; kind: string; title: string; result: string; organization?: string;
  source_type?: string; contest_stage?: string; contest_award?: string; team_name?: string;
  start_date?: string; end_date?: string; display_title?: string; position_status?: string;
  source_url?: string; is_starred?: boolean;
};
type Props = { userId: string | number; variant?: 'honors' | 'experience'; isOwnProfile?: boolean };
const source = (r: RecordItem) => r.source_url && <a href={r.source_url} target="_blank" rel="noopener noreferrer">证明来源 ↗</a>;

export default function MemberRecordSummary(props: Props) {
  return props.variant === 'experience'
    ? <ExperienceSummary {...props} />
    : <PersonalHonorWall userId={props.userId} isOwnProfile={props.isOwnProfile || false} />;
}

function ExperienceSummary({ userId, isOwnProfile = false }: Props) {
  const [items, setItems] = useState<RecordItem[]>([]);
  const [positions, setPositions] = useState<RecordItem[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError(''); setItems([]); setPositions([]);
    request.get<{ code: number; data: { items: RecordItem[]; positions?: RecordItem[] } }>('/team-space/public-records', {
      params: { userId, section: 'experience' }, signal: controller.signal, timeout: 15000,
    }).then(({data}) => {
      if (data.code !== 200 || !Array.isArray(data.data?.items)) throw new Error('个人记录加载失败，请重试');
      if (!controller.signal.aborted) { setItems(data.data.items); setPositions(data.data.positions || []); }
    }).catch(e => { if (!controller.signal.aborted) setError(teamError(e)); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [userId, retry]);

  const positionKey = (r: RecordItem) => [r.organization,r.title,r.start_date,r.end_date].join('|');
  const officialPositions = new Set(positions.map(positionKey));
  const experiences = [...positions,...items.filter(r => r.kind === 'organizing' || r.kind === 'position' && !officialPositions.has(positionKey(r)))];
  return <div className="profile-records" data-record-section="experience">
    <div style={{ display: 'flex', justifyContent: 'space-between', flexWrap: 'wrap', gap: 12, marginBottom: 20 }}>
      <Typography.Text strong>职务与办赛经历</Typography.Text>
      {isOwnProfile && <Link href="/team-space">维护我的档案</Link>}
    </div>
    {error && <Alert type="warning" showIcon title={error} action={<Button size="small" onClick={() => setRetry(value => value + 1)}>重试个人记录</Button>} style={{ marginBottom: 16 }} />}
    <Skeleton loading={loading} active paragraph={{ rows: 4 }}>
      <List dataSource={experiences}
        locale={{ emptyText: error ? <span /> : <Empty description="暂无已核验的职务或办赛经历" /> }}
        renderItem={r => <List.Item><List.Item.Meta
          title={<><Tag>{kindNames[r.kind]}</Tag>{r.source_type === 'official_roster' && <Tag color="blue">正式任职</Tag>}{r.display_title || r.title}{r.kind === 'position' && <Tag color={r.position_status === 'former' ? 'default' : 'blue'}>{positionStatusLabels[r.position_status || 'unknown']}</Tag>}</>}
          description={<Space wrap>{r.kind === 'organizing' && r.organization && <span>举办单位：{r.organization}</span>}{r.result && <span>{r.kind === 'organizing' ? '承担工作：' : ''}{r.result}</span>}<span>{[r.start_date, r.end_date].filter(Boolean).join(' — ')}</span>{source(r)}</Space>}
        /></List.Item>}
      />
    </Skeleton>
  </div>;
}
