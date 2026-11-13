"use client";

import { useEffect, useState } from 'react';
import { Alert, Button, Card, Input, Modal, Space, Table, Tag, Upload, message } from 'antd';
import axios from 'axios';
import { downloadExcelTemplate, readExcelRows } from '@/utils/excelImport';

type Entry = { rosterId: number; universityName: string; numberId: string; realName: string; groupLabel: string; seatNumber: string; isRanking: number; userId: number | null };
const headers = ['学校', '学号', '姓名', '分组', '座位', '参与排名'];

export default function EventRosterManager({ eventId, name }: { eventId: string; name: string }) {
  const [rows, setRows] = useState<Entry[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [text, setText] = useState('');
  const [query, setQuery] = useState('');
  const [saving, setSaving] = useState(false);
  const load = async () => {
    setLoading(true);
    try {
      const res = await axios.get('/api/event/admin/roster', { params: { eventId } });
      if (res.data.code !== 200) throw new Error(res.data.msg);
      setRows(res.data.data);
    } catch { message.error('名单加载失败，请重试'); } finally { setLoading(false); }
  };
  useEffect(() => { void load(); }, [eventId]);
  const save = async () => {
    setSaving(true);
    try {
      const entries = text.trim().split(/\r?\n/).filter(line => line.trim()).map(line => {
        const values = line.split('\t').map(s => s.trim());
        const [universityName, numberId, realName, groupLabel = '', seatNumber = '', ranking = '是'] = values;
        if (!['是', '否', '1', '0', ''].includes(ranking)) throw new Error('参与排名请填写“是”或“否”');
        return { universityName, numberId, realName, groupLabel, seatNumber, isRanking: ['否', '0'].includes(ranking) ? 0 : 1 };
      });
      const res = await axios.post('/api/event/admin/roster/import', { eventId: Number(eventId), entries });
      if (res.data.code !== 200) throw new Error(res.data.msg);
      setRows(res.data.data); setOpen(false); setText(''); message.success('确定名单已导入');
    } catch (error) { message.error(error instanceof Error ? error.message : '导入失败'); } finally { setSaving(false); }
  };
  const exportRoster = async () => {
    try {
      const res = await axios.get('/api/event/admin/roster/export', { params: { eventId }, responseType: 'blob' });
      if (!String(res.headers['content-type'] || '').includes('spreadsheetml')) throw new Error();
      const url = URL.createObjectURL(res.data);
      const link = document.createElement('a'); link.href = url; link.download = `${name}_确定参赛名单.xlsx`; link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch { message.error('导出失败'); }
  };
  const linked = rows.filter(row => row.userId != null).length;
  return <Card title={name} extra={<Button href="/admin/contest">返回赛事列表</Button>}>
    <Alert type="info" showIcon message="固定名单参赛，不开放自主报名" description={`已导入 ${rows.length} 人，已关联实名账号 ${linked} 人，待注册或实名核验 ${rows.length - linked} 人。学校、学号、姓名完全一致且通过实名核验后自动关联，无需重新报名。`} style={{ marginBottom: 20 }} />
    <Space wrap style={{ marginBottom: 16 }}>
      <Button onClick={load} loading={loading}>刷新名单</Button>
      <Button type="primary" onClick={() => setOpen(true)}>导入确定名单</Button>
      <Button onClick={exportRoster}>批量导出 Excel</Button>
      <Input.Search allowClear placeholder="搜索学校、姓名或学号" value={query} onChange={e => setQuery(e.target.value)} style={{ width: 260 }} />
    </Space>
    <Table<Entry> rowKey="rosterId" loading={loading} dataSource={rows.filter(row => [row.universityName, row.realName, row.numberId].some(value => value.includes(query)))}
      pagination={{ defaultPageSize: 20, showSizeChanger: true, showTotal: total => `共 ${total} 人` }} scroll={{ x: 1000 }}
      columns={[
        { title: '学校', dataIndex: 'universityName' }, { title: '学号', dataIndex: 'numberId' }, { title: '姓名', dataIndex: 'realName' },
        { title: '分组', dataIndex: 'groupLabel' }, { title: '座位', dataIndex: 'seatNumber' },
        { title: '参评身份', dataIndex: 'isRanking', render: value => <Tag color={value === 1 ? 'blue' : 'orange'}>{value === 1 ? '正式参赛' : '打星参赛'}</Tag> },
        { title: '实名账号', dataIndex: 'userId', render: value => <Tag color={value == null ? 'default' : 'green'}>{value == null ? '待注册或核验' : '已关联'}</Tag> }
      ]} />
    <Modal title="导入确定参赛名单" open={open} onCancel={() => setOpen(false)} onOk={save} okText="确认导入名单" confirmLoading={saving} width={760}>
      <p>每行一人，按“学校、学号、姓名、分组、座位、参与排名”的顺序从表格粘贴。参与排名填“是”或“否”；分组和座位可留空。重复导入同一学号不会增加人数。</p>
      <Space style={{ marginBottom: 12 }}>
        <Button onClick={() => downloadExcelTemplate({ fileName: '确定参赛名单模板.xlsx', headers })}>下载模板</Button>
        <Upload accept=".xlsx,.xls" showUploadList={false} beforeUpload={async file => {
          try { const values = await readExcelRows(file, headers); setText(values.map(row => row.join('\t')).join('\n')); }
          catch { message.error('表格读取失败，请使用模板'); } return false;
        }}><Button>读取 Excel</Button></Upload>
      </Space>
      <Input.TextArea rows={10} value={text} onChange={e => setText(e.target.value)} placeholder="北京电子科技学院（Tab）学号（Tab）姓名（Tab）新生（Tab）01（Tab）是" />
    </Modal>
  </Card>;
}
