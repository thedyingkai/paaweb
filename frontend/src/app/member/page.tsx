import { Metadata } from 'next';
import IntroPageClient from '../intro/IntroPageClient';

export const metadata: Metadata = {
  title: { absolute: '校队成员' },
};

export default function MemberPage() {
  return <IntroPageClient category="association_member" />;
}
