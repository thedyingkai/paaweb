"use client";

import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Descriptions, Input, Result, Space, Tag, Typography } from 'antd';
import { CheckCircleFilled, HomeOutlined, SafetyCertificateOutlined, SearchOutlined } from '@ant-design/icons';
import { useRouter } from 'next/navigation';
import axios from 'axios';
import SiteHeader from '../../components/SiteHeader';

type VerifyResult = {
  found: boolean;
  valid: boolean;
  message: string;
  certificateNo?: string;
  certificateName?: string;
  certificateKind?: string;
  levelScope?: string;
  teamName?: string;
  awardName?: string;
  issueDate?: string;
  members?: Array<{ name: string; studentId?: string; school?: string }>;
};

interface CertificateVerifyPanelProps {
  embedded?: boolean;
}

export function CertificateVerifyPanel({ embedded = false }: CertificateVerifyPanelProps) {
  const [number, setNumber] = useState(() => {
    if (typeof window === 'undefined') return '';
    return new URLSearchParams(window.location.search).get('number')?.trim() || '';
  });
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<VerifyResult | null>(null);
  const autoVerifiedRef = useRef(false);
  const initialNumberRef = useRef(number);
  const router = useRouter();

  const verify = useCallback(async (value?: string) => {
    const normalized = (value ?? number).trim();
    if (!normalized) return;
    setLoading(true);
    setResult(null);
    try {
      const response = await axios.get('/api/certificate/grant/public/verify', { params: { number: normalized } });
      if (response.data.code === 200) setResult(response.data.data);
      else setResult({ found: false, valid: false, message: response.data.msg || '查询失败' });
    } catch {
      setResult({ found: false, valid: false, message: '查询服务暂时不可用，请稍后重试' });
    } finally {
      setLoading(false);
    }
  }, [number]);

  useEffect(() => {
    if (autoVerifiedRef.current || !initialNumberRef.current) return;
    autoVerifiedRef.current = true;
    queueMicrotask(() => verify(initialNumberRef.current));
  }, [verify]);

  return (
    <div
      className={embedded ? undefined : 'replica-page-panel'}
      style={{
        maxWidth: 920,
        margin: '0 auto',
        padding: embedded ? '10px 0 0' : 28,
        minHeight: embedded ? undefined : '72vh',
        background: embedded ? undefined : 'rgba(248, 252, 255, .9)'
      }}
    >
      <div style={{ textAlign: 'center', marginBottom: 28 }}>
        <SafetyCertificateOutlined style={{ fontSize: 52, color: '#1677ff' }} />
        <Typography.Title level={2} style={{ margin: '12px 0 6px' }}>电子证书真伪查询</Typography.Title>
        <Typography.Text type="secondary">输入证书右下角的完整编号，核验校队电子证书签发信息</Typography.Text>
      </div>

      <Card style={{ maxWidth: 720, margin: '0 auto' }}>
        <Input.Search
          size="large"
          value={number}
          onChange={event => setNumber(event.target.value)}
          onSearch={verify}
          enterButton={<><SearchOutlined /> 查询</>}
          loading={loading}
          placeholder="例如：PAA-2026-0001"
          maxLength={100}
        />
        <Alert
          type="info"
          showIcon
          message="历史证书说明"
          description="2026年以前的证书无编号，无法进行查询验真；2026年及以后发放的证书均有唯一编号，可通过本页面查验。"
          style={{ marginTop: 16 }}
        />

        {result && !result.found && (
          <Result status="warning" title="未查到有效签发记录" subTitle={result.message} />
        )}

        {result?.found && !result.valid && (
          <Result status="error" title="证书已撤销" subTitle={result.message}>
            <Descriptions bordered column={1} size="small">
              <Descriptions.Item label="证书编号">{result.certificateNo}</Descriptions.Item>
              <Descriptions.Item label="证书名称">{result.certificateName}</Descriptions.Item>
            </Descriptions>
          </Result>
        )}

        {result?.found && result.valid && (
          <div style={{ marginTop: 24 }}>
            <Result
              status="success"
              icon={<CheckCircleFilled style={{ color: '#52c41a' }} />}
              title="证书信息真实有效"
              subTitle={result.message}
            />
            <Descriptions bordered column={{ xs: 1, sm: 2 }}>
                  <Descriptions.Item label="证书编号" span={2}><Typography.Text copyable strong>{result.certificateNo}</Typography.Text></Descriptions.Item>
                  <Descriptions.Item label="证书名称">{result.certificateName}</Descriptions.Item>
                  <Descriptions.Item label="证书类型"><Tag color={result.certificateKind === '团队证书' ? 'purple' : 'green'}>{result.certificateKind}</Tag></Descriptions.Item>
                  {result.levelScope && <Descriptions.Item label="等级大类"><Tag color="geekblue">{result.levelScope}</Tag></Descriptions.Item>}
                  {result.teamName && <Descriptions.Item label="队伍名称" span={2}>{result.teamName}</Descriptions.Item>}
              <Descriptions.Item label="奖项">{result.awardName}</Descriptions.Item>
              <Descriptions.Item label="落款日期">{result.issueDate}</Descriptions.Item>
              <Descriptions.Item label="获奖人" span={2}>
                <Space wrap>{(result.members || []).map(member => (
                  <Tag color="blue" key={`${member.name}-${member.studentId || ''}-${member.school || ''}`}>
                    {member.name}{member.school ? `（${member.school}）` : ''}
                  </Tag>
                ))}</Space>
              </Descriptions.Item>
            </Descriptions>
          </div>
        )}
      </Card>

      {!embedded && (
        <div style={{ textAlign: 'center', marginTop: 24 }}>
          <Button icon={<HomeOutlined />} onClick={() => router.push('/')}>返回首页</Button>
        </div>
      )}
    </div>
  );
}

export default function CertificateVerifyClient() {
  return (
    <>
      <SiteHeader />
      <main className="content-page-shell">
        <CertificateVerifyPanel />
      </main>
    </>
  );
}
