import type { Metadata } from 'next';
import RecruitmentClient from './RecruitmentClient';
export const metadata: Metadata = { title: '申请加入校队 · BESTI ACM' };
export default function Page() { return <RecruitmentClient />; }
