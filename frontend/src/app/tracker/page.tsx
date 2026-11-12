"use client";
import { schoolOjProblemUrl } from '@/utils/schoolOj';

/* eslint-disable react-hooks/set-state-in-effect */

import React, { useEffect, useState } from 'react';
import {
  Button,
  Card,
  Checkbox,
  Col,
  DatePicker,
  Empty,
  Form,
  Input,
  Layout,
  List,
  Modal,
  Result,
  Row,
  Select,
  Spin,
  Statistic,
  Table,
  Tabs,
  Tag,
  Typography,
  message
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  BarChartOutlined,
  CalendarOutlined,
  ExportOutlined,
  FireOutlined,
  LinkOutlined,
  LoginOutlined,
  ReloadOutlined,
  RightOutlined,
  SearchOutlined,
  TrophyOutlined,
  LockOutlined,
  HomeOutlined
} from '@ant-design/icons';
import ReactECharts from 'echarts-for-react';
import axios from 'axios';
import dayjs from 'dayjs';
import { useRouter } from 'next/navigation';
import SiteHeader from '../components/SiteHeader';
import { TrackerService } from '@/services/tracker.service';
import { readStoredUser } from '@/utils/auth';
import PageLoader from '../components/PageLoader';

const { Sider, Content } = Layout;
const { Title, Text } = Typography;
const { Option } = Select;
const { RangePicker } = DatePicker;

const DIFF_RANGES = [
  'N/A', '<1200', '1200-1399', '1400-1599', '1600-1899',
  '1900-2099', '2100-2399', '2400-2599', '2600-2999', '3000+'
];

const DIFF_COLORS = [
  '#bfbfbf', '#cccccc', '#77ff77', '#77ddff', '#aaaaff',
  '#ff88ff', '#ffcc88', '#ff7777', '#ff3333', '#aa0000'
];

interface PlatformStat {
  name: string;
  count: number;
  color: string;
}

interface ChartScope {
  difficultyDistribution: number[];
  timeLabels: string[];
  timeValues: number[];
}

interface DashboardData {
  totalSolved: number;
  last7DaysSolved: number;
  last30DaysSolved: number;
  lastYearSolved: number;
  platformStats: PlatformStat[];
  scope7Days: ChartScope;
  scope30Days: ChartScope;
  scope1Year: ChartScope;
  dailyHeatmap: [string, number][];
  maxDifficultyHeatmap: [string, number][];
}

interface UpcomingContest {
  upcomingContestsId: number;
  contestsName: string;
  contestsStarttime: string;
  url: string;
  sourceId?: number;
}

interface OjAccounts {
  codeforcesId?: string;
  atcoderId?: string;
  newcoderId?: string;
  luoguId?: string;
  cwnuojId?: string;
  qojId?: string;
  ucupId?: string;
}

interface SubmissionRecord {
  practiceProblemId: number;
  sourceId: number;
  problemId: string;
  problemName: string;
  problemDifficulty?: string;
  acTime: string;
}

const getDifficultyColor = (diffStr: string) => {
  if (!diffStr || diffStr === 'N/A') return '#bfbfbf';
  const value = parseInt(diffStr.replace(/[^0-9]/g, ''), 10);
  if (Number.isNaN(value)) return '#bfbfbf';
  if (value < 1200) return '#999999';
  if (value < 1400) return '#389e0d';
  if (value < 1600) return '#08979c';
  if (value < 1900) return '#1d39c4';
  if (value < 2100) return '#722ed1';
  if (value < 2400) return '#fa8c16';
  return '#f5222d';
};

const getPlatformColor = (name: string) => {
  const map: Record<string, string> = {
    CodeForces: '#FFD700',
    AtCoder: '#000000',
    'QOJ / UCup': '#722ed1',
    Luogu: '#E74C3C',
    NowCoder: '#2ECC71',
    CWNU: '#3498DB', '学校 OJ': '#3498DB'
  };
  return map[name] || '#888';
};

const platformIdMap: Record<string, number> = { CodeForces: 1, AtCoder: 2, NowCoder: 3, Luogu: 4, CWNU: 5, 'QOJ / UCup': 6 };
const platformNameMap: Record<number, string> = { 1: 'CodeForces', 2: 'AtCoder', 3: 'NowCoder', 4: 'Luogu', 5: '学校 OJ', 6: 'QOJ / UCup', 7: 'QOJ / UCup' };

export default function TrackerPage() {
  const router = useRouter();
  const user = readStoredUser();
  const [loading, setLoading] = useState(true);
  const [dashboard, setDashboard] = useState<DashboardData | null>(null);
  const [accounts, setAccounts] = useState<OjAccounts>({});
  const [selectedPlatforms, setSelectedPlatforms] = useState<string[]>([]);
  const [syncPlatforms, setSyncPlatforms] = useState<string[]>([]);
  const [syncing, setSyncing] = useState(false);
  const [syncForm] = Form.useForm<{ clientId?: string; uid?: string; qojCookie?: string }>();
  const [chartScope, setChartScope] = useState<'7d' | '30d' | '1y'>('30d');
  const [selectedYear, setSelectedYear] = useState<number>(dayjs().year());
  const [tableData, setTableData] = useState<SubmissionRecord[]>([]);
  const [tableTotal, setTableTotal] = useState(0);
  const [tableLoading, setTableLoading] = useState(false);
  const [queryForm] = Form.useForm();
  const [query, setQuery] = useState({ page: 1, size: 20 });

  const loadSubmissions = async () => {
    setTableLoading(true);
    try {
      const values = queryForm.getFieldsValue();
      const payload = {
        ...values,
        startTime: values.timeRange?.[0]?.format('YYYY-MM-DD'),
        endTime: values.timeRange?.[1]?.format('YYYY-MM-DD'),
        page: query.page,
        size: query.size
      };
      delete payload.timeRange;

      const res = await axios.post('/api/tracker/submissions', payload);
      if (res.data?.data) {
        setTableData(res.data.data.records || []);
        setTableTotal(res.data.data.total || 0);
      }
    } catch (error) {
      console.error(error);
    } finally {
      setTableLoading(false);
    }
  };

  const loadAllData = async () => {
    setLoading(true);
    try {
      const [dashboardRes, accountRes] = await Promise.all([
        axios.get<{ code: number; data?: DashboardData }>('/api/tracker/dashboard'),
        axios.get<{ code: number; data?: OjAccounts }>(`/api/user/profile/${user?.userId}`)
      ]);

      setDashboard(dashboardRes.data?.data || null);
      setAccounts(accountRes.data?.data || {});
      loadSubmissions();
    } catch (error) {
      console.error(error);
      message.error('加载数据失败，请检查网络');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    document.title = "CP Tracker";
    if (user) loadAllData();
    else setLoading(false);
  }, [user?.userId]);

  useEffect(() => {
    const bound: string[] = [];
    if (accounts.codeforcesId) bound.push('CodeForces');
    if (accounts.atcoderId) bound.push('AtCoder');
    if (accounts.newcoderId) bound.push('NowCoder');
    if (accounts.luoguId) bound.push('Luogu');
    if (accounts.cwnuojId) bound.push('CWNU');
    if (accounts.qojId) bound.push('QOJ');
    if (accounts.ucupId) bound.push('UCup');
    setSelectedPlatforms(bound);
  }, [accounts]);

  useEffect(() => {
    if (user) loadSubmissions();
  }, [query]);

  if (!user) {
    return (
      <>
        <SiteHeader />
        <div className="tracker-page-shell" style={{ minHeight: '100vh', display: 'flex', justifyContent: 'center', alignItems: 'center', padding: '24px 16px' }}>
          <Card 
            className="bits-card" 
            variant="borderless" 
            style={{ 
              width: 'min(480px, 100%)', 
              textAlign: 'center', 
              padding: '40px 24px', 
              background: 'rgba(255, 255, 255, 0.86)', 
              backdropFilter: 'blur(10px)',
              borderRadius: '24px',
              boxShadow: '0 20px 50px rgba(24, 144, 255, 0.1)'
            }}
          >
            <div style={{
              width: 80,
              height: 80,
              borderRadius: '50%',
              background: 'linear-gradient(135deg, #e6f7ff 0%, #bae7ff 100%)',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              marginBottom: 24,
              color: '#1677ff',
              fontSize: 36,
              boxShadow: '0 8px 24px rgba(24, 144, 255, 0.15)'
            }}>
              <LockOutlined />
            </div>
            <h2 style={{ 
              fontSize: 24, 
              fontWeight: 800, 
              marginBottom: 12, 
              background: 'linear-gradient(100deg, #17212b 0%, #1677ff 100%)',
              WebkitBackgroundClip: 'text',
              WebkitTextFillColor: 'transparent'
            }}>
              需要登录
            </h2>
            <p style={{ color: '#595959', fontSize: 15, lineHeight: 1.6, marginBottom: 28 }}>
              请登录后查看您的 CP Tracker 训练数据统计
            </p>
            <div style={{ display: 'flex', justifyContent: 'center', gap: 14 }}>
              <Button 
                type="primary" 
                size="large"
                icon={<LoginOutlined />} 
                onClick={() => router.push('/login')}
                style={{
                  background: 'linear-gradient(135deg, #1677ff 0%, #13c2c2 100%)',
                  border: 'none',
                  boxShadow: '0 8px 20px rgba(24, 144, 255, 0.25)',
                  height: 44,
                  borderRadius: 22,
                  padding: '0 28px',
                  fontWeight: 600
                }}
              >
                立即登录
              </Button>
              <Button 
                size="large"
                icon={<HomeOutlined />} 
                onClick={() => router.push('/')}
                style={{
                  height: 44,
                  borderRadius: 22,
                  padding: '0 24px',
                  borderColor: '#d9d9d9',
                  color: '#595959',
                  fontWeight: 600
                }}
              >
                返回首页
              </Button>
            </div>
          </Card>
        </div>
      </>
    );
  }

  const yearOptions = Array.from({ length: 6 }, (_, index) => dayjs().year() - index);

  const handleSync = () => {
    const hasAccount = accounts.codeforcesId || accounts.atcoderId || accounts.newcoderId || accounts.luoguId || accounts.cwnuojId || accounts.qojId || accounts.ucupId;
    if (!hasAccount) {
      message.warning('请先绑定至少一个OJ账号');
      return;
    }
    if (selectedPlatforms.length === 0) {
      message.warning('请至少选择一个要同步的平台');
      return;
    }

    setSyncPlatforms(selectedPlatforms);
  };

  const submitSync = async ({ clientId, uid, qojCookie }: { clientId?: string; uid?: string; qojCookie?: string }) => {
    const cookie = uid?.trim() ? `__client_id=${clientId?.trim()}; _uid=${uid.trim()}` : clientId?.trim();
    setSyncing(true);
    try {
      const response = await TrackerService.refreshTracker(syncPlatforms, cookie, qojCookie);
      if (response.data?.code !== 200) {
        message.error(response.data?.msg || '同步检查未通过');
        return;
      }
      syncForm.resetFields();
      setSyncPlatforms([]);
      message.success('同步请求已发送，请稍后刷新页面查看最新数据');
    } catch (error) {
      if (axios.isAxiosError(error)) message.error((error.response?.data as { message?: string })?.message || '同步请求发送失败');
      else message.error('同步请求发送失败');
    } finally {
      setSyncing(false);
    }
  };

  const jumpToProblem = (sourceId: number, problemId: string) => {
    if (!problemId) return;
    let url = '';
    const problem = problemId.trim();

    switch (sourceId) {
      case 1: {
        const codeforcesMatch = problem.match(/^CF(\d+)(.+)$/);
        if (codeforcesMatch) url = `https://codeforces.com/problemset/problem/${codeforcesMatch[1]}/${codeforcesMatch[2]}`;
        else url = `https://codeforces.com/problemset?order=BY_RATING_ASC&search=${problem}`;
        break;
      }
      case 2: {
        const parts = problem.split('_');
        url = `https://atcoder.jp/contests/${parts[0] || problem.replace(/[a-z]$/, '')}/tasks/${problem}`;
        break;
      }
      case 3:
        url = `https://ac.nowcoder.com/acm/problem/${problem.replace(/^NC/, '')}`;
        break;
      case 4:
        url = `https://www.luogu.com.cn/problem/${problem}`;
        break;
      case 6:
      case 7:
        url = `https://qoj.ac/problem/${problem.replace(/^QOJ/, '')}`;
        break;
      case 5:
        url = schoolOjProblemUrl(problem);
        break;
      default:
        message.info('该平台暂不支持自动跳转');
        return;
    }
    if (!url) { message.info('学校 OJ 暂未配置，请联系管理员'); return; }
    window.open(url, '_blank', 'noopener,noreferrer');
  };

  const scopeData = dashboard?.[chartScope === '7d' ? 'scope7Days' : chartScope === '30d' ? 'scope30Days' : 'scope1Year'];

  const getBarOption = () => {
    if (!scopeData) return {};
    return {
      title: { text: '难度分布 (数量)', left: 'center', textStyle: { fontSize: 14 } },
      tooltip: { trigger: 'axis' },
      grid: { left: '3%', right: '4%', bottom: '15%', containLabel: true },
      xAxis: { type: 'category', data: DIFF_RANGES, axisLabel: { interval: 0, rotate: 30, fontSize: 10 } },
      yAxis: { type: 'value' },
      series: [{
        data: scopeData.difficultyDistribution.map((value, index) => ({ value, itemStyle: { color: DIFF_COLORS[index] || '#3b82f6' } })),
        type: 'bar',
        barWidth: '60%'
      }]
    };
  };

  const getPieOption = () => {
    if (!scopeData) return {};
    const pieData = scopeData.difficultyDistribution
      .map((value, index) => ({ name: DIFF_RANGES[index], value, itemStyle: { color: DIFF_COLORS[index] } }))
      .filter(item => item.value > 0);
    return {
      title: { text: '难度分布 (占比)', left: 'center', textStyle: { fontSize: 14 } },
      tooltip: {
        trigger: 'item',
        formatter: '{b}: {c} ({d}%)',
        backgroundColor: 'rgba(255, 255, 255, 0.95)',
        textStyle: { color: '#333' },
        padding: 10
      },
      legend: { show: true, bottom: 0, left: 'center', width: '90%', itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 10 } },
      series: [{
        type: 'pie',
        center: ['50%', '45%'],
        radius: ['40%', '65%'],
        data: pieData,
        label: { show: false },
        labelLine: { show: false },
        itemStyle: { borderRadius: 5, borderColor: '#fff', borderWidth: 2 }
      }]
    };
  };

  const getLineOption = () => {
    if (!scopeData) return {};
    return {
      title: { text: chartScope === '1y' ? '每月解题数' : '每日解题数', left: 'center', textStyle: { fontSize: 14 } },
      tooltip: { trigger: 'axis' },
      grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
      xAxis: { type: 'category', data: scopeData.timeLabels },
      yAxis: { type: 'value' },
      series: [{ data: scopeData.timeValues, type: 'line', smooth: true, areaStyle: { opacity: 0.3 }, itemStyle: { color: '#10b981' } }]
    };
  };

  const getHeatmapOption = (isMaxDiff: boolean) => {
    if (!dashboard) return {};
    const data = isMaxDiff ? dashboard.maxDifficultyHeatmap : dashboard.dailyHeatmap;
    const visualMap = isMaxDiff ? {
      type: 'piecewise',
      orient: 'horizontal',
      left: 'center',
      bottom: 0,
      pieces: [
        { min: 3000, label: '3000+', color: '#aa0000' },
        { min: 2600, max: 2999, label: 'GM', color: '#ff3333' },
        { min: 2400, max: 2599, label: 'Red', color: '#ff7777' },
        { min: 2100, max: 2399, label: 'Master', color: '#ffcc88' },
        { min: 1900, max: 2099, label: 'CM', color: '#ff88ff' },
        { min: 1600, max: 1899, label: 'Expert', color: '#aaaaff' },
        { min: 1400, max: 1599, label: 'Spec', color: '#77ddff' },
        { min: 1200, max: 1399, label: 'Pupil', color: '#77ff77' },
        { max: 1199, label: 'Newbie', color: '#cccccc' }
      ],
      textStyle: { fontSize: 9 },
      itemWidth: 10,
      itemHeight: 10
    } : {
      type: 'piecewise',
      orient: 'horizontal',
      left: 'center',
      bottom: 0,
      pieces: [
        { min: 5, label: 'More', color: '#216e39' },
        { min: 3, max: 4, label: '3-4', color: '#30a14e' },
        { min: 1, max: 2, label: '1-2', color: '#9be9a8' },
        { value: 0, label: 'Less', color: '#ebedf0' }
      ],
      textStyle: { fontSize: 10 },
      itemWidth: 12,
      itemHeight: 12
    };

    return {
      tooltip: { position: 'top', formatter: (params: { data: [string, number] }) => `${params.data[0]}: ${params.data[1]}` },
      visualMap,
      calendar: {
        top: 25,
        left: 35,
        right: 20,
        cellSize: ['auto', 14],
        range: String(selectedYear),
        itemStyle: { color: '#ebedf0', borderWidth: 2, borderColor: '#ffffff' },
        yearLabel: { show: true, fontSize: 12, margin: 40 },
        dayLabel: { show: true, nameMap: 'en', firstDay: 1 },
        monthLabel: { show: true, nameMap: 'en' },
        splitLine: { show: false }
      },
      series: [{ type: 'heatmap', coordinateSystem: 'calendar', data, itemStyle: { borderWidth: 2, borderColor: '#ffffff' } }]
    };
  };

  const renderSidebar = () => (
    <Card variant="borderless" style={{ height: '100%', boxShadow: 'none', background: 'transparent' }} styles={{ body: { padding: 0 } }}>
      <div className="tracker-sidebar-block">
        <Title level={5} style={{ marginBottom: 12 }}>已绑定账号</Title>
        <p style={{ fontSize: 12, color: '#666' }}>自动合并本人及已确认队友的通过题目，重复题目只计一次。在个人主页「团队」中组队。</p>
        <div style={{ marginBottom: 10, fontSize: 12, color: '#999' }}>请勾选需要同步的平台：</div>
        <List size="small" split={false}>
          {[
            { name: 'CodeForces', id: accounts.codeforcesId },
            { name: 'AtCoder', id: accounts.atcoderId },
            { name: 'NowCoder', id: accounts.newcoderId },
            { name: 'Luogu', id: accounts.luoguId },
            { name: 'QOJ', id: accounts.qojId },
            { name: 'UCup', id: accounts.ucupId },
            { name: 'CWNU', id: accounts.cwnuojId }
          ].map(item => (
            <List.Item key={item.name} style={{ padding: '6px 0', border: 'none' }}>
              <div style={{ display: 'flex', alignItems: 'center', width: '100%' }}>
                <Checkbox
                  checked={selectedPlatforms.includes(item.name)}
                  disabled={!item.id}
                  onChange={event => setSelectedPlatforms(event.target.checked ? [...selectedPlatforms, item.name] : selectedPlatforms.filter(platform => platform !== item.name))}
                  style={{ marginRight: 8 }}
                />
                <div style={{ flex: 1 }}><Text style={{ fontSize: 13 }}>{item.name === 'CWNU' ? '学校 OJ' : item.name}</Text></div>
                {item.id ? <Tag color="success" style={{ marginRight: 0 }}>已绑定</Tag> : <Tag style={{ marginRight: 0 }}>未绑定</Tag>}
              </div>
            </List.Item>
          ))}
        </List>
        <div style={{ marginTop: 12 }}>
          <Button type="link" size="small" onClick={() => router.push(`/profile/${user.userId}`)} style={{ paddingLeft: 0 }}>
            <LinkOutlined /> 前往个人主页绑定
          </Button>
        </div>
        <Button type="primary" block icon={<ReloadOutlined />} onClick={handleSync} style={{ marginTop: 12 }} disabled={selectedPlatforms.length === 0}>
          同步刷题数据 ({selectedPlatforms.length})
        </Button>
      </div>

      <div className="tracker-sidebar-block">
        <Title level={5}>校队赛事与登记</Title>
        <p>联盟主办比赛、ICPC、CCPC、天梯赛由校队统一组织。</p>
        <p>其他比赛由成员自愿登记。</p>
        <Button type="link" onClick={() => router.push('/competitions')}>查看登记方式</Button>
      </div>
    </Card>
  );

  if (loading && !dashboard) {
    return (
      <>
        <PageLoader loading={loading} />
        <SiteHeader />
        <div className="tracker-page-shell" style={{ textAlign: 'center', padding: 50 }} />
      </>
    );
  }

  if (!dashboard) {
    return (
      <>
        <SiteHeader />
        <div className="tracker-page-shell"><Empty description="暂无数据" style={{ padding: 50 }} /></div>
      </>
    );
  }

  const columns: ColumnsType<SubmissionRecord> = [
    { title: '平台', dataIndex: 'sourceId', width: 100, render: value => { const name = platformNameMap[value] || 'Unknown'; return <Tag color={getPlatformColor(name)}>{name}</Tag>; } },
    { title: '题目ID', dataIndex: 'problemId', width: 120, render: (text, record) => <Button type="link" size="small" icon={<ExportOutlined />} onClick={() => jumpToProblem(record.sourceId, text)} style={{ padding: 0, fontFamily: 'monospace' }}>{text}</Button> },
    { title: '题目名称', dataIndex: 'problemName', ellipsis: true },
    { title: '难度', dataIndex: 'problemDifficulty', width: 100, render: text => text && text !== 'N/A' ? <Tag color={getDifficultyColor(text)}>{text}</Tag> : <Text type="secondary">-</Text> },
    { title: 'AC时间', dataIndex: 'acTime', width: 160, render: value => dayjs(value).format('YYYY-MM-DD HH:mm:ss') }
  ];

  return (
    <>
      <SiteHeader />
      <Layout className="tracker-page-shell">
        <Layout className="tracker-old-layout">
          <Content style={{ padding: 24, flex: 1, overflow: 'hidden', minWidth: 0 }}>
            <div style={{ marginBottom: 16 }}>
              <Title level={3} style={{ margin: 0 }}>CP Tracker 统计中心</Title>
              <Text type="secondary">全网算法竞赛做题数据聚合与分析平台</Text>
            </div>

            <Row gutter={16} style={{ marginBottom: 16 }}>
              <Col span={6}><Card variant="borderless" style={{ borderRadius: 8 }}><Statistic title="总解决题目" value={dashboard.totalSolved || 0} prefix={<TrophyOutlined />} valueStyle={{ color: '#1890ff' }} /></Card></Col>
              <Col span={6}><Card variant="borderless" style={{ borderRadius: 8 }}><Statistic title="近7天解决" value={dashboard.last7DaysSolved || 0} prefix={<FireOutlined />} valueStyle={{ color: '#52c41a' }} /></Card></Col>
              <Col span={6}><Card variant="borderless" style={{ borderRadius: 8 }}><Statistic title="近30天解决" value={dashboard.last30DaysSolved || 0} prefix={<BarChartOutlined />} valueStyle={{ color: '#fa8c16' }} /></Card></Col>
              <Col span={6}><Card variant="borderless" style={{ borderRadius: 8 }}><Statistic title="近1年解决" value={dashboard.lastYearSolved || 0} valueStyle={{ color: '#eb2f96' }} /></Card></Col>
            </Row>

            <Card title="平台分布" size="small" style={{ marginBottom: 16, borderRadius: 8 }}>
              <Row gutter={16} justify="start">
                {dashboard.platformStats?.map(stat => (
                  <Col key={stat.name} xs={12} sm={8} md={4} style={{ textAlign: 'center', marginBottom: 8 }}>
                    <div style={{ fontSize: 12, color: '#888' }}>{stat.name === 'CWNU' ? '学校 OJ' : stat.name}</div>
                    <div style={{ fontSize: 20, fontWeight: 'bold', color: stat.color }}>{stat.count}</div>
                  </Col>
                ))}
              </Row>
            </Card>

            <Card style={{ marginBottom: 16, borderRadius: 8 }} styles={{ body: { padding: '10px 24px 24px' } }}>
              <Tabs
                activeKey={chartScope}
                onChange={key => setChartScope(key as '7d' | '30d' | '1y')}
                items={[{ key: '7d', label: '近7天' }, { key: '30d', label: '近30天' }, { key: '1y', label: '近1年' }]}
              />
              <Row gutter={24}>
                <Col span={8}><div style={{ height: 280 }}><ReactECharts option={getBarOption()} style={{ height: '100%' }} /></div></Col>
                <Col span={8}><div style={{ height: 280 }}><ReactECharts option={getPieOption()} style={{ height: '100%' }} /></div></Col>
                <Col span={8}><div style={{ height: 280 }}><ReactECharts option={getLineOption()} style={{ height: '100%' }} /></div></Col>
              </Row>
            </Card>

            <Card
              title="年度热力图"
              size="small"
              style={{ marginBottom: 16, borderRadius: 8 }}
              extra={
                <Select value={selectedYear} onChange={setSelectedYear} style={{ width: 100 }} size="small">
                  {yearOptions.map(year => <Option key={year} value={year}>{year}年</Option>)}
                </Select>
              }
            >
              <Row gutter={24}>
                <Col span={24}>
                  <div style={{ marginBottom: 4, textAlign: 'center', fontWeight: 'bold', fontSize: 12 }}>每日解题数</div>
                  <ReactECharts option={getHeatmapOption(false)} style={{ height: 160 }} />
                </Col>
                <Col span={24} style={{ marginTop: 16 }}>
                  <div style={{ marginBottom: 4, textAlign: 'center', fontWeight: 'bold', fontSize: 12 }}>每日最大难度</div>
                  <ReactECharts option={getHeatmapOption(true)} style={{ height: 160 }} />
                </Col>
              </Row>
            </Card>

            <Card title="做题记录明细" size="small" style={{ borderRadius: 8 }}>
              <Form form={queryForm} layout="inline" onFinish={() => setQuery({ ...query, page: 1 })} style={{ marginBottom: 16, rowGap: 8 }}>
                <Form.Item name="sourceId"><Select placeholder="平台" style={{ width: 110 }} allowClear>{dashboard.platformStats?.map(platform => <Option key={platform.name} value={platformIdMap[platform.name]}>{platform.name === 'CWNU' ? '学校 OJ' : platform.name}</Option>)}</Select></Form.Item>
                <Form.Item name="difficultyRange"><Select placeholder="难度" style={{ width: 110 }} allowClear>{DIFF_RANGES.map((range, index) => <Option key={range} value={index.toString()}>{range}</Option>)}</Select></Form.Item>
                <Form.Item name="problemId"><Input placeholder="题目ID" style={{ width: 120 }} /></Form.Item>
                <Form.Item name="keyword"><Input placeholder="题目名称" style={{ width: 140 }} /></Form.Item>
                <Form.Item name="timeRange"><RangePicker style={{ width: 240 }} /></Form.Item>
                <Form.Item><Button type="primary" htmlType="submit" icon={<SearchOutlined />}>搜索</Button></Form.Item>
              </Form>
              <Table
                dataSource={tableData}
                rowKey="practiceProblemId"
                loading={tableLoading}
                size="small"
                pagination={{ current: query.page, pageSize: query.size, total: tableTotal, showSizeChanger: true, onChange: (page, size) => setQuery({ page, size }) }}
                columns={columns}
              />
            </Card>
          </Content>

          <Sider width={280} theme="light" style={{ background: 'transparent', border: 'none', marginLeft: 16 }}>
            {renderSidebar()}
          </Sider>
        </Layout>
      </Layout>
      <Modal
        open={syncPlatforms.length > 0}
        title={`同步 ${syncPlatforms.length} 个平台的刷题数据`}
        okText="开始同步"
        confirmLoading={syncing}
        onOk={() => syncForm.submit()}
        onCancel={() => setSyncPlatforms([])}
        destroyOnHidden
      >
        <p>数据同步将在后台进行，您可继续浏览其他页面。</p>
        <p>选中的平台：<b>{syncPlatforms.join(', ')}</b></p>
        <Form form={syncForm} layout="vertical" preserve={false} onFinish={submitSync}>
          {(syncPlatforms.includes('QOJ') || syncPlatforms.includes('UCup')) && (
            <>
              <p style={{ fontSize: 12, color: '#666' }}>QOJ 和 UCup 队伍账号共用 QOJ 的提交记录。首次同步请填写登录凭据；留空使用已保存的凭据。网站会检查能否读取完整记录；若遇到平台的浏览器验证，服务器无法自动完成验证，原题量会保留，重复粘贴 Cookie 不保证恢复。</p>
              <Form.Item label="QOJ Cookie" name="qojCookie" rules={[{ max: 16000, message: 'Cookie 过长' }, { pattern: /^[^\r\n]*$/, message: '请粘贴一整行 Cookie' }]}>
                <Input.Password placeholder="粘贴已登录 qoj.ac 的完整 Cookie 请求头" autoComplete="off" />
              </Form.Item>
              <details style={{ marginBottom: 16, fontSize: 12, color: '#666' }}>
                <summary>如何取得 Cookie</summary>
                <p>在浏览器登录 qoj.ac，按 F12 打开开发者工具，切换到「网络 / Network」并刷新页面。点击 qoj.ac 的请求，在「请求标头 / Request Headers」中找到 Cookie，复制它的完整值。不要复制 Set-Cookie，也不用填写账号密码。</p>
              </details>
            </>
          )}
          {syncPlatforms.includes('Luogu') && (
            <>
              <p style={{ fontSize: 12, color: '#666' }}>
                请分别填写同一次洛谷登录的两个值。如果登录的是已绑定账号，可以只填 __client_id。两项都留空则使用已保存的凭据。
              </p>
              <Form.Item
                label="洛谷 __client_id"
                name="clientId"
                rules={[{ transform: (value?: string) => value?.trim(), pattern: /^[^;=\s]+$/, message: '请只填写 __client_id 的值' }]}
              >
                <Input.Password placeholder="请输入 __client_id 的值" autoComplete="off" />
              </Form.Item>
              <Form.Item
                label="洛谷 _uid"
                name="uid"
                dependencies={['clientId']}
                rules={[
                  { transform: (value?: string) => value?.trim(), pattern: /^\d+$/, message: '请输入数字 UID' },
                  ({ getFieldValue }) => ({
                    validator(_, value?: string) {
                      if (value?.trim() && !getFieldValue('clientId')?.trim()) {
                        return Promise.reject(new Error('填写 _uid 时也请填写 __client_id'));
                      }
                      return Promise.resolve();
                    }
                  })
                ]}
              >
                <Input placeholder="请输入 _uid 的值" inputMode="numeric" />
              </Form.Item>
            </>
          )}
        </Form>
      </Modal>
    </>
  );
}
