"use client";

import React, { useEffect, useState } from 'react';
import { Table, Button, Card, Tag, Space, message, Modal, Input, Image } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined } from '@ant-design/icons';
import axios from 'axios';
import { useNavigate } from '@/app/admin/_legacy/router';

const ContestCategoryManager: React.FC = () => {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const navigate = useNavigate();

  const fetchData = async (page = 1) => {
    setLoading(true);
    try {
      const res = await axios.get('/api/contest/category/list', {
        params: { current: page, size: pagination.pageSize, keyword, admin: true }
      });
      if (res.data.code === 200) {
        setData(res.data.data.records);
        setPagination({ ...pagination, current: page, total: res.data.data.total });
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const handleDelete = (id: number) => {
    Modal.confirm({
      title: '确认删除',
      content: '删除后无法恢复，确定要删除该赛事吗？',
      okType: 'danger',
      onOk: async () => {
        const res = await axios.delete(`/api/contest/category/delete/${id}`);
        if (res.data.code === 200) {
          message.success('删除成功');
          fetchData(pagination.current);
        } else {
          message.error(res.data.msg);
        }
      }
    });
  };

  const levelMap: any = {
    national: { text: '国家级', color: 'red' },
    provincial: { text: '省部级', color: 'volcano' },
    city: { text: '市级', color: 'orange' },
    school: { text: '校级', color: 'blue' },
    department: { text: '院级', color: 'green' },
  };

  const columns = [
    {
      title: 'Logo',
      dataIndex: 'logoUrl',
      key: 'logoUrl',
      render: (url: string) => url ? 
        <Image src={`/api${url}`} alt="logo" width={40} height={40} style={{ objectFit: 'contain', borderRadius: 4 }} /> : 
        <div style={{ width: 40, height: 40, background: '#f0f0f0', borderRadius: 4, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#999', fontSize: 10 }}>无</div>
    },
    { title: '赛事名称', dataIndex: 'name', key: 'name' },
    {
      title: '级别',
      dataIndex: 'level',
      key: 'level',
      render: (level: string) => <Tag color={levelMap[level]?.color || 'default'}>{levelMap[level]?.text || level}</Tag>
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: number) => status === 1 ? <Tag color="success">上架</Tag> : <Tag color="default">下架</Tag>
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: any) => (
        <Space>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => navigate(`/admin/contest/category/edit/${record.categoryId}`)}>编辑</Button>
          <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => handleDelete(record.categoryId)}>删除</Button>
        </Space>
      )
    }
  ];

  return (
    <Card title="赛事库管理" extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/admin/contest/category/add')}>新增赛事</Button>}>
      <div style={{ marginBottom: 16, display: 'flex', gap: 8 }}>
        <Input 
          placeholder="搜索赛事名称" 
          value={keyword} 
          onChange={e => setKeyword(e.target.value)} 
          style={{ width: 240 }} 
          onPressEnter={() => fetchData(1)}
          allowClear
        />
        <Button type="primary" icon={<SearchOutlined />} onClick={() => fetchData(1)}>搜索</Button>
      </div>
      <Table
        rowKey="categoryId"
        columns={columns}
        dataSource={data}
        loading={loading}
        pagination={{
          ...pagination,
          showTotal: (total) => `共 ${total} 条`,
          onChange: (page) => fetchData(page)
        }}
      />
    </Card>
  );
};

export default ContestCategoryManager;