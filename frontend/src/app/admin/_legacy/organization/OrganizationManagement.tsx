"use client";

import React, { useState, useEffect } from 'react';
import { Card, Button, Table, Space, Modal, Form, Input, message, Breadcrumb, Row, Col, Empty, Upload } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, UploadOutlined, InfoCircleOutlined, DownloadOutlined } from '@ant-design/icons';
import axios from 'axios';
import ImportPreview, { ImportPreviewData } from '../components/ImportPreview';
import { downloadExcelTemplate, readExcelRows, rowsToTsv } from '@/utils/excelImport';

const IMPORT_HEADERS = ['学校名称', '学院名称', '专业名称'];

const OrganizationManagement: React.FC = () => {
  const [universities, setUniversities] = useState<any[]>([]);
  const [schools, setSchools] = useState<any[]>([]);
  const [majors, setMajors] = useState<any[]>([]);
  
  const [uniSearch, setUniSearch] = useState('');
  const [schoolSearch, setSchoolSearch] = useState('');
  const [majorSearch, setMajorSearch] = useState('');
  
  const [selectedUni, setSelectedUni] = useState<any>(null);
  const [selectedSchool, setSelectedSchool] = useState<any>(null);
  
  const [isUniModalOpen, setIsUniModalOpen] = useState(false);
  const [isSchoolModalOpen, setIsSchoolModalOpen] = useState(false);
  const [isMajorModalOpen, setIsMajorModalOpen] = useState(false);
  const [editingItem, setEditingItem] = useState<any>(null);

  const [isImportModalOpen, setIsImportModalOpen] = useState(false);
  const [importRows, setImportRows] = useState<string[][]>([]);
  const [importPreview, setImportPreview] = useState<ImportPreviewData | null>(null);
  const [importLoading, setImportLoading] = useState(false);

  const [form] = Form.useForm();

  useEffect(() => {
    fetchUniversities();
  }, []);

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

  const handleSave = async (type: 'university' | 'school' | 'major') => {
    try {
      const values = await form.validateFields();
      let payload: any = { ...values };

      if (type === 'school') {
        if (!selectedUni) return message.error('请先选择学校');
        payload.universityId = selectedUni.universityId;
      }
      if (type === 'major') {
        if (!selectedSchool) return message.error('请先选择学院');
        payload.schoolId = selectedSchool.schoolId;
      }

      if (editingItem) {
        if (type === 'university') payload.universityId = editingItem.universityId;
        if (type === 'school') payload.schoolId = editingItem.schoolId;
        if (type === 'major') payload.majorId = editingItem.majorId;
      }

      const res = await axios.post(`/api/admin/org/${type}/save`, payload);
      if (res.data.code === 200) {
        message.success('操作成功');
        setEditingItem(null);
        form.resetFields();

        if (type === 'university') {
          setIsUniModalOpen(false);
          setSelectedUni(null);
          setSelectedSchool(null);
          setSchools([]);
          setMajors([]);
          await fetchUniversities();
        } else if (type === 'school') {
          setIsSchoolModalOpen(false);
          setSelectedSchool(null);
          setMajors([]);
          await fetchSchools(selectedUni.universityId);
        } else if (type === 'major') {
          setIsMajorModalOpen(false);
          await fetchMajors(selectedSchool.schoolId);
        }
      } else {
        message.error(res.data.msg);
      }
    } catch (e) {
      console.error('Save failed:', e);
    }
  };

  const handleDelete = (type: 'university' | 'school' | 'major', id: number) => {
    Modal.confirm({
      title: '确认删除？',
      content: '删除后无法恢复，若下层级存在内容将无法删除。',
      onOk: async () => {
        const res = await axios.delete(`/api/admin/org/${type}/delete/${id}`);
        if (res.data.code === 200) {
          message.success('删除成功');
          if (type === 'university') {
            setSelectedUni(null);
            setSchools([]);
            setMajors([]);
            fetchUniversities();
          } else if (type === 'school') {
            setSelectedSchool(null);
            setMajors([]);
            fetchSchools(selectedUni.universityId);
          } else {
            fetchMajors(selectedSchool.schoolId);
          }
        } else {
          message.error(res.data.msg);
        }
      }
    });
  };

  const handleImport = async () => {
    if (!importRows.length) {
        message.warning("请先上传填写好的 Excel 文件");
        return;
    }
    setImportLoading(true);
    try {
        const confirming = Boolean(importPreview);
        const res = await axios.post('/api/admin/org/import', { content: rowsToTsv(importRows), preview: !confirming });
        if (res.data.code === 200) {
            if (!confirming) {
                setImportPreview(res.data.data);
                return;
            }
            const { success, failure, failedList } = res.data.data;
            setIsImportModalOpen(false);
            setImportRows([]);
            setImportPreview(null);
            
            Modal.info({
                title: '导入完成',
                width: 500,
                content: (
                    <div>
                        <p style={{ color: '#52c41a' }}>成功导入: {success} 条</p>
                        <p style={{ color: failure > 0 ? '#ff4d4f' : '#666' }}>失败: {failure} 条</p>
                        {failedList && failedList.length > 0 && (
                            <div style={{ marginTop: 10, maxHeight: 200, overflowY: 'auto', background: '#f9f9f9', padding: 8, border: '1px solid #eee', borderRadius: 4 }}>
                                <div style={{ marginBottom: 6, fontWeight: 'bold' }}>失败详情:</div>
                                {failedList.map((item: any, idx: number) => (
                                    <div key={idx} style={{ fontSize: 12, marginBottom: 4, color: '#ff4d4f' }}>
                                        第{idx + 1}条: {item.reason}
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                ),
                onOk: () => {
                    fetchUniversities(); 
                }
            });
        } else {
            message.error(res.data.msg);
        }
    } catch (error) {
        message.error("导入请求失败");
        console.error(error);
    } finally {
        setImportLoading(false);
    }
  };

  const resetImport = () => {
    setIsImportModalOpen(false);
    setImportRows([]);
    setImportPreview(null);
  };

  const selectImportFile = async (file: File) => {
    try {
      const rows = await readExcelRows(file, IMPORT_HEADERS);
      if (!rows.length) {
        message.warning('Excel 中没有待导入数据');
        return false;
      }
      setImportRows(rows);
      setImportPreview(null);
      message.success(`已读取 ${rows.length} 条数据，请上传校验`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'Excel 读取失败');
    }
    return false;
  };

  const openModal = (type: 'university' | 'school' | 'major', item: any = null) => {
    setEditingItem(item);
    form.resetFields();
    if (item) form.setFieldsValue(item);
    if (type === 'university') setIsUniModalOpen(true);
    if (type === 'school') setIsSchoolModalOpen(true);
    if (type === 'major') setIsMajorModalOpen(true);
  };

  const filteredUnis = universities.filter(u => u.universityName.toLowerCase().includes(uniSearch.toLowerCase()));
  const filteredSchools = schools.filter(s => s.schoolName.toLowerCase().includes(schoolSearch.toLowerCase()));
  const filteredMajors = majors.filter(m => m.majorName.toLowerCase().includes(majorSearch.toLowerCase()));

  return (
    <div style={{ padding: '24px' }}>
      <Breadcrumb style={{ marginBottom: '16px' }}>
        <Breadcrumb.Item>管理后台</Breadcrumb.Item>
        <Breadcrumb.Item>组织管理</Breadcrumb.Item>
      </Breadcrumb>

      <Row gutter={16}>
        <Col span={8}>
          <Card 
            title="学校列表" 
            extra={
                <Space>
                    <Button icon={<UploadOutlined />} onClick={() => setIsImportModalOpen(true)}>批量导入</Button>
                    <Button type="primary" icon={<PlusOutlined />} onClick={() => openModal('university')}>添加</Button>
                </Space>
            }
          >
            <Input.Search 
              placeholder="搜索学校..." 
              style={{ marginBottom: 12 }} 
              onChange={e => setUniSearch(e.target.value)} 
              allowClear
            />
            <Table
              dataSource={filteredUnis}
              rowKey="universityId"
              pagination={false}
              size="small"
              scroll={{ y: 450 }} 
              columns={[
                { title: '名称', dataIndex: 'universityName' },
                {
                  title: '操作',
                  width: 80,
                  render: (_, record) => (
                    <Space onClick={e => e.stopPropagation()}>
                      <Button type="text" size="small" icon={<EditOutlined />} onClick={() => openModal('university', record)} />
                      <Button type="text" size="small" danger icon={<DeleteOutlined />} onClick={() => handleDelete('university', record.universityId)} />
                    </Space>
                  )
                }
              ]}
              onRow={(record) => ({
                onClick: () => {
                  setSelectedUni(record);
                  setSelectedSchool(null);
                  setSchoolSearch('');
                  setMajors([]);
                  fetchSchools(record.universityId);
                },
                style: { cursor: 'pointer' }
              })}
              rowClassName={(record) => record.universityId === selectedUni?.universityId ? 'custom-row-selected' : ''}
            />
          </Card>
        </Col>

        <Col span={8}>
          <Card 
            title={`学院列表 ${selectedUni ? `(${selectedUni.universityName})` : ''}`} 
            extra={<Button type="primary" size="small" disabled={!selectedUni} icon={<PlusOutlined />} onClick={() => openModal('school')}>添加</Button>}
          >
            {selectedUni ? (
              <>
                <Input.Search 
                  placeholder="搜索学院..." 
                  style={{ marginBottom: 12 }} 
                  onChange={e => setSchoolSearch(e.target.value)} 
                  allowClear
                />
                <Table
                  dataSource={filteredSchools}
                  rowKey="schoolId"
                  pagination={false}
                  size="small"
                  scroll={{ y: 450 }}
                  columns={[
                    { title: '学院名称', dataIndex: 'schoolName' },
                    {
                      title: '操作',
                      width: 80,
                      render: (_, record) => (
                        <Space onClick={e => e.stopPropagation()}>
                          <Button type="text" size="small" icon={<EditOutlined />} onClick={() => openModal('school', record)} />
                          <Button type="text" size="small" danger icon={<DeleteOutlined />} onClick={() => handleDelete('school', record.schoolId)} />
                        </Space>
                      )
                    }
                  ]}
                  onRow={(record) => ({
                    onClick: () => {
                      setSelectedSchool(record);
                      setMajorSearch('');
                      fetchMajors(record.schoolId);
                    },
                    style: { cursor: 'pointer' }
                  })}
                  rowClassName={(record) => record.schoolId === selectedSchool?.schoolId ? 'custom-row-selected' : ''}
                />
              </>
            ) : <Empty description="请先在左侧选择学校" />}
          </Card>
        </Col>

        <Col span={8}>
          <Card 
            title={`专业列表 ${selectedSchool ? `(${selectedSchool.schoolName})` : ''}`} 
            extra={<Button type="primary" size="small" disabled={!selectedSchool} icon={<PlusOutlined />} onClick={() => openModal('major')}>添加</Button>}
          >
            {selectedSchool ? (
              <>
                <Input.Search 
                  placeholder="搜索专业..." 
                  style={{ marginBottom: 12 }} 
                  onChange={e => setMajorSearch(e.target.value)} 
                  allowClear
                />
                <Table
                  dataSource={filteredMajors}
                  rowKey="majorId"
                  pagination={false}
                  size="small"
                  scroll={{ y: 450 }}
                  columns={[
                    { title: '专业名称', dataIndex: 'majorName' },
                    { title: '学制', dataIndex: 'schoolingYears', width: 60, render: (val) => `${val}Y` },
                    {
                      title: '操作',
                      width: 80,
                      render: (_, record) => (
                        <Space onClick={e => e.stopPropagation()}>
                          <Button type="text" size="small" icon={<EditOutlined />} onClick={() => openModal('major', record)} />
                          <Button type="text" size="small" danger icon={<DeleteOutlined />} onClick={() => handleDelete('major', record.majorId)} />
                        </Space>
                      )
                    }
                  ]}
                />
              </>
            ) : <Empty description="请在中间选择学院" />}
          </Card>
        </Col>
      </Row>

      <Modal
        title="批量导入组织架构"
        open={isImportModalOpen}
        onOk={handleImport}
        onCancel={resetImport}
        confirmLoading={importLoading}
        okText={importPreview ? '确认导入可用数据' : '上传并校验'}
        width={760}
        destroyOnClose
      >
        <div style={{ marginBottom: 16, background: '#e6f7ff', padding: '12px', borderRadius: 4, border: '1px solid #91d5ff' }}>
            <div style={{ fontWeight: 'bold', marginBottom: 4 }}><InfoCircleOutlined /> 格式说明：</div>
            <ul style={{ paddingLeft: 20, margin: 0, fontSize: 13, color: '#555' }}>
                <li>格式顺序：<b>学校名称 &gt; 学院名称 &gt; 专业名称</b></li>
                <li>仅“学校名称”为必填，后续字段可留空。</li>
                <li>如果学校/学院已存在，系统会自动关联，不会重复创建。</li>
            </ul>
        </div>
        <Space wrap>
          <Button icon={<DownloadOutlined />} onClick={() => downloadExcelTemplate({
            fileName: '组织架构导入模板.xlsx', headers: IMPORT_HEADERS,
            examples: [['北京电子科技学院', '网络空间安全系', '计算机科学与技术']],
          })}>下载 Excel 模板</Button>
          <Upload accept=".xlsx,.xls" maxCount={1} beforeUpload={(file) => selectImportFile(file as File)} onRemove={() => { setImportRows([]); setImportPreview(null); }}>
            <Button icon={<UploadOutlined />}>选择填写好的 Excel</Button>
          </Upload>
        </Space>
        {importRows.length > 0 && <div style={{ marginTop: 8, color: '#666' }}>已读取 {importRows.length} 条数据</div>}
        {importPreview && <ImportPreview data={importPreview} />}
      </Modal>

      <Modal 
        title={editingItem ? "编辑学校" : "添加学校"} 
        open={isUniModalOpen} 
        onOk={() => handleSave('university')} 
        onCancel={() => setIsUniModalOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="universityName" label="学校名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
        </Form>
      </Modal>

      <Modal 
        title={editingItem ? "编辑学院" : "添加学院"} 
        open={isSchoolModalOpen} 
        onOk={() => handleSave('school')} 
        onCancel={() => setIsSchoolModalOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="schoolName" label="学院名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
        </Form>
      </Modal>

      <Modal 
        title={editingItem ? "编辑专业" : "添加专业"} 
        open={isMajorModalOpen} 
        onOk={() => handleSave('major')} 
        onCancel={() => setIsMajorModalOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="majorName" label="专业名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="schoolingYears" label="学制(年)" initialValue={4} rules={[{ required: true }]}>
            <Input type="number" />
          </Form.Item>
        </Form>
      </Modal>

      <style>{`
        .custom-row-selected td {
          background-color: #e6f7ff !important;
          font-weight: bold;
          color: #1890ff;
        }
        .ant-table-row:hover {
          cursor: pointer;
        }
      `}</style>
    </div>
  );
};

export default OrganizationManagement;
