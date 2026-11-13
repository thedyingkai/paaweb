"use client";

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { Alert, Button, Empty, Input, Skeleton, Space, Tabs, Tag } from 'antd';
import { CalendarOutlined, SearchOutlined, TeamOutlined } from '@ant-design/icons';
import axios from 'axios';
import { eventSignupPeriod, eventSignupCount } from '@/utils/eventSignup';
import { RecruitmentWindow, windowState, windowLabels, timeText, dayjs } from '../join-team/recruitment';
import ActivityLayout from '../contest/ContestNav';
import styles from './page.module.css';

interface EventItem {
  eventId: number; name: string; categoryName?: string; isTeam?: number; signupMode?: string;
  signupStart: string; signupEnd: string | null; eventStart: string; eventEnd: string;
  signupCount: number; maxSignup: number | null;
}

// Event API uses wall-clock timestamps in UTC+8; recruitment timestamps already include a zone.
const eventTime = (value: string) => dayjs(/(?:Z|[+-]\d{2}:\d{2})$/i.test(value) ? value : `${value.replace(' ', 'T')}+08:00`).utcOffset(8);

const eventStatus = (item: EventItem, now: number) => {
  if (now >= eventTime(item.eventEnd).valueOf()) return { key: 'ended', text: '已结束', color: 'default', order: 3 };
  if (now >= eventTime(item.eventStart).valueOf()) return { key: 'ongoing', text: '正在比赛中', color: 'red', order: 0 };
  if (item.signupMode === 'roster') return { key: 'upcoming', text: '名单已确定', color: 'blue', order: 2 };
  if (now >= eventTime(item.signupStart).valueOf() && (!item.signupEnd || now < eventTime(item.signupEnd).valueOf()))
    return { key: 'registering', text: '报名进行中', color: 'green', order: 1 };
  return { key: 'upcoming', text: now < eventTime(item.signupStart).valueOf() ? '报名未开始' : '等待开赛', color: 'blue', order: 2 };
};

export default function CompetitionListClient() {
  const [events, setEvents] = useState<EventItem[]>([]);
  const [windows, setWindows] = useState<RecruitmentWindow[]>([]);
  const [loading, setLoading] = useState(true);
  const [eventError, setEventError] = useState(false);
  const [windowError, setWindowError] = useState(false);
  const [reload, setReload] = useState(0);
  const [query, setQuery] = useState('');
  const [tab, setTab] = useState('all');
  const [now, setNow] = useState(Date.now());

  useEffect(() => { const timer = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(timer); }, []);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setEventError(false); setWindowError(false);
    const fetchData = async (path: string) => {
      const { data } = await axios.get(path, { signal: controller.signal, timeout: 15000 });
      if (data.code !== 200 || !Array.isArray(data.data)) throw new Error('Invalid response');
      return data.data;
    };
    void Promise.allSettled([fetchData('/api/event/list'), fetchData('/api/team-recruitment/windows')]).then(([contests, recruitment]) => {
      if (controller.signal.aborted) return;
      if (contests.status === 'fulfilled') setEvents(contests.value); else setEventError(true);
      if (recruitment.status === 'fulfilled') setWindows(recruitment.value); else setWindowError(true);
      setLoading(false);
    });
    return () => controller.abort();
  }, [reload]);

  const visible = useMemo(() => events.filter(event => {
    const key = query.trim().toLowerCase();
    return (!key || event.name.toLowerCase().includes(key) || event.categoryName?.toLowerCase().includes(key))
      && (tab === 'all' || eventStatus(event, now).key === tab);
  }).sort((a, b) => eventStatus(a, now).order - eventStatus(b, now).order
    || (eventStatus(a, now).key === 'ended' ? eventTime(b.eventStart).valueOf() - eventTime(a.eventStart).valueOf() : eventTime(a.eventStart).valueOf() - eventTime(b.eventStart).valueOf())), [events, query, tab, now]);
  const recruitment = windows.filter(window => ['upcoming', 'open'].includes(windowState(window, now)))
    .sort((a, b) => dayjs(a.opens_at).valueOf() - dayjs(b.opens_at).valueOf());

  return <ActivityLayout><section className={styles.page}>
    <header className={styles.heading}>
      <div><h1>赛事</h1><p>比赛安排、参赛名单与校队选拔通知</p></div>
      <nav className={styles.links} aria-label="赛事相关功能">
        <Link href="/team-space">我的参赛档案</Link><Link href="/honor">历年战绩</Link><Link href="/community">校队交流区</Link>
      </nav>
    </header>

    <Skeleton loading={loading} active paragraph={{ rows: 5 }}>
      {windowError && <Alert type="warning" showIcon message="校队选拔通知暂时未能加载" action={<Button href="/join-team">查看申请页</Button>} style={{ marginBottom: 16 }} />}
      {recruitment.map(window => <aside key={window.id} className={styles.recruitment}>
        <div className={styles.noticeMain}>
          <div className={styles.noticeTitle}><TeamOutlined /><h2>{window.title}</h2><Tag color={windowState(window, now) === 'open' ? 'green' : 'blue'}>{windowLabels[windowState(window, now)]}</Tag></div>
          <p><strong>{timeText(window.opens_at)}</strong> 开放 · <strong>{timeText(window.closes_at)}</strong> 截止（北京时间）</p>
        </div>
        <Button type="primary" href="/join-team">{windowState(window, now) === 'open' ? '查看条件并申请' : '查看申请条件'}</Button>
      </aside>)}
      <div className={styles.toolbar}>
        <h2>比赛列表</h2>
        <Input aria-label="搜索比赛" prefix={<SearchOutlined />} allowClear placeholder="搜索比赛名称" value={query} onChange={event => setQuery(event.target.value)} />
      </div>
      <Tabs activeKey={tab} onChange={setTab} items={[{ key: 'all', label: '全部赛事' }, { key: 'registering', label: '正在报名' }, { key: 'upcoming', label: '即将开始' }, { key: 'ongoing', label: '进行中' }, { key: 'ended', label: '历史赛事' }]} />
      {eventError && <Alert type="error" showIcon message="比赛列表加载失败，请重试" action={<Button onClick={() => setReload(value => value + 1)}>重新加载</Button>} />}
      <div className={styles.events}>
        {visible.map(event => {
          const status = eventStatus(event, now), fixed = event.signupMode === 'roster';
          return <article className={styles.event} key={event.eventId}>
            <div className={styles.eventHeading}>
              <h2><Link href={`/contest/signup/${event.eventId}`}>{event.name}</Link></h2><Tag color={status.color}>{status.text}</Tag>
            </div>
            <Space wrap size={[8, 8]}><Tag>{event.isTeam ? '团队赛' : '个人赛'}</Tag><span className={styles.count}>{fixed ? `确定参赛名单 ${event.signupCount || 0} 人` : `报名人数：${eventSignupCount(event.signupCount, event.maxSignup)}`}</span></Space>
            <p className={styles.schedule}><CalendarOutlined /> 比赛时间：<strong>{eventTime(event.eventStart).format('YYYY-MM-DD HH:mm')} 至 {eventTime(event.eventEnd).format('YYYY-MM-DD') === eventTime(event.eventStart).format('YYYY-MM-DD') ? eventTime(event.eventEnd).format('HH:mm') : eventTime(event.eventEnd).format('YYYY-MM-DD HH:mm')}</strong></p>
            <div className={styles.eventBottom}>
              <p>{fixed ? '按确定名单参赛，无需重新报名。登录后可查看本人参赛信息。' : `报名时间：${eventSignupPeriod(event.signupStart, event.signupEnd)}`}</p>
              <Space wrap><Button type="primary" href={`/contest/signup/${event.eventId}`}>查看比赛详情</Button><Button href={`/contest/signup/${event.eventId}/list`}>参赛名单</Button></Space>
            </div>
          </article>;
        })}
        {!visible.length && !eventError && <Empty description={query || tab !== 'all' ? '没有符合条件的比赛' : '暂无已发布的比赛'} />}
      </div>
      <p className={styles.footnote}>其他比赛可在 <Link href="/team-space">个人参赛档案</Link> 中自愿登记。<Link href="/join-team">查看校队申请</Link></p>
    </Skeleton>
  </section></ActivityLayout>;
}
