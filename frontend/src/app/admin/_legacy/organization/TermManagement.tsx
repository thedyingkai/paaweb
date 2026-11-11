"use client";

import React, { useEffect, useState } from 'react';
import { Card, Table, Button, Modal, Form, Input, DatePicker, message, Tag } from 'antd';
import { PlusOutlined, EditOutlined } from '@ant-design/icons';
import axios from 'axios';
import dayjs from 'dayjs';

const TermManagement: React.FC = () => {
    const [list, setList] = useState([]);
    const [loading, setLoading] = useState(false);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingTerm, setEditingTerm] = useState<any>(null);
    const [form] = Form.useForm();

    useEffect(() => {
        // AdminLayoutClient gates this route with manage_term; the save API enforces it again.
        fetchTerms();
    }, []);

    const fetchTerms = async () => {
        setLoading(true);
        try {
            const res = await axios.get('/api/common/terms');
            if (res.data.code === 200) {
                const sorted = res.data.data.sort((a: any, b: any) => b.termName.localeCompare(a.termName));
                setList(sorted);
            }
        } catch (e) {
            message.error('加载学期数据失败');
        } finally {
            setLoading(false);
        }
    };

    const handleSave = async () => {
        try {
            const values = await form.validateFields();
            const payload = {
                ...values,
                startTime: values.startTime.format('YYYY-MM-DD'),
                endTime: values.endTime ? values.endTime.format('YYYY-MM-DD') : null,
                termId: editingTerm?.termId
            };
            
            const res = await axios.post('/api/admin/org/term/save', payload);
            if (res.data.code === 200) {
                message.success(editingTerm ? '修改成功' : '添加成功');
                setIsModalOpen(false);
                fetchTerms();
            } else {
                message.error(res.data.msg);
            }
        } catch (e) { 
        }
    };

    const columns = [
        { title: 'ID', dataIndex: 'termId', width: 80 },
        { 
            title: '学期名称', 
            dataIndex: 'termName', 
            render: (text: string) => <Tag color="geekblue">{text}</Tag>
        },
        { title: '开始时间', dataIndex: 'startTime' },
        { title: '结束时间', dataIndex: 'endTime', render: (t:any) => t || <span style={{color:'#ccc'}}>-</span> },
        {
            title: '操作',
            width: 120,
            render: (_: any, record: any) => (
                <Button 
                    size="small" 
                    icon={<EditOutlined />} 
                    onClick={() => {
                        setEditingTerm(record);
                        form.setFieldsValue({
                            termName: record.termName,
                            startTime: dayjs(record.startTime),
                            endTime: record.endTime ? dayjs(record.endTime) : null
                        });
                        setIsModalOpen(true);
                    }}
                >
                    编辑
                </Button>
            )
        }
    ];

    return (
        <Card 
            title="学期管理" 
            extra={
                <Button type="primary" icon={<PlusOutlined />} onClick={() => {
                    setEditingTerm(null);
                    form.resetFields();
                    setIsModalOpen(true);
                }}>
                    添加学期
                </Button>
            }
        >
            <Table 
                dataSource={list} 
                columns={columns} 
                rowKey="termId" 
                loading={loading} 
                pagination={{ pageSize: 10 }}
            />

            <Modal 
                title={editingTerm ? "编辑学期" : "添加学期"} 
                open={isModalOpen} 
                onOk={handleSave} 
                onCancel={() => setIsModalOpen(false)}
                destroyOnClose
            >
                <Form form={form} layout="vertical">
                    <Form.Item 
                        name="termName" 
                        label="学期名称" 
                        tooltip="格式必须为 YYYY-YYYY-N，例如 2025-2026-1"
                        rules={[
                            { required: true, message: '请输入学期名称' },
                            { pattern: /^\d{4}-\d{4}-[12]$/, message: '格式错误，应为 2025-2026-1 这种形式' }
                        ]}
                    >
                        <Input placeholder="2025-2026-1" disabled={!!editingTerm} />
                    </Form.Item>
                    
                    <Form.Item name="startTime" label="开始时间" rules={[{ required: true, message: '请选择开始时间' }]}>
                        <DatePicker style={{ width: '100%' }} />
                    </Form.Item>
                    
                    <Form.Item name="endTime" label="结束时间">
                        <DatePicker style={{ width: '100%' }} />
                    </Form.Item>
                </Form>
            </Modal>
        </Card>
    );
};

export default TermManagement;
