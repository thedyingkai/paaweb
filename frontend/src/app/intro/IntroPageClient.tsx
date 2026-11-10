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

const departmentOrder = ['理事团', '办公室', '宣传部', '组织部', '技术部', '监察部'];

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
  const [selectedDept, setSelectedDept] = useState('');
  const [members, setMembers] = useState<MemberItem[]>([]);
  const [loading, setLoading] = useState(true);

  const groupedMembers = useMemo(() => {
    const groups = members.reduce<Record<string, MemberItem[]>>((acc, member) => {
      const department = member.department || '其他';
      acc[department] = acc[department] || [];
      acc[department].push(member);
      return acc;
    }, {});

    return Object.keys(groups)
      .sort((a, b) => {
        const ia = departmentOrder.indexOf(a);
        const ib = departmentOrder.indexOf(b);
        if (ia !== -1 && ib !== -1) return ia - ib;
        if (ia !== -1) return -1;
        if (ib !== -1) return 1;
        return a.localeCompare(b);
      })
      .map(department => ({ department, list: groups[department] }));
  }, [members]);

  useEffect(() => {
    if (groupedMembers.length > 0) {
      if (!selectedDept || !groupedMembers.find(g => g.department === selectedDept)) {
        setSelectedDept(groupedMembers[0].department);
      }
    } else {
      setSelectedDept('');
    }
  }, [groupedMembers, selectedDept]);

  useEffect(() => {
    const fetchIntro = async () => {
      setLoading(true);
      setContent('');
      setUpdateTime('');
      setMembers([]);

      try {
        if (category === 'association_member') {
          const termRes = await axios.get<{ code: number; data: string[] }>('/api/association-member/terms');
          const termList = [...(termRes.data.data || [])].sort((a, b) => b.localeCompare(a));
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

  const getPositionColor = (position?: string) => {
    if (position === '理事长' || position === '部长') return 'gold';
    if (position === '副理事长' || position === '副部长') return 'cyan';
    if (position === '团支书') return 'purple';
    return 'default';
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
                  {groupedMembers.length === 0 ? (
                    <>
                      <div className="member-control-panel member-control-panel-empty">
                        <div className="member-term-control">
                          <Text strong>届别筛选</Text>
                          <Select
                            className="bits-select"
                            value={selectedTerm || undefined}
                            onChange={handleTermChange}
                            placeholder="选择学年"
                            options={terms.map(term => ({ label: term, value: term }))}
                          />
                        </div>
                      </div>
                      <Empty description="该学年暂无成员数据" />
                    </>
                  ) : (
                    <>
                      <div className="member-control-panel">
                        <div className="member-term-control">
                          <Text strong>届别筛选</Text>
                          <Select
                            className="bits-select"
                            value={selectedTerm || undefined}
                            onChange={handleTermChange}
                            placeholder="选择学年"
                            options={terms.map(term => ({ label: term, value: term }))}
                          />
                        </div>
                        <div className="member-dept-tabs" aria-label="校队成员部门筛选">
                          {groupedMembers.map(group => (
                            <Button
                              key={group.department}
                              type={selectedDept === group.department ? "primary" : "default"}
                              shape="round"
                              onClick={() => setSelectedDept(group.department)}
                            >
                              {group.department}
                            </Button>
                          ))}
                        </div>
                      </div>

                      <Row gutter={[20, 20]} justify="center">
                        {(groupedMembers.find(g => g.department === selectedDept)?.list || []).map(member => {
                          let tagColor = 'blue';
                          if (member.position === '理事长' || member.position === '部长') {
                            tagColor = 'gold';
                          } else if (member.position === '副理事长' || member.position === '副部长') {
                            tagColor = 'cyan';
                          } else if (member.position === '团支书') {
                            tagColor = 'purple';
                          } else {
                            tagColor = 'default';
                          }

                          return (
                            <Col xs={24} sm={12} md={8} lg={6} key={member.memberId}>
                              <Card
                                hoverable
                                styles={{ body: { padding: '24px 20px', textAlign: 'center' } }}
                                style={{ borderRadius: 12, border: 'none', background: 'rgba(255,255,255,0.7)', backdropFilter: 'blur(8px)', boxShadow: '0 4px 24px rgba(0,0,0,0.02)' }}
                                onClick={() => { if (member.userId) router.push(`/profile/${member.userId}`); }}
                              >
                                <Avatar
                                  size={80}
                                  src={member.avatarUrl || '/avatar-default.svg'}
                                  style={{ border: '3px solid #fff', boxShadow: '0 4px 10px rgba(0,0,0,0.06)', marginBottom: 16 }}
                                />
                                <div style={{ fontSize: 18, fontWeight: 700, color: '#262626', marginBottom: 6 }}>
                                  {member.userId ? (
                                    <a
                                      href={`/profile/${member.userId}`}
                                      style={{ color: '#262626', textDecoration: 'none' }}
                                      onClick={(e) => { e.stopPropagation(); router.push(`/profile/${member.userId}`); }}
                                    >
                                      {member.realName}
                                    </a>
                                  ) : (
                                    member.realName || '未命名成员'
                                  )}
                                </div>
                                <div style={{ marginBottom: 12 }}>
                                  <Tag color={tagColor} style={{ padding: '2px 10px', borderRadius: 4, fontSize: 11, fontWeight: 600 }}>
                                    {`${member.department} · ${member.position}`}
                                  </Tag>
                                </div>
                                <p style={{
                                  color: '#8c8c8c',
                                  fontSize: 13,
                                  fontStyle: member.introduction ? 'italic' : 'normal',
                                  margin: 0,
                                  height: 38,
                                  overflow: 'hidden',
                                  display: '-webkit-box',
                                  WebkitLineClamp: 2,
                                  WebkitBoxOrient: 'vertical',
                                  lineHeight: 1.4
                                }}>
                                  {member.introduction || '“这个成员很懒，什么都没留下”'}
                                </p>
                              </Card>
                            </Col>
                          );
                        })}
                      </Row>
                    </>
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
