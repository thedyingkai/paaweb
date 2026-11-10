"use client";

import { Button, Card, Divider, Skeleton, Space, Tag, Typography } from 'antd';
import {
  ClockCircleOutlined,
  DownloadOutlined,
  EyeOutlined,
  LeftOutlined,
  UserOutlined
} from '@ant-design/icons';
import axios from 'axios';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import SiteHeader from '../components/SiteHeader';
import MarkdownContent from '../components/content/MarkdownContent';
import PageLoader from '../components/PageLoader';

const { Title, Text } = Typography;

interface Attachment {
  fileId: string | number;
  name: string;
}

interface NoticeDetail {
  title: string;
  publisherName?: string;
  createTime?: string;
  viewCount?: number;
  content?: string;
  attachments?: Attachment[];
}

interface NoticeDetailClientProps {
  id: string;
}

const NoticeDetailClient = ({ id }: NoticeDetailClientProps) => {
  const router = useRouter();
  const pathname = usePathname();
  const type = pathname.split('/')[1] || 'notice';
  const [data, setData] = useState<NoticeDetail | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchDetail = async () => {
      setLoading(true);
      try {
        const res = await axios.get<{ code: number; data?: NoticeDetail }>(`/api/notice/detail/${id}`);
        setData(res.data.data || null);
      } finally {
        setLoading(false);
      }
    };

    fetchDetail().catch(() => setLoading(false));
  }, [id]);

  return (
    <>
      <PageLoader loading={loading} />
      <SiteHeader />
      <main className="content-page-shell">
        <Card className="detail-card" variant="borderless" style={{ width: 'min(980px, 100%)', margin: '0 auto' }}>
          <Button className="bits-ghost-button" icon={<LeftOutlined />} onClick={() => router.back()}>
            返回上一页
          </Button>

          <Skeleton loading={loading} active paragraph={{ rows: 10 }}>
            {data ? (
              <>
                <header className="detail-header" style={{ textAlign: 'center', marginBottom: 32 }}>
                  <Tag className="bits-pill" style={{ marginBottom: 12 }}>NOTICE DETAIL</Tag>
                  <Title level={2} style={{ margin: 0, fontSize: 32, fontWeight: 800 }}>{data.title}</Title>
                  <Space className="detail-meta" size={[18, 8]} wrap style={{ justifyContent: 'center', marginTop: 12 }}>
                    <Text><UserOutlined /> {data.publisherName || '校队'}</Text>
                    <Text><ClockCircleOutlined /> {data.createTime || '时间未知'}</Text>
                    <Text><EyeOutlined /> {data.viewCount || 0} 次浏览</Text>
                  </Space>
                </header>
                <Divider />
                <div className="center-readable">
                  <MarkdownContent content={data.content} />
                </div>
                {(data.attachments || []).length > 0 && (
                  <>
                    <Divider />
                    <div className="attachment-panel">
                      <strong><DownloadOutlined /> 附件下载</strong>
                      <Space direction="vertical">
                        {(data.attachments || []).map(file => (
                          <a key={file.fileId} href={`/api/file/download/${file.fileId}`}>
                            {file.name}
                          </a>
                        ))}
                      </Space>
                    </div>
                  </>
                )}
              </>
            ) : (
              <div className="bits-empty">公告不存在或暂时无法访问</div>
            )}
          </Skeleton>
        </Card>
      </main>
    </>
  );
};

export default NoticeDetailClient;
