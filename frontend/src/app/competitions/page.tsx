import type { Metadata } from 'next';
import CompetitionListClient from './CompetitionListClient';

export const metadata: Metadata = {
  title: '赛事 · BESTI ACM',
  description: '查看校赛、联赛的比赛安排、确定参赛名单及 ACM 校队选拔通知。',
};

export default function CompetitionsPage() {
  return <CompetitionListClient />;
}
