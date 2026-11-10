"use client";

import React, { useEffect, useMemo, useState } from 'react';
import { Button, Card, Col, Empty, Input, Row, Select, Skeleton, Space, Tag, Typography } from 'antd';
import { ArrowRightOutlined, SearchOutlined, TrophyOutlined } from '@ant-design/icons';
import axios from 'axios';
import SiteHeader from '../components/SiteHeader';
import PageLoader from '../components/PageLoader';
import Link from 'next/link';

const { Title, Paragraph } = Typography;

interface Term {
  termName: string;
}

// A single honor item stored in sys_intro content JSON array
interface HonorItem {
  uid: string;
  imageUrl: string;
  title?: string;
  academicYear?: string;
  sortOrder?: number;
}

const HonorWallClient = () => {
  const [rawHonors, setRawHonors] = useState<HonorItem[]>([]);
  const [availableYears, setAvailableYears] = useState<string[]>([]);
  const [selectedYear, setSelectedYear] = useState<string>('');
  const [loading, setLoading] = useState(true);
  const [keyword, setKeyword] = useState('');

  // 1. Fetch honors JSON
  useEffect(() => {
    const fetchAll = async () => {
      setLoading(true);
      try {
        const honorsRes = await axios.get<{ code: number; data?: { content?: string } }>('/api/intro/honors');

        // Parse honors JSON
        let parsed: HonorItem[] = [];
        if (honorsRes.data.code === 200 && honorsRes.data.data?.content) {
          try {
            const arr = JSON.parse(honorsRes.data.data.content);
            if (Array.isArray(arr)) parsed = arr;
          } catch (e) {
            console.error('荣誉墙数据解析失败', e);
          }
        }
        setRawHonors(parsed);

        // Extract unique academic years from honors, sorted descending
        const years = Array.from(
          new Set(parsed.map(h => h.academicYear).filter((y): y is string => !!y))
        ).sort((a, b) => b.localeCompare(a));
        setAvailableYears(years);

        // Default: latest year with tagged honors, or 'all' if none tagged
        setSelectedYear(years.length > 0 ? years[0] : 'all');
      } catch (e) {
        console.error(e);
      } finally {
        setLoading(false);
      }
    };
    fetchAll();
  }, []);

  // 2. Filter + sort client-side
  const filteredHonors = useMemo(() => {
    let list = rawHonors;

    // Academic year filter
    if (selectedYear && selectedYear !== 'all') {
      list = list.filter(h => h.academicYear === selectedYear);
    }

    // Keyword filter
    if (keyword.trim()) {
      const q = keyword.toLowerCase();
      list = list.filter(h => (h.title || '').toLowerCase().includes(q));
    }

    // Sort by sortOrder ascending (lower number = higher priority)
    return [...list].sort((a, b) => {
      const sa = a.sortOrder ?? 9999;
      const sb = b.sortOrder ?? 9999;
      return sa !== sb ? sa - sb : 0;
    });
  }, [rawHonors, selectedYear, keyword]);

  const yearOptions = [
    { value: 'all', label: '全部荣誉' },
    ...availableYears.map(y => ({ value: y, label: y + '学年' })),
  ];

  return (
    <>
      <PageLoader loading={loading} />
      <SiteHeader />
      <main className="content-page-shell">
        {/* Hero Section */}
        <section className="content-hero content-hero-centered content-hero-980">
          <Tag className="bits-pill" icon={<TrophyOutlined />}>HONOR WALL</Tag>
          <Title className="content-hero-title">校队荣誉墙</Title>
          <Paragraph>记录ACM 校队历年在各类竞赛与活动中取得的优异成绩</Paragraph>
        </section>

        {/* Filter & Search Bar */}
        <div style={{ width: 'min(1200px, 100%)', margin: '0 auto 24px' }}>
          <div style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            gap: 16,
            flexWrap: 'wrap',
            background: 'rgba(255,255,255,0.7)',
            padding: '12px 20px',
            borderRadius: 16,
            backdropFilter: 'blur(6px)',
            border: '1px solid rgba(24,144,255,0.08)',
          }}>
            <Space size={12}>
              <span style={{ fontWeight: 600, color: '#486174' }}>学年筛选:</span>
              <Select
                value={selectedYear}
                onChange={setSelectedYear}
                style={{ minWidth: 160 }}
                options={yearOptions}
              />
            </Space>
            <Input
              className="bits-input"
              prefix={<SearchOutlined />}
              placeholder="搜索荣誉名称..."
              value={keyword}
              onChange={e => setKeyword(e.target.value)}
              allowClear
              style={{ width: 260 }}
            />
          </div>
        </div>

        {/* Honor Cards */}
        <div style={{ width: 'min(1200px, 100%)', margin: '0 auto' }}>
          <Skeleton loading={loading} active paragraph={{ rows: 10 }}>
            {filteredHonors.length === 0 ? (
              <Card variant="borderless" style={{
                borderRadius: 24,
                background: 'rgba(255,255,255,0.8)',
                padding: '40px 0',
              }}>
                <Empty description={keyword ? '未找到匹配的荣誉记录' : '暂无荣誉记录，请管理员在后台添加'} />
              </Card>
            ) : (
              <Row gutter={[24, 24]}>
                {filteredHonors.map((item, idx) => (
                  <Col xs={24} sm={12} md={8} key={item.uid || idx}>
                    <Card
                      className="bits-card"
                      variant="borderless"
                      hoverable
                      cover={
                        item.imageUrl ? (
                          <div style={{ height: 200, overflow: 'hidden', borderRadius: '16px 16px 0 0', position: 'relative', zIndex: 1 }}>
                            <img
                              src={item.imageUrl}
                              alt={item.title || '荣誉'}
                              style={{
                                width: '100%',
                                height: '100%',
                                objectFit: 'cover',
                                transition: 'transform 0.4s ease',
                                position: 'relative',
                                zIndex: 2,
                              }}
                              className="honor-img"
                            />
                          </div>
                        ) : (
                          <div style={{
                            height: 160,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                            borderRadius: '16px 16px 0 0',
                            position: 'relative',
                            zIndex: 1
                          }}>
                            <TrophyOutlined style={{ fontSize: 56, color: 'rgba(255,255,255,0.85)' }} />
                          </div>
                        )
                      }
                      style={{
                        borderRadius: 16,
                        overflow: 'hidden',
                        background: 'rgba(255,255,255,0.88)',
                        backdropFilter: 'blur(8px)',
                        boxShadow: '0 8px 24px rgba(120,180,220,0.08)',
                        border: '1px solid rgba(24,144,255,0.07)',
                        height: '100%',
                      }}
                      styles={{ body: { padding: '20px 20px 20px' } }}
                    >
                      <div style={{
                        fontSize: 16,
                        fontWeight: 700,
                        color: '#17212b',
                        lineHeight: 1.5,
                        display: '-webkit-box',
                        WebkitLineClamp: 3,
                        WebkitBoxOrient: 'vertical',
                        overflow: 'hidden',
                        minHeight: 48,
                      }}>
                        {item.title || '校队荣誉'}
                      </div>
                      {availableYears.find(y => y === item.academicYear) && (
                        <Tag color="blue" style={{ marginTop: 12 }}>
                          {item.academicYear}学年
                        </Tag>
                      )}
                    </Card>
                  </Col>
                ))}
              </Row>
            )}
          </Skeleton>

          {/* Back to intro link */}
          <div style={{ textAlign: 'center', marginTop: 48 }}>
            <Link href="/intro">
              <Button
                type="dashed"
                shape="round"
                icon={<ArrowRightOutlined />}
                style={{ padding: '0 28px', height: 42 }}
              >
                了解校队更多
              </Button>
            </Link>
          </div>
        </div>
      </main>
    </>
  );
};

export default HonorWallClient;
