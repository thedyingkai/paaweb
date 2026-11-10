import { Metadata } from 'next';
import HonorWallClient from './SchoolHonorsClient';

export const metadata: Metadata = {
  title: '校队荣誉与参赛档案 · BESTI ACM',
};

export default function HonorWallPage() {
  return <HonorWallClient />;
}
