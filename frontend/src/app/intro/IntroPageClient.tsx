"use client";

/* eslint-disable react-hooks/set-state-in-effect */

import {
  Avatar,
  Card,
  Empty,
  Select,
  Skeleton,
  Tabs,
  Tag,
  Typography,
  Row,
  Col,
  Divider,
  Button
} from 'antd';
import {
  BookOutlined,
  CalendarOutlined,
  InfoCircleOutlined,
  TeamOutlined,
  UserOutlined
} from '@ant-design/icons';
import axios from 'axios';
import dayjs from 'dayjs';
import { useRouter } from 'next/navigation';
import { useEffect, useMemo, useState } from 'react';
import SiteHeader from '../components/SiteHeader';
import { memberRoles, memberRoleKey, positionLabel, positionStatusLabels, currentAcademicTerm } from '@/lib/besti-members';
import MarkdownContent from '../components/content/MarkdownContent';

const { Title, Paragraph, Text } = Typography;

type IntroCategory = 'association' | 'rules' | 'association_member';

interface IntroPageClientProps {
  category: IntroCategory;
}

interface IntroResponse {
  content?: string;
  updateTime?: string;
}

interface MemberItem {
  memberId: number | string;
  department?: string;
  position?: string;
  displayPosition?: string;
  positionStatus?: string;
  startDate?: string;
  endDate?: string;
  realName?: string;
  avatarUrl?: string;
  introduction?: string;
  userId?: number | string;
}

const titleMap: Record<IntroCategory, string> = {
  association: 'ACM 校队简介',
  rules: '校队赛事与登记规则',
  association_member: '校队成员'
};

const subtitleMap: Record<IntroCategory, string> = {
  association: '理解校队的目标、文化和日常活动',
  rules: '沉淀赛事规范，让每一次竞技更公平清晰',
  association_member: '认识正在共同维护校队运转的同学们'
};


const tabItems = [
  { key: 'association', label: '校队简介', icon: <InfoCircleOutlined /> },
  { key: 'rules', label: '竞赛规则', icon: <BookOutlined /> },
  { key: 'association_member', label: '校队成员', icon: <TeamOutlined /> }
];

const routeMap: Record<IntroCategory, string> = {
  association: '/intro',
  rules: '/contest/rule',
  association_member: '/member'
};

const IntroPageClient = ({ category }: IntroPageClientProps) => {
  const router = useRouter();
  const [content, setContent] = useState('');
  const [updateTime, setUpdateTime] = useState('');
  const [terms, setTerms] = useState<string[]>([]);
  const [selectedTerm, setSelectedTerm] = useState('');
  const [selectedDept, setSelectedDept] = useState('all');
  const [tenureFilter, setTenureFilter] = useState('all');
  const [members, setMembers] = useState<MemberItem[]>([]);
  const [loading, setLoading] = useState(true);

  const visibleMembers = useMemo(() => members
    .filter(member => selectedDept === 'all' || memberRoleKey(member.department, member.position) === selectedDept)
    .filter(member => tenureFilter === 'all' || member.positionStatus === tenureFilter),
    [members, selectedDept, tenureFilter]);

  useEffect(() => {
    const fetchIntro = async () => {
      setLoading(true);
      setContent('');
      setUpdateTime('');
      setMembers([]);

      try {
        if (category === 'association_member') {
          const termRes = await axios.get<{ code: number; data: string[] }>('/api/association-member/terms');
          const termList = Array.from(new Set([currentAcademicTerm(), ...(termRes.data.data || [])])).sort((a, b) => b.localeCompare(a));
          setTerms(termList);
          const defaultTerm = termList[0] || '';
          setSelectedTerm(defaultTerm);

          if (defaultTerm) {
            const memberRes = await axios.get<{ code: number; data: MemberItem[] }>('/api/association-member/list', {
              params: { term: defaultTerm }
            });
            setMembers(memberRes.data.data || []);
          }
          return;
        }

        const res = await axios.get<{ code: number; data?: IntroResponse }>(`/api/intro/${category}`);
        const data = res.data.data;
        setContent(data?.content || '');
        setUpdateTime(data?.updateTime ? dayjs(data.updateTime).format('YYYY-MM-DD HH:mm') : '');
      } finally {
        setLoading(false);
      }
    };

    fetchIntro().catch(() => setLoading(false));
  }, [category]);

  const handleTermChange = async (term: string) => {
    setSelectedTerm(term);
    setLoading(true);
    try {
      const res = await axios.get<{ code: number; data: MemberItem[] }>('/api/association-member/list', {
        params: { term }
      });
      setMembers(res.data.data || []);
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <SiteHeader />
      <main className="content-page-shell">
        <section className={`content-hero content-hero-980 ${category === 'association_member' ? 'member-hero-centered' : ''}`}>
          <Tag className="bits-pill">关于BESTI ACM</Tag>
          <Title className={`content-hero-title ${category === 'association_member' ? 'member-hero-title' : ''}`}>
            {titleMap[category]}
          </Title>
          <Paragraph>{subtitleMap[category]}</Paragraph>
        </section>

        <Card 
          className={category === 'association_member' ? "member-transparent-surface" : "content-main-card intro-content-card"}
          variant="borderless" 
          style={category === 'association_member' ? { 
            width: 'min(980px, 100%)', 
            margin: '0 auto', 
            background: 'transparent', 
            boxShadow: 'none',
            padding: 0
          } : { 
            width: 'min(980px, 100%)', 
            margin: '0 auto' 
          }}
        >
          {updateTime && (
            <div style={{ textAlign: 'center', marginBottom: 16 }}>
              <div className="intro-update-time" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6, color: '#8c8c8c', fontSize: 13 }}>
                <CalendarOutlined />
                <span>更新于 {updateTime}</span>
              </div>
            </div>
          )}
          {category !== 'association_member' && <Divider style={{ margin: '0 0 32px 0' }} />}

            <Skeleton loading={loading} active paragraph={{ rows: 8 }}>
              {category === 'association_member' ? (
                <div className="member-page">
                  <div className="member-control-panel">
                    <div className="member-term-control">
                      <Text strong>学年</Text>
                      <Select className="bits-select" value={selectedTerm || undefined} onChange={handleTermChange}
                        placeholder="选择学年" options={terms.map(term => ({ label: term, value: term }))} />
                    </div>
                    <div className="member-dept-tabs" aria-label="校队成员职务筛选">
                      {[{ value: 'all', label: '全部职务' }, ...memberRoles].map(role => (
                        <Button key={role.value} type={selectedDept === role.value ? 'primary' : 'default'}
                          shape="round" onClick={() => setSelectedDept(role.value)}>{role.label}</Button>
                      ))}
                    </div>
                    <Select aria-label="任职状态" value={tenureFilter} onChange={setTenureFilter} style={{ minWidth: 130 }} options={[
                      { value: 'all', label: '全部任期' }, ...Object.entries(positionStatusLabels).map(([value, label]) => ({ value, label })),
                    ]} />
                  </div>
                  <Paragraph type="secondary" style={{ textAlign: 'center' }}>任期结束后自动标记为“前”职务；可按学年查看历任成员。</Paragraph>
                  {visibleMembers.length === 0 ? <Empty description="该筛选下暂无成员数据" /> : (
                    <Row gutter={[20, 20]} justify="center">
                      {visibleMembers.map(member => (
                        <Col xs={24} sm={12} md={8} key={member.memberId}>
                          <Card hoverable={!!member.userId} styles={{ body: { padding: '24px 20px', textAlign: 'center' } }}
                            style={{ borderRadius: 12, border: 'none', background: 'rgba(255,255,255,0.7)' }}
                            onClick={() => { if (member.userId) router.push(`/profile/${member.userId}`); }}>
                            <Avatar size={80} src={member.avatarUrl || '/avatar-default.svg'} style={{ marginBottom: 16 }} />
                            <div style={{ fontSize: 18, fontWeight: 700, marginBottom: 10 }}>{member.realName || '未命名成员'}</div>
                            <Tag color={member.positionStatus === 'former' ? 'default' : 'blue'} style={{ whiteSpace: 'normal' }}>
                              {member.displayPosition || positionLabel(member.department, member.position, member.positionStatus)}
                            </Tag>
                            <div style={{ marginTop: 10 }}><Text type="secondary">{positionStatusLabels[member.positionStatus || 'unknown']}</Text></div>
                            <div style={{ fontSize: 12, margin: '6px 0 12px', color: '#888' }}>{member.startDate} — {member.endDate}</div>
                            <Paragraph ellipsis={{ rows: 2 }}>{member.introduction || '暂无个人简介'}</Paragraph>
                          </Card>
                        </Col>
                      ))}
                    </Row>
                  )}
                </div>
              ) : (
                <div className="center-readable">
                  <MarkdownContent content={content} />
                </div>
              )}
            </Skeleton>
          </Card>
      </main>
    </>
  );
};

export default IntroPageClient;
