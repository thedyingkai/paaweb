"use client";
import { useEffect, useState } from 'react';
import { Alert, Button, Card, Empty, List, Skeleton, Space, Tag, Typography } from 'antd';
import { CalendarOutlined, TrophyOutlined } from '@ant-design/icons';
import Link from 'next/link';
import { positionStatusLabels, contestStageLabels } from '@/lib/besti-members';
import { teamError, kindNames } from '@/lib/besti';
import request from '@/utils/request';

type RecordItem = {
  id: number | string;
  kind: string;
  title: string;
  result: string;
  organization?: string;
  source_type?: string;
  family?: string;
  contest_stage?: string;
  contest_award?: string;
  team_name?: string;
  start_date?: string;
  end_date?: string;
  display_title?: string;
  position_status?: string;
  source_url?: string;
  history_link_id?: number;
  is_starred?: boolean;
};
type CertificateHonor = {
  id: string | number;
  name: string;
  levelName?: string;
  levelType?: string;
  rankType?: string;
  awardDate?: string;
};
const awardColor = (award?: string) => {
  if (award?.includes('金') || award?.includes('一等') || award?.includes('特等')) return '#b88200';
  if (award?.includes('银') || award?.includes('二等')) return '#63758c';
  if (award?.includes('铜') || award?.includes('三等')) return '#b8741a';
  return '#1677ff';
};
const source = (r: RecordItem) => r.source_url && <a href={r.source_url} target="_blank" rel="noopener noreferrer">证明来源 ↗</a>;

export default function MemberRecordSummary({
  userId,
  variant = 'honors',
  isOwnProfile = false,
  certificates = [],
}: {
  userId: string | number;
  variant?: 'honors' | 'experience';
  isOwnProfile?: boolean;
  certificates?: CertificateHonor[];
}) {
  const [items, setItems] = useState<RecordItem[]>([]);
  const [positions, setPositions] = useState<RecordItem[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [retry, setRetry] = useState(0);
  const [counts, setCounts] = useState({ awards: 0, starred: 0 });
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);setError('');setItems([]);setPositions([]);
    request.get<{ code: number; data: { items: RecordItem[]; positions?: RecordItem[]; awards: number; starred: number } }>('/team-space/public-records', {
      params: { userId }, signal: controller.signal, timeout: 15000,
    }).then(({data}) => {
      if (data.code !== 200 || !Array.isArray(data.data?.items)) throw new Error('个人记录加载失败，请重试');
      if (!controller.signal.aborted) {
        setItems(data.data.items);
        setPositions(data.data.positions || []);
        setCounts({ awards: data.data.awards || 0, starred: data.data.starred || 0 });
      }
    }).catch(e => {
      if (!controller.signal.aborted) setError(teamError(e));
    }).finally(() => {
      if (!controller.signal.aborted) setLoading(false);
    });
    return () => controller.abort();
  }, [userId, retry]);

  const awards = items.filter(r => r.kind === 'award');
  const positionKey = (r: RecordItem) => [r.organization,r.title,r.start_date,r.end_date].join('|');
  const officialPositions = new Set(positions.map(positionKey));
  const experiences = [...positions,...items.filter(r => r.kind !== 'position' || !officialPositions.has(positionKey(r)))];
  return (
    <div className="profile-records" data-record-section={variant}>
      <div style={{ display: 'flex', justifyContent: 'space-between', flexWrap: 'wrap', gap: 12, marginBottom: 20 }}>
        <Space wrap>
          {variant === 'honors' ? <>
            {!loading && !error && <Tag title="括号内为打星等效奖项，不计入正式获奖。">获奖 {counts.awards}{counts.starred > 0 ? `（+${counts.starred}）` : ''}</Tag>}
            {certificates.length > 0 && <Tag>证书荣誉 {certificates.length}</Tag>}
          </> : <Typography.Text strong>职务与参赛经历</Typography.Text>}
        </Space>
        {isOwnProfile && <Space wrap>
          {variant === 'honors' && <Link href="/history-awards">认领历史战绩</Link>}
          <Link href="/team-space">维护我的档案</Link>
        </Space>}
      </div>
      {error && <Alert type="warning" showIcon title={error} action={<Button size="small" onClick={() => setRetry(value => value + 1)}>重试个人记录</Button>} style={{ marginBottom: 16 }} />}
      <Skeleton loading={loading} active paragraph={{ rows: 4 }}>
        {variant === 'honors' ? <>
          {!error && !awards.length && !certificates.length && <Empty description="暂无荣誉记录" />}
          <div className="honor-grid">
            {awards.map(r => {
              const color = r.is_starred ? '#d48806' : awardColor(r.contest_award || r.result);
              return <Card className="honor-card" variant="borderless" key={`record-${r.id}`}>
                <div className="honor-icon" style={{ color, background: `${color}18` }}><TrophyOutlined /></div>
                <div style={{ minWidth: 0, overflowWrap: 'anywhere' }}>
                  <Space wrap size={[4, 4]}>
                    {r.family && <Tag>{r.family}</Tag>}
                    {r.contest_stage && <Tag>{contestStageLabels[r.contest_stage] || r.contest_stage}</Tag>}
                    {r.is_starred ? <Tag color="orange">打星{r.contest_award ? ` · 等效${r.contest_award}` : ''}</Tag> : <Tag color={color}>{r.contest_award || r.result}</Tag>}
                  </Space>
                  <h3>{r.display_title || r.title}</h3>
                  {r.team_name && <p>{r.team_name}</p>}
                  {r.contest_award && !r.is_starred && <p>学校认定：{r.result}</p>}
                  <Space wrap>
                    {r.start_date && <Typography.Text type="secondary"><CalendarOutlined /> {r.start_date}</Typography.Text>}
                    {source(r)}
                  </Space>
                </div>
              </Card>;
            })}
            {certificates.map(item => {
              const color = awardColor(item.rankType);
              return <Card className="honor-card" variant="borderless" key={`certificate-${item.id}`}>
                <div className="honor-icon" style={{ color, background: `${color}18` }}><TrophyOutlined /></div>
                <div style={{ minWidth: 0, overflowWrap: 'anywhere' }}>
                  <Space wrap><Tag color="blue">{item.levelType || '证书荣誉'}</Tag><Tag color={color}>{item.rankType || '获奖'}</Tag></Space>
                  <h3>{item.name}</h3>
                  {item.levelName && <p>{item.levelName}</p>}
                  {item.awardDate && <Typography.Text type="secondary"><CalendarOutlined /> {item.awardDate}</Typography.Text>}
                </div>
              </Card>;
            })}
          </div>
        </> : <List
          dataSource={experiences}
          locale={{ emptyText: error ? <span /> : <Empty description="暂无已核验的职务或参赛经历" /> }}
          renderItem={r => <List.Item><List.Item.Meta
            title={<><Tag>{r.kind === 'award' ? '参赛经历' : kindNames[r.kind]}</Tag>{r.source_type === 'official_roster' && <Tag color="blue">正式任职</Tag>}{r.display_title || r.title}{r.kind === 'position' && <Tag color={r.position_status === 'former' ? 'default' : 'blue'}>{positionStatusLabels[r.position_status || 'unknown']}</Tag>}</>}
            description={<Space wrap>{r.team_name && <span>{r.team_name}</span>}{r.is_starred ? <Tag color="orange">打星{r.contest_award ? `（等效${r.contest_award}）` : '参赛'}</Tag> : r.contest_award ? <Tag>{contestStageLabels[r.contest_stage || ''] || ''} · {r.contest_award}</Tag> : r.result && <span>{r.result}</span>}<span>{[r.start_date, r.end_date].filter(Boolean).join(' — ')}</span>{source(r)}</Space>}
          /></List.Item>}
        />}
      </Skeleton>
    </div>
  );
}
