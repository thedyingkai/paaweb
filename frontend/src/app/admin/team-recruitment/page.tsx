"use client";
import { useEffect, useState, type Key } from 'react';
import { Alert, Button, Card, ConfigProvider, DatePicker, Descriptions, Empty, Form, Input, Modal, Select, Space, Switch, Table, Tag, Typography, message } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import type { Dayjs } from 'dayjs';
import Link from 'next/link';
import request from '@/utils/request';
import { teamError } from '@/lib/besti';
import { currentAcademicTerm, academicTermDates } from '@/lib/besti-members';
import { RecruitmentWindow, TeamApplication, dayjs, statusLabels, windowLabels, windowState, timeText } from '../../join-team/recruitment';
import ApplicationDetails from '../../join-team/ApplicationDetails';
type WindowValues = {title:string;description:string;range:[Dayjs,Dayjs];term:string;membership:[Dayjs,Dayjs];enabled:boolean};
type ReviewValues = {note:string;membership:[Dayjs,Dayjs]};
export default function RecruitmentAdmin() {
  const [windows,setWindows]=useState<RecruitmentWindow[]>([]);
  const [windowId,setWindowId]=useState<number>();
  const [status,setStatus]=useState('pending');
  const [page,setPage]=useState(1);
  const [applications,setApplications]=useState<TeamApplication[]>([]);
  const [total,setTotal]=useState(0);
  const [loading,setLoading]=useState(false);
  const [error,setError]=useState('');
  const [reload,setReload]=useState(0);
  const [editing,setEditing]=useState<RecruitmentWindow | 'new' | null>(null);
  const [reviewing,setReviewing]=useState<TeamApplication | null>(null);
  const [saving,setSaving]=useState(false);
  const [saveError,setSaveError]=useState('');
  const [selected,setSelected]=useState<Key[]>([]);
  const [exportFormat,setExportFormat]=useState<'xlsx'|'docx'>('xlsx');
  const [exporting,setExporting]=useState(false);
  const [windowForm]=Form.useForm<WindowValues>();
  const [reviewForm]=Form.useForm<ReviewValues>();
  const activeWindow=windows.find(w=>w.id===windowId);
  useEffect(()=>setSelected([]),[windowId,status,reload]);
  const exportApplications=async(onlySelected:boolean)=>{
    if(!windowId)return;setExporting(true);
    try {
      const response=await request.post('/team-recruitment/admin/export',{windowId,status,format:exportFormat,...(onlySelected?{ids:selected}: {})},{responseType:'blob',timeout:120000});
      if(String(response.headers['content-type'] || '').includes('json'))throw new Error(JSON.parse(await response.data.text()).msg || '导出失败');
      const url=URL.createObjectURL(response.data);const link=document.createElement('a');link.href=url;
      link.download=`ACM竞赛选拔报名表_${windowId}_${dayjs().format('YYYYMMDD_HHmm')}.${exportFormat==='xlsx'?'xlsx':'zip'}`;
      document.body.appendChild(link);link.click();link.remove();setTimeout(()=>URL.revokeObjectURL(url),30000);
      message.success('导出文件已生成');
    }catch(e){
      const failure=e as {response?:{data?:Blob}};
      if(failure.response?.data instanceof Blob){
        try{message.error(JSON.parse(await failure.response.data.text()).msg || '导出失败');}catch{message.error('导出失败，请稍后重试');}
      }else message.error(teamError(e));
    }finally{setExporting(false);}
  };
  useEffect(()=>{
    const controller=new AbortController(); setError('');
    request.get('/team-recruitment/admin/windows',{signal:controller.signal}).then(({data})=>{
      if(data.code!==200) throw new Error(data.msg);
      if(!controller.signal.aborted) { setWindows(data.data); setWindowId(current=>current || data.data[0]?.id); }
    }).catch(e=>{if(!controller.signal.aborted)setError(teamError(e));});
    return ()=>controller.abort();
  },[reload]);
  useEffect(()=>{
    setApplications([]); setTotal(0); if(!windowId)return;
    const controller=new AbortController(); setLoading(true);setError('');
    request.get('/team-recruitment/admin/applications',{params:{windowId,status,page},signal:controller.signal}).then(({data})=>{
      if(data.code!==200)throw new Error(data.msg);
      if(!controller.signal.aborted){setApplications(data.data.items);setTotal(data.data.total);}
    }).catch(e=>{if(!controller.signal.aborted)setError(teamError(e));}).finally(()=>{if(!controller.signal.aborted)setLoading(false);});
    return ()=>controller.abort();
  },[windowId,status,page,reload]);
  const editWindow=(window?:RecruitmentWindow)=>{
    const term=window?.term || currentAcademicTerm(),dates=academicTermDates(term);
    setEditing(window || 'new');setSaveError('');
    windowForm.setFieldsValue({title:window?.title || '',description:window?.description || '',term,enabled:window?.enabled || false,
      range:window?[dayjs(window.opens_at).utcOffset(8),dayjs(window.closes_at).utcOffset(8)]:[dayjs().utcOffset(8),dayjs().utcOffset(8).add(7,'day')],
      membership:window?[dayjs(window.member_start),dayjs(window.member_end)]:[dayjs(dates.startDate),dayjs(dates.endDate)]});
  };
  const saveWindow=async(values:WindowValues)=>{
    setSaving(true);setSaveError('');
    try{
      const response=await request.post('/team-recruitment/admin/windows',{
        ...(editing && editing!=='new'?{id:editing.id,version:editing.version}:{}),title:values.title,description:values.description,
        opensAt:values.range[0].format('YYYY-MM-DDTHH:mm:ssZ'),closesAt:values.range[1].format('YYYY-MM-DDTHH:mm:ssZ'),
        term:values.term,memberStart:values.membership[0].format('YYYY-MM-DD'),memberEnd:values.membership[1].format('YYYY-MM-DD'),enabled:values.enabled,
      });
      if(response.data.code!==200)throw new Error(response.data.msg);
      message.success('申请批次已保存');setEditing(null);setReload(n=>n+1);
    }catch(e){setSaveError(teamError(e));}finally{setSaving(false);}
  };
  const openReview=(application:TeamApplication)=>{
    setReviewing(application);setSaveError('');
    reviewForm.setFieldsValue({note:application.review_note || '',membership:[dayjs(activeWindow?.member_start),dayjs(activeWindow?.member_end)]});
  };
  const review=async(approved:boolean)=>{
    if(!reviewing)return;
    const values=await reviewForm.validateFields();
    if(!approved && !values.note?.trim()){setSaveError('请填写未通过的原因');return;}
    setSaving(true);setSaveError('');
    try{
      const response=await request.post(`/team-recruitment/admin/applications/${reviewing.id}/review`,{version:reviewing.version,approved,note:values.note,
        memberStart:values.membership[0].format('YYYY-MM-DD'),memberEnd:values.membership[1].format('YYYY-MM-DD')});
      if(response.data.code!==200)throw new Error(response.data.msg);
      message.success(approved?'申请已通过，已加入校队名单':'申请已驳回');setReviewing(null);setReload(n=>n+1);
    }catch(e){setSaveError(teamError(e));}finally{setSaving(false);}
  };
  return <ConfigProvider locale={zhCN}><Space orientation="vertical" size={20} style={{width:'100%'}}>
    <Card title="校队入队申请" extra={<Button type="primary" onClick={()=>editWindow()}>新建申请批次</Button>}>
      <Typography.Paragraph>设置申请开放时间和队员任期。审核通过后以“队员”身份加入 ACM 校队，任期内开放校队交流区。时间均为北京时间。</Typography.Paragraph>
      <Link href="/join-team" target="_blank">查看成员申请页 ↗</Link>
      {error && <Alert type="error" title={error} showIcon action={<Button onClick={()=>setReload(n=>n+1)}>重试</Button>} style={{marginTop:16}} />}
      <Space wrap style={{margin:'20px 0 10px'}}>
        <Select aria-label="选择申请批次" placeholder="选择申请批次" style={{width:330}} value={windowId} onChange={id=>{setWindowId(id);setPage(1);}} options={windows.map(w=>({value:w.id,label:w.title}))} />
        {activeWindow && <><Tag color={windowState(activeWindow)==='open'?'green':'default'}>{windowLabels[windowState(activeWindow)]}</Tag><Button onClick={()=>editWindow(activeWindow)}>编辑批次</Button></>}
      </Space>
      {activeWindow && <Typography.Paragraph type="secondary">申请：{timeText(activeWindow.opens_at)} 至 {timeText(activeWindow.closes_at)}；默认任期：{activeWindow.member_start} 至 {activeWindow.member_end}</Typography.Paragraph>}
      {!windows.length && !error && <Empty description="尚未创建申请批次" />}
    </Card>
    {activeWindow && <Card title="申请审核" extra={<Select value={status} onChange={value=>{setStatus(value);setPage(1);}} style={{width:140}} options={[{value:'all',label:'全部状态'},...Object.entries(statusLabels).map(([value,label])=>({value,label}))]} />}>
      <Space wrap style={{marginBottom:16}}>
        <Select aria-label="导出格式" value={exportFormat} onChange={setExportFormat} style={{width:190}} options={[{value:'xlsx',label:'Excel 汇总表'},{value:'docx',label:'Word 原表（ZIP）'}]}/>
        <Button disabled={!selected.length||loading} loading={exporting} onClick={()=>void exportApplications(true)}>导出勾选（{selected.length}）</Button>
        <Button disabled={!total||loading} loading={exporting} onClick={()=>void exportApplications(false)}>导出筛选结果（{total}）</Button>
        <Typography.Text type="secondary">支持跨页勾选；筛选结果包含所有分页。Word 压缩包内同时附 Excel 汇总。</Typography.Text>
      </Space>
      <Table rowKey="id" loading={loading} dataSource={applications} scroll={{x:760}} pagination={{current:page,pageSize:50,total,showSizeChanger:false,onChange:setPage}}
        rowSelection={{selectedRowKeys:selected,onChange:setSelected,preserveSelectedRowKeys:true}}
        columns={[
          {title:'申请人',dataIndex:'real_name',render:(name,record)=><Link href={`/profile/${record.user_id}`} target="_blank">{name}</Link>},
          {title:'学号',dataIndex:'student_number'},
          {title:'校赛名次',dataIndex:'campus_rank',render:value=>value||'未填写'},
          {title:'提交时间',dataIndex:'created_at',render:timeText},
          {title:'状态',dataIndex:'status',render:value=><Tag>{statusLabels[value]}</Tag>},
          {title:'操作',render:(_,record)=><Button onClick={()=>openReview(record)}>{record.status==='pending'?'审核申请':'查看详情'}</Button>},
        ]} />
    </Card>}
    <Modal title={editing==='new'?'新建申请批次':'编辑申请批次'} open={!!editing} onCancel={()=>!saving&&setEditing(null)} onOk={()=>windowForm.submit()} confirmLoading={saving} closable={!saving} maskClosable={!saving} width={720}>
      {saveError&&<Alert type="error" title={saveError} showIcon/>}
      <Form form={windowForm} layout="vertical" onFinish={saveWindow} disabled={saving}>
        <Form.Item name="title" label="批次名称" rules={[{required:true,whitespace:true}]}><Input maxLength={200} placeholder="例如：2026 年秋季校队招新" /></Form.Item>
        <Form.Item name="description" label="申请说明"><Input.TextArea rows={4} maxLength={4000} showCount placeholder="填写申请条件、训练安排和选拔方式" /></Form.Item>
        <Form.Item name="range" label="申请开始、截止时间（北京时间）" rules={[{required:true}]}><DatePicker.RangePicker showTime format="YYYY-MM-DD HH:mm" style={{width:'100%'}} /></Form.Item>
        <Form.Item name="term" label="所属学年" rules={[{required:true}]}><Input placeholder="2026-2027学年" maxLength={50}/></Form.Item>
        <Form.Item name="membership" label="默认队员任期" rules={[{required:true}]}><DatePicker.RangePicker style={{width:'100%'}} /></Form.Item>
        <Form.Item name="enabled" label="发布申请批次" valuePropName="checked" extra="发布后，成员可查看该批次；只有起止时间内可提交。关闭后立即暂停申请。"><Switch /></Form.Item>
      </Form>
    </Modal>
    <Modal title={`入队申请 · ${reviewing?.real_name || ''}`} open={!!reviewing} onCancel={()=>!saving&&setReviewing(null)} width={800} closable={!saving} maskClosable={!saving}
      footer={reviewing?.status==='pending'?<Space><Button disabled={saving} onClick={()=>setReviewing(null)}>取消</Button><Button danger loading={saving} onClick={()=>void review(false)}>不通过</Button><Button type="primary" loading={saving} onClick={()=>void review(true)}>通过并加入校队</Button></Space>:null}>
      {reviewing&&<>
        <ApplicationDetails application={reviewing}/>
        {saveError&&<Alert type="error" showIcon title={saveError}/>}
        <Form form={reviewForm} layout="vertical" disabled={saving||reviewing.status!=='pending'}>
          <Form.Item name="membership" label="通过后的队员任期" rules={[{required:true}]}><DatePicker.RangePicker style={{width:'100%'}}/></Form.Item>
          <Form.Item name="note" label="审核意见（不通过时必填）"><Input.TextArea rows={3} maxLength={2000}/></Form.Item>
        </Form>
      </>}
    </Modal>
  </Space></ConfigProvider>;
}
