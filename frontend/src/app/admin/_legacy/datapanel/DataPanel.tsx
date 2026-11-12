"use client";

import React, { useEffect, useState } from 'react';
import { Card, Row, Col, Statistic, Table, Form, Input, Select, Button, Space, Tag, message } from 'antd';
import { ReloadOutlined, UserOutlined, TrophyOutlined, RiseOutlined, CalendarOutlined, DownloadOutlined } from '@ant-design/icons';
import axios from 'axios';
import { getOverallStats, getUserPracticeStats, type OverallStats, type UserPracticeStats } from '@/services/data-panel.service';
import { useNavigate } from '@/app/admin/_legacy/router';

const { Option } = Select;

const DataPanel: React.FC = () => {
  const navigate = useNavigate();
  const [overallStats, setOverallStats] = useState<OverallStats | null>(null);
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<UserPracticeStats[]>([]);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  });

  // Filter states
  const [form] = Form.useForm();
  const [universities, setUniversities] = useState<any[]>([]);
  const [schools, setSchools] = useState<any[]>([]);
  const [majors, setMajors] = useState<any[]>([]);

  const debounceTimer = React.useRef<any>(null);

  useEffect(() => {
    fetchUniversities();
    fetchOverallStats();
    fetchData();
  }, []);

  const fetchOverallStats = async () => {
    try {
      const values = form.getFieldsValue();
      const res = await getOverallStats(values);
      if (res.code === 200) {
        setOverallStats(res.data);
      }
    } catch (error) {
      console.error('Failed to fetch overall stats', error);
    }
  };

  const fetchUniversities = async () => {
    try {
      const res = await axios.get('/api/common/universities');
      if (res.data.code === 200) setUniversities(res.data.data);
    } catch (e) {
      console.error(e);
    }
  };

  const fetchSchools = async (uniId: number) => {
    try {
      const res = await axios.get(`/api/common/schools/${uniId}`);
      if (res.data.code === 200) setSchools(res.data.data);
    } catch (e) {
      console.error(e);
    }
  };

  const fetchMajors = async (schoolId: number) => {
    try {
      const res = await axios.get(`/api/common/majors/${schoolId}`);
      if (res.data.code === 200) setMajors(res.data.data);
    } catch (e) {
      console.error(e);
    }
  };

  // Generate grade years (last 6 years)
  const currentYear = new Date().getFullYear();
  const gradeYears = Array.from({ length: 6 }, (_, i) => String(currentYear - i));

  const fetchData = async (page = pagination.current, size = pagination.pageSize) => {
    setLoading(true);
    try {
      const values = form.getFieldsValue();
      const res = await getUserPracticeStats({
        page,
        size,
        ...values
      });
      
      if (res.code === 200) {
        setData(res.data.records);
        setPagination({
          current: res.data.current,
          pageSize: res.data.size,
          total: res.data.total,
        });
      }
    } catch (error) {
      console.error('Failed to fetch user stats', error);
    } finally {
      setLoading(false);
    }
  };

  const handleTableChange = (newPagination: any) => {
    fetchData(newPagination.current, newPagination.pageSize);
  };

  const handleValuesChange = (changedValues: any, allValues: any) => {
    if ('universityId' in changedValues) {
        onUniversityChange(changedValues.universityId);
    }
    if ('schoolId' in changedValues) {
        onSchoolChange(changedValues.schoolId);
    }

    if (debounceTimer.current) {
        clearTimeout(debounceTimer.current);
    }

    debounceTimer.current = setTimeout(() => {
        fetchOverallStats();
        fetchData(1);
    }, 500);
  };

  const onUniversityChange = (value: number) => {
    form.setFieldsValue({ schoolId: undefined, majorId: undefined });
    setSchools([]);
    setMajors([]);
    if (value) fetchSchools(value);
  };

  const onSchoolChange = (value: number) => {
    form.setFieldsValue({ majorId: undefined });
    setMajors([]);
    if (value) fetchMajors(value);
  };

  const [exportLoading, setExportLoading] = useState(false);

  const handleExport = async () => {
    setExportLoading(true);
    try {
      const values = form.getFieldsValue();
      const response = await axios.get('/api/admin/data-panel/export', {
        params: values,
        responseType: 'blob',
      });
      
      // 获取后端返回的文件名
      let fileName = 'tracker_data.xlsx';
      const contentDisposition = response.headers['content-disposition'];
      if (contentDisposition) {
          const fileNameMatch = contentDisposition.match(/filename\*=UTF-8''(.+)/);
          if (fileNameMatch && fileNameMatch.length === 2) {
              fileName = decodeURIComponent(fileNameMatch[1]);
          } else {
              const fileNameMatch2 = contentDisposition.match(/filename="(.+)"/);
              if (fileNameMatch2 && fileNameMatch2.length === 2) {
                  fileName = fileNameMatch2[1];
              }
          }
      }

      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', fileName);
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      message.success('导出成功');
    } catch (error) {
      console.error('Export failed', error);
      message.error('导出失败');
    } finally {
      setExportLoading(false);
    }
  };

  const columns = [
    { title: '姓名', dataIndex: 'realName', key: 'realName' },
    { title: '学号', dataIndex: 'numberId', key: 'numberId' },
    { title: '学院', dataIndex: 'collegeName', key: 'collegeName' },
    { title: '专业', dataIndex: 'majorName', key: 'majorName' },
    { title: '年级', dataIndex: 'gradeYear', key: 'gradeYear' },
    { 
      title: '学历', 
      dataIndex: 'eduBg', 
      key: 'eduBg',
      render: (val: string) => {
        if (val === 'undergraduate') return '本科生';
        if (val === 'postgraduate') return '研究生';
        return '其他';
      }
    },
    { title: '班级', dataIndex: 'className', key: 'className' },
    { 
      title: '在校状态', 
      dataIndex: 'isEnrolledStu', 
      key: 'isEnrolledStu',
      render: (val: number) => val === 1 ? <Tag color="green">在校</Tag> : <Tag color="default">离校</Tag>
    },
    { title: '洛谷刷题量', dataIndex: 'luoguSolved', key: 'luoguSolved', sorter: (a: any, b: any) => a.luoguSolved - b.luoguSolved },
    { title: '牛客刷题量', dataIndex: 'nowcoderSolved', key: 'nowcoderSolved', sorter: (a: any, b: any) => a.nowcoderSolved - b.nowcoderSolved },
    { title: 'Codeforces刷题量', dataIndex: 'codeforcesSolved', key: 'codeforcesSolved', sorter: (a: any, b: any) => a.codeforcesSolved - b.codeforcesSolved },
    { title: 'QOJ / UCup题量', dataIndex: 'qojSolved', key: 'qojSolved', sorter: (a: any, b: any) => a.qojSolved - b.qojSolved },
    { title: 'AtCoder刷题量', dataIndex: 'atcoderSolved', key: 'atcoderSolved', sorter: (a: any, b: any) => a.atcoderSolved - b.atcoderSolved },
    { title: '学校 OJ 刷题量', dataIndex: 'cwnuSolved', key: 'cwnuSolved', sorter: (a: any, b: any) => a.cwnuSolved - b.cwnuSolved },
    { title: '总刷题量', dataIndex: 'totalSolved', key: 'totalSolved', sorter: (a: any, b: any) => a.totalSolved - b.totalSolved },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: UserPracticeStats) => (
        <Button type="link" size="small" onClick={() => navigate(`/admin/data-panel/tracker/${record.userId}`)}>
          查看 CP Tracker
        </Button>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <Card 
        title="Tracker数据" 
        extra={
            <Space>
                <Button onClick={() => { form.resetFields(); fetchOverallStats(); fetchData(1); }} icon={<ReloadOutlined />}>重置</Button>
                <Button type="primary" onClick={handleExport} icon={<DownloadOutlined />} loading={exportLoading}>导出Excel</Button>
            </Space>
        }
      >
        {/* Filters */}
        <Form form={form} layout="inline" style={{ marginBottom: 24 }} onValuesChange={handleValuesChange}>
          <Form.Item name="universityId">
            <Select placeholder="选择学校" style={{ width: 140 }} allowClear>
              {universities.map(u => (
                <Option key={u.universityId} value={u.universityId}>{u.universityName}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="schoolId">
            <Select placeholder="选择学院" style={{ width: 140 }} allowClear disabled={!schools.length}>
              {schools.map(s => (
                <Option key={s.schoolId} value={s.schoolId}>{s.schoolName}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="majorId">
            <Select placeholder="选择专业" style={{ width: 140 }} allowClear disabled={!majors.length}>
              {majors.map(m => (
                <Option key={m.majorId} value={m.majorId}>{m.majorName}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="gradeYear">
            <Select placeholder="年级" style={{ width: 100 }} allowClear>
                {gradeYears.map(year => (
                    <Option key={year} value={year}>{year}级</Option>
                ))}
            </Select>
          </Form.Item>
          <Form.Item name="eduBg">
            <Select placeholder="学历" style={{ width: 100 }} allowClear>
                <Option value="undergraduate">本科生</Option>
                <Option value="postgraduate">研究生</Option>
                <Option value="other">其他</Option>
            </Select>
          </Form.Item>
          <Form.Item name="className">
            <Input placeholder="班级" style={{ width: 100 }} allowClear />
          </Form.Item>
          <Form.Item name="isEnrolledStu">
            <Select placeholder="在校状态" style={{ width: 100 }} allowClear>
                <Option value={1}>在校</Option>
                <Option value={0}>离校</Option>
            </Select>
          </Form.Item>
          <Form.Item name="keyword">
            <Input placeholder="姓名/学号/昵称" style={{ width: 150 }} />
          </Form.Item>
        </Form>

        {/* Summary Stats */}
        <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
          <Col xs={24} sm={12} md={4}>
            <Card bordered={false} style={{ background: '#e6f7ff', borderRadius: 8 }}>
              <Statistic 
                title="用户数量" 
                value={overallStats?.userCount || 0} 
                prefix={<UserOutlined />}
                valueStyle={{ color: '#1890ff' }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={5}>
            <Card bordered={false} style={{ background: '#f6ffed', borderRadius: 8 }}>
              <Statistic 
                title="总刷题量" 
                value={overallStats?.totalSolved || 0} 
                prefix={<TrophyOutlined />}
                valueStyle={{ color: '#52c41a' }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={5}>
            <Card bordered={false} style={{ background: '#fff7e6', borderRadius: 8 }}>
              <Statistic 
                title="平均刷题量" 
                value={overallStats?.averageSolved || 0} 
                prefix={<RiseOutlined />}
                valueStyle={{ color: '#fa8c16' }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={5}>
             <Card bordered={false} style={{ background: '#fff0f6', borderRadius: 8 }}>
              <Statistic 
                title="近30天总刷题量" 
                value={overallStats?.last30DaysSolved || 0} 
                prefix={<CalendarOutlined />}
                valueStyle={{ color: '#eb2f96' }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={5}>
             <Card bordered={false} style={{ background: '#f0f5ff', borderRadius: 8 }}>
              <Statistic 
                title="近7天总刷题量" 
                value={overallStats?.last7DaysSolved || 0} 
                prefix={<CalendarOutlined />}
                valueStyle={{ color: '#722ed1' }}
              />
            </Card>
          </Col>
        </Row>

        <Table
          columns={columns}
          dataSource={data}
          rowKey="userId"
          pagination={pagination}
          loading={loading}
          onChange={handleTableChange}
          scroll={{ x: 1300 }}
        />
      </Card>
    </div>
  );
};

export default DataPanel;
