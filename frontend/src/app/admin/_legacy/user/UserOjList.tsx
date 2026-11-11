"use client";

import React, { useState, useEffect } from 'react';
import { 
  Card, Table, Button, Input, Space, Modal, Form, 
  message, Breadcrumb, Row, Col
} from 'antd';
import { SearchOutlined, EditOutlined, ReloadOutlined, GlobalOutlined } from '@ant-design/icons';
import axios from 'axios';

const UserOjList: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<any[]>([]);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const [keyword, setKeyword] = useState('');
  
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<any>(null);
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async (page = 1, pageSize = pagination.pageSize) => {
    setLoading(true);
    try {
      const params = {
        page: page,
        size: pageSize,
        keyword: keyword
      };
      const res = await axios.get('/api/admin/user/oj-accounts', { params });
      if (res.data.code === 200) {
        setData(res.data.data.records);
        setPagination({
          current: res.data.data.current,
          pageSize: res.data.data.size,
          total: res.data.data.total
        });
      } else {
        message.error(res.data.msg);
      }
    } catch (error) {
      message.error('获取用户OJ账号列表失败');
    } finally {
      setLoading(false);
    }
  };

  const handleTableChange = (pag: any) => {
    fetchData(pag.current, pag.pageSize);
  };

  const handleSearch = () => {
    fetchData(1);
  };

  const handleReset = () => {
    setKeyword('');
    setTimeout(() => fetchData(1), 0);
  };

  const handleEdit = (record: any) => {
    setEditingUser(record);
    form.setFieldsValue({
      codeforcesId: record.codeforcesId,
      atcoderId: record.atcoderId,
      luoguId: record.luoguId,
      newcoderId: record.newcoderId,
      cwnuojId: record.cwnuojId
    });
    setIsModalOpen(true);
  };

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      
      const payload = { 
        userId: editingUser.userId,
        ...values 
      };
      
      const res = await axios.post('/api/admin/user/oj-account/update', payload);
      if (res.data.code === 200) {
        message.success('更新成功');
        setIsModalOpen(false);
        fetchData(pagination.current);
      } else {
        message.error(res.data.msg);
      }
    } catch (e) {
    } finally {
      setSubmitting(false);
    }
  };

  const columns = [
    { 
      title: '序号', 
      key: 'index', 
      width: 60, 
      align: 'center' as const, 
      render: (_: any, __: any, index: number) => (pagination.current - 1) * pagination.pageSize + index + 1 
    },
    { title: '昵称', dataIndex: 'userName', width: 150, ellipsis: true },
    { title: '真实姓名', dataIndex: 'realName', width: 100 },
    { title: '学号', dataIndex: 'numberId', width: 120 },
    { 
      title: 'Codeforces', 
      dataIndex: 'codeforcesId', 
      render: (text: string) => text || <span style={{color: '#ccc'}}>-</span> 
    },
    { 
      title: 'AtCoder', 
      dataIndex: 'atcoderId', 
      render: (text: string) => text || <span style={{color: '#ccc'}}>-</span> 
    },
    { 
      title: '洛谷', 
      dataIndex: 'luoguId', 
      render: (text: string) => text || <span style={{color: '#ccc'}}>-</span> 
    },
    { 
      title: '牛客', 
      dataIndex: 'newcoderId', 
      render: (text: string) => text || <span style={{color: '#ccc'}}>-</span> 
    },
    { 
      title: '学校OJ', 
      dataIndex: 'cwnuojId', 
      render: (text: string) => text || <span style={{color: '#ccc'}}>-</span> 
    },
    {
      title: '操作',
      key: 'action',
      width: 80,
      fixed: 'right' as const,
      render: (_: any, record: any) => (
        <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>编辑</Button>
      ),
    },
  ];

  return (
    <div style={{ padding: '24px' }}>
      <Breadcrumb style={{ marginBottom: '16px' }}>
        <Breadcrumb.Item>管理后台</Breadcrumb.Item>
        <Breadcrumb.Item>用户管理</Breadcrumb.Item>
        <Breadcrumb.Item>OJ账号管理</Breadcrumb.Item>
      </Breadcrumb>

      <Card>
        <Row gutter={16} style={{ marginBottom: 16 }}>
            <Col span={8}>
                <Input 
                    placeholder="搜索学号、账号、姓名、邮箱、手机号" 
                    prefix={<SearchOutlined style={{ color: '#ccc' }} />} 
                    value={keyword}
                    onChange={e => setKeyword(e.target.value)}
                    onPressEnter={handleSearch}
                    allowClear
                />
            </Col>
            <Col span={16} style={{ textAlign: 'left' }}>
                 <Space>
                    <Button type="primary" onClick={handleSearch} icon={<SearchOutlined />}>查询</Button>
                    <Button onClick={handleReset} icon={<ReloadOutlined />}>重置</Button>
                 </Space>
            </Col>
        </Row>

        <Table
          columns={columns}
          dataSource={data}
          rowKey="userId"
          pagination={{ 
            ...pagination, 
            showTotal: (total) => `共 ${total} 条`,
            showSizeChanger: true 
          }}
          loading={loading}
          onChange={handleTableChange}
          scroll={{ x: 1300 }}
          size="middle"
        />
      </Card>

      <Modal
        title={
            <Space>
                <GlobalOutlined />
                <span>编辑OJ账号信息 - {editingUser?.realName} ({editingUser?.numberId})</span>
            </Space>
        }
        open={isModalOpen}
        onOk={handleSave}
        onCancel={() => setIsModalOpen(false)}
        confirmLoading={submitting}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
            <Form.Item name="codeforcesId" label="Codeforces 账号 (昵称)">
                <Input placeholder="输入Codeforces Handle" />
            </Form.Item>
            <Form.Item name="atcoderId" label="AtCoder 账号 (昵称)">
                <Input placeholder="输入AtCoder Handle" />
            </Form.Item>
            <Form.Item name="luoguId" label="洛谷数字 UID" rules={[{ pattern: /^\d+$/, message: '请填写洛谷个人主页地址中的数字 UID' }]}>
                <Input placeholder="例如 123456（个人主页 /user/ 后的数字）" />
            </Form.Item>
            <Form.Item name="newcoderId" label="牛客竞赛数字 UID" rules={[{ pattern: /^\d+$/, message: '请填写牛客竞赛个人主页地址中的数字 UID' }]}>
                <Input placeholder="例如 123456（竞赛主页 /profile/ 后的数字）" />
            </Form.Item>
            <Form.Item name="cwnuojId" label="学校OJ 账号 (账号ID)">
                <Input placeholder="输入学校OJ账号" />
            </Form.Item>
            <div style={{color: '#999', fontSize: 12, marginTop: 10}}>
                注：修改后可能需要等待下一次爬虫运行才能更新最新的刷题数据。
            </div>
        </Form>
      </Modal>
    </div>
  );
};

export default UserOjList;
