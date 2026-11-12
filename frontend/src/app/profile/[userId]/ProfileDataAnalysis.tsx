"use client";

import { Card, Empty, Select, Segmented, Skeleton, Typography } from 'antd';
import ReactECharts from 'echarts-for-react';
import dayjs from 'dayjs';
import { useMemo, useState } from 'react';

const { Title } = Typography;

interface PlatformStat {
  name: string;
  count: number;
  color?: string;
}

interface DashboardData {
  totalSolved?: number;
  last7DaysSolved?: number;
  last30DaysSolved?: number;
  lastYearSolved?: number;
  platformStats?: PlatformStat[];
  dailyHeatmap?: [string, number][];
  maxDifficultyHeatmap?: [string, number][];
}

interface RatingHistoryItem {
  termName?: string;
  contestName?: string;
  ratingAll?: number;
  ratingContest?: number;
  ratingCertificate?: number;
  ratingPractice?: number;
}

interface ProfileDataAnalysisProps {
  loading?: boolean;
  dashboard: DashboardData | null;
  ratingHistory: RatingHistoryItem[];
}

const ratingNameMap: Record<string, string> = {
  ratingAll: '总分',
  ratingContest: '比赛',
  ratingCertificate: '奖项',
  ratingPractice: '刷题'
};

const ratingColorMap: Record<string, string> = {
  ratingAll: '#1677ff',
  ratingContest: '#fa8c16',
  ratingCertificate: '#7c3aed',
  ratingPractice: '#13a366'
};

const ProfileDataAnalysis = ({ loading, dashboard, ratingHistory }: ProfileDataAnalysisProps) => {
  const [ratingType, setRatingType] = useState('ratingAll');
  const [heatYear, setHeatYear] = useState(dayjs().year());

  const ratingOption = useMemo(() => {
    const recent = ratingHistory.slice(-8);
    const color = ratingColorMap[ratingType];
    return {
      tooltip: { trigger: 'axis' },
      grid: { left: 36, right: 24, top: 36, bottom: 54 },
      xAxis: {
        type: 'category',
        data: recent.map(item => item.termName || item.contestName || '记录'),
        boundaryGap: false,
        axisLabel: { rotate: 24, color: '#6d8495' }
      },
      yAxis: { type: 'value', axisLabel: { color: '#6d8495' }, splitLine: { lineStyle: { color: 'rgba(120,180,220,0.18)' } } },
      series: [{
        name: ratingNameMap[ratingType],
        data: recent.map(item => Number(item[ratingType as keyof RatingHistoryItem] || 0)),
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        itemStyle: { color },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: `${color}66` },
              { offset: 1, color: `${color}00` }
            ]
          }
        }
      }]
    };
  }, [ratingHistory, ratingType]);

  const getHeatmapOption = (source: [string, number][] = [], isDifficulty: boolean) => ({
    tooltip: {
      position: 'top',
      formatter: (params: { data: [string, number] }) => (
        isDifficulty ? `${params.data[0]} 最大难度：${params.data[1]}` : `${params.data[0]}：${params.data[1]} 题`
      )
    },
    visualMap: isDifficulty ? {
      type: 'piecewise',
      orient: 'horizontal',
      left: 'center',
      bottom: 0,
      pieces: [
        { min: 3000, label: '3000+', color: '#aa0000' },
        { min: 2400, max: 2999, label: 'Red+', color: '#ff3333' },
        { min: 1900, max: 2399, label: 'Master', color: '#ff88ff' },
        { min: 1600, max: 1899, label: 'Expert', color: '#aaaaff' },
        { min: 1200, max: 1599, label: 'Pupil', color: '#77d977' },
        { max: 1199, label: 'Newbie', color: '#d2dae2' }
      ],
      textStyle: { color: '#6d8495', fontSize: 10 }
    } : {
      type: 'piecewise',
      orient: 'horizontal',
      left: 'center',
      bottom: 0,
      pieces: [
        { min: 5, label: 'More', color: '#216e39' },
        { min: 3, max: 4, label: '3-4', color: '#30a14e' },
        { min: 1, max: 2, label: '1-2', color: '#9be9a8' },
        { value: 0, label: 'Less', color: '#e8eef3' }
      ],
      textStyle: { color: '#6d8495', fontSize: 10 }
    },
    calendar: {
      top: 26,
      left: 38,
      right: 22,
      cellSize: ['auto', 14],
      range: String(heatYear),
      itemStyle: { color: '#e8eef3', borderWidth: 2, borderColor: 'rgba(255,255,255,0.9)' },
      yearLabel: { show: false },
      dayLabel: { nameMap: 'en', firstDay: 1, color: '#7890a2' },
      monthLabel: { nameMap: 'en', color: '#7890a2' },
      splitLine: { show: false }
    },
    series: [{ type: 'heatmap', coordinateSystem: 'calendar', data: source }]
  });

  const yearOptions = Array.from({ length: 5 }, (_, index) => dayjs().year() - index).map(year => ({
    value: year,
    label: `${year}年`
  }));

  return (
    <Skeleton loading={loading} active paragraph={{ rows: 8 }}>
      <div className="profile-analysis">
        <Card className="soft-panel analysis-chart-card" variant="borderless">
          <div className="panel-title-row">
            <Title level={4}>Rating 学期变化趋势</Title>
            <Segmented
              value={ratingType}
              onChange={value => setRatingType(String(value))}
              options={[
                { label: '总分', value: 'ratingAll' },
                { label: '比赛', value: 'ratingContest' },
                { label: '奖项', value: 'ratingCertificate' },
                { label: '刷题', value: 'ratingPractice' }
              ]}
            />
          </div>
          {ratingHistory.length ? (
            <ReactECharts option={ratingOption} style={{ height: 360 }} />
          ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无学期 Rating 数据" />}
        </Card>

        <Card className="soft-panel analysis-chart-card" variant="borderless">
          <div className="panel-title-row">
            <Title level={4}>年度刷题热力图</Title>
            <Select value={heatYear} onChange={setHeatYear} options={yearOptions} style={{ width: 104 }} />
          </div>
          {dashboard ? (
            <>
              <div className="heatmap-title">每日解题数</div>
              <ReactECharts option={getHeatmapOption(dashboard.dailyHeatmap, false)} style={{ height: 180 }} />
              <div className="heatmap-title">每日最大难度</div>
              <ReactECharts option={getHeatmapOption(dashboard.maxDifficultyHeatmap, true)} style={{ height: 180 }} />
            </>
          ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无刷题数据" />}
        </Card>
      </div>
    </Skeleton>
  );
};

export default ProfileDataAnalysis;
