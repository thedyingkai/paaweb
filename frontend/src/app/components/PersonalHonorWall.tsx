"use client";

import { useEffect, useState } from 'react';
import { Alert, Button, Card, Checkbox, ConfigProvider, Empty, Modal, Skeleton, Space, Tag, Typography, message } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { ArrowDownOutlined, ArrowUpOutlined, CalendarOutlined, SettingOutlined, TrophyOutlined } from '@ant-design/icons';
import Link from 'next/link';
import { contestStageLabels } from '@/lib/besti-members';
import { teamError } from '@/lib/besti';
import request from '@/utils/request';

type Award = {
  id: number | string; title: string; result: string; family?: string; contest_stage?: string;
  contest_award?: string; team_name?: string; start_date?: string; source_url?: string; is_starred?: boolean;
};
type Certificate = { id: string; name: string; levelName?: string; levelType?: string; rankType?: string; awardDate?: string };
type Preferences = { customized: boolean; selectedKeys: string[] };
type Showcase = { records: Award[]; certificates: Certificate[]; preferences: Preferences };
type Entry = { key: string; title: string; date?: string; award?: Award; certificate?: Certificate };
const awardColor = (award?: string) => {
  if (award?.includes('金') || award?.includes('一等') || award?.includes('特等')) return '#b88200';
  if (award?.includes('银') || award?.includes('二等')) return '#63758c';
  if (award?.includes('铜') || award?.includes('三等')) return '#b8741a';
  return '#1677ff';
};

function HonorCard({ entry }: { entry: Entry }) {
  const r = entry.award;
  const c = entry.certificate;
  const color = r?.is_starred ? '#d48806' : awardColor(r?.contest_award || r?.result || c?.rankType);
  return <Card className="honor-card" variant="borderless" data-honor-key={entry.key}>
    <div className="honor-icon" style={{ color, background: `${color}18` }}><TrophyOutlined /></div>
    <div style={{ minWidth: 0, overflowWrap: 'anywhere' }}>
      <Space wrap size={[4, 4]}>
        {r ? <>
          {r.family && <Tag>{r.family}</Tag>}
          {r.contest_stage && <Tag>{contestStageLabels[r.contest_stage] || r.contest_stage}</Tag>}
          {r.is_starred ? <Tag color="orange">打星{r.contest_award ? ` · 等效${r.contest_award}` : ''}</Tag> : <Tag color={color}>{r.contest_award || r.result}</Tag>}
        </> : <><Tag color="blue">{c?.levelType || '证书荣誉'}</Tag><Tag color={color}>{c?.rankType || '获奖'}</Tag></>}
      </Space>
      <h3>{entry.title}</h3>
      {r?.team_name && <p>{r.team_name}</p>}
      {r?.contest_award && !r.is_starred && <p>学校认定：{r.result}</p>}
      {c?.levelName && <p>{c.levelName}</p>}
      <Space wrap>
        {entry.date && <Typography.Text type="secondary"><CalendarOutlined /> {entry.date}</Typography.Text>}
        {r?.source_url && <a href={r.source_url} target="_blank" rel="noopener noreferrer">证明来源 ↗</a>}
      </Space>
    </div>
  </Card>;
}

export default function PersonalHonorWall({ userId, isOwnProfile }: { userId: string | number; isOwnProfile: boolean }) {
  const [data, setData] = useState<Showcase | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  const [open, setOpen] = useState(false);
  const [draftKeys, setDraftKeys] = useState<string[]>([]);
  const [draftDefault, setDraftDefault] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState('');
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError(''); setData(null); setOpen(false);
    request.get<{ code: number; data: Showcase; msg?: string }>('/certificate/honor-wall/showcase', {
      params: { userId }, signal: controller.signal, timeout: 15000,
    }).then(({ data: response }) => {
      if (response.code !== 200 || !Array.isArray(response.data?.records)) throw new Error(response.msg || '荣誉墙加载失败');
      if (!controller.signal.aborted) setData(response.data);
    }).catch(e => { if (!controller.signal.aborted) setError(teamError(e)); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [userId, retry]);

  const entries: Entry[] = [
    ...(data?.records || []).map(award => ({ key: `record-${award.id}`, title: award.title, date: award.start_date, award })),
    ...(data?.certificates || []).map(certificate => ({ key: `certificate-${certificate.id}`, title: certificate.name, date: certificate.awardDate, certificate })),
  ];
  const entryMap = new Map(entries.map(entry => [entry.key, entry]));
  const selected = data?.preferences.customized ? data.preferences.selectedKeys : entries.map(entry => entry.key);
  const visible = selected.map(key => entryMap.get(key)).filter((entry): entry is Entry => !!entry);
  const draftEntries = [...draftKeys.map(key => entryMap.get(key)).filter((entry): entry is Entry => !!entry), ...entries.filter(entry => !draftKeys.includes(entry.key))];
  const startEditing = () => {
    setDraftKeys(visible.map(entry => entry.key));
    setDraftDefault(!data?.preferences.customized); setSaveError(''); setOpen(true);
  };
  const move = (key: string, offset: number) => {
    const next = [...draftKeys];
    const index = next.indexOf(key);
    if (index < 0 || index + offset < 0 || index + offset >= next.length) return;
    [next[index], next[index + offset]] = [next[index + offset], next[index]];
    setDraftKeys(next); setDraftDefault(false);
  };
  const save = async () => {
    setSaving(true); setSaveError('');
    try {
      const response = draftDefault
        ? await request.delete('/certificate/honor-wall/showcase')
        : await request.put('/certificate/honor-wall/showcase', { selectedKeys: draftKeys });
      if (response.data.code !== 200) throw new Error(response.data.msg || '保存失败');
      setData(previous => previous && ({ ...previous, preferences: response.data.data }));
      setOpen(false); message.success('荣誉墙展示设置已保存');
    } catch (e) { setSaveError(teamError(e)); }
    finally { setSaving(false); }
  };

  return <ConfigProvider locale={zhCN}><div className="profile-records" data-record-section="honors">
    <div style={{ display: 'flex', justifyContent: 'space-between', flexWrap: 'wrap', gap: 12, marginBottom: 20 }}>
      <Space wrap>
        {data && <>
          <Tag title="括号内为打星等效奖项，单独统计，按等效奖项计入积分。">获奖 {data.records.filter(r => !r.is_starred).length}{data.records.some(r => r.is_starred) ? `（+${data.records.filter(r => r.is_starred).length}）` : ''}</Tag>
          {data.certificates.length > 0 && <Tag>证书荣誉 {data.certificates.length}</Tag>}
          {data.preferences.customized && <Typography.Text type="secondary">展示 {visible.length} 项</Typography.Text>}
        </>}
      </Space>
      {isOwnProfile && <Space wrap>
        <Button icon={<SettingOutlined />} onClick={startEditing} disabled={!data || loading}>管理展示</Button>
        <Link href="/history-awards">认领历史战绩</Link><Link href="/team-space">维护我的档案</Link>
      </Space>}
    </div>
    {error && <Alert type="warning" showIcon title={error} action={<Button size="small" onClick={() => setRetry(value => value + 1)}>重试</Button>} />}
    <Skeleton loading={loading} active paragraph={{ rows: 4 }}>
      {!error && !visible.length && <Empty description={entries.length ? '暂未展示荣誉' : '暂无荣誉记录'} />}
      <div className="honor-grid">{visible.map(entry => <HonorCard key={entry.key} entry={entry} />)}</div>
    </Skeleton>
    <Modal title="管理荣誉墙展示" open={open} width={820} onCancel={() => !saving && setOpen(false)}
      closable={!saving} maskClosable={!saving} footer={<Space wrap>
        <Button disabled={saving} onClick={() => { setDraftKeys(entries.map(entry => entry.key)); setDraftDefault(true); }}>恢复默认</Button>
        <Button disabled={saving} onClick={() => setOpen(false)}>取消</Button>
        <Button type="primary" loading={saving} onClick={save}>保存展示设置</Button>
      </Space>}>
      <Typography.Paragraph>勾选要展示的比赛，用上移、下移调整顺序。保存后，访客将按此顺序看到你的荣誉墙。</Typography.Paragraph>
      <Space wrap style={{ marginBottom: 12 }}>
        <Button size="small" disabled={saving} onClick={() => { setDraftKeys([...draftKeys, ...entries.map(e => e.key).filter(key => !draftKeys.includes(key))]); setDraftDefault(false); }}>全选</Button>
        <Button size="small" disabled={saving} onClick={() => { setDraftKeys([]); setDraftDefault(false); }}>全不选</Button>
        <Typography.Text type="secondary">已选 {draftKeys.length} / {entries.length} 项</Typography.Text>
      </Space>
      <Typography.Paragraph type="secondary">{draftDefault ? '默认展示全部荣誉，新增奖项自动加入。' : '仅展示所选荣誉；新增奖项可在这里继续勾选。'} 展示设置不影响获奖记录和积分。</Typography.Paragraph>
      {saveError && <Alert type="error" showIcon title={saveError} style={{ marginBottom: 12 }} />}
      <div style={{ maxHeight: '55vh', overflowY: 'auto' }}>
        {!entries.length && <Empty description="暂无可展示的荣誉" />}
        {draftEntries.map(entry => {
          const index = draftKeys.indexOf(entry.key);
          return <div key={entry.key} data-honor-choice={entry.key} style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 10, padding: '14px 2px', borderBottom: '1px solid #eef1f5' }}>
            <Checkbox checked={index >= 0} disabled={saving} onChange={e => {
              setDraftKeys(e.target.checked ? [...draftKeys, entry.key] : draftKeys.filter(key => key !== entry.key)); setDraftDefault(false);
            }} style={{ flex: '1 1 320px' }}>
              <Typography.Text strong>{index >= 0 ? `${index + 1}. ` : ''}{entry.title}</Typography.Text>
              <div style={{ marginTop: 5, color: '#7a8795' }}>{entry.date} · {entry.award?.is_starred ? '打星 · ' : ''}{entry.award?.contest_award || entry.award?.result || entry.certificate?.levelName || entry.certificate?.rankType}</div>
            </Checkbox>
            <Space size={4}>
              <Button size="small" icon={<ArrowUpOutlined />} aria-label={`上移 ${entry.title}`} disabled={saving || index <= 0} onClick={() => move(entry.key, -1)}>上移</Button>
              <Button size="small" icon={<ArrowDownOutlined />} aria-label={`下移 ${entry.title}`} disabled={saving || index < 0 || index === draftKeys.length - 1} onClick={() => move(entry.key, 1)}>下移</Button>
            </Space>
          </div>;
        })}
      </div>
    </Modal>
  </div></ConfigProvider>;
}
