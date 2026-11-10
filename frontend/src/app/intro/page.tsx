import { Metadata } from 'next';
import IntroPageClient from './IntroPageClient';

export const metadata: Metadata = {
  title: '校队简介',
};

export default function IntroMainPage() {
  return <IntroPageClient category="association" />;
}
