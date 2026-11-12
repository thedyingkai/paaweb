"use client";
import { useEffect, useState } from 'react';
import { Alert, Button, Card, Col, ConfigProvider, Descriptions, Empty, Form, Input, Modal, Popconfirm, Row, Space, Spin, Tag, Typography, message } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import Link from 'next/link';
import SiteHeader from '../components/SiteHeader';
import request from '@/utils/request';
import { teamError } from '@/lib/besti';
import { RecruitmentWindow, TeamApplication, statusLabels, windowLabels, windowState, timeText } from './recruitment';
import ApplicationDetails from './ApplicationDetails';
type Me = { identity: { account_status: string; real_name: string; number_id: string; school_name: string; email?:string; gender?:number; phone?:string }; currentMember: boolean; applications: TeamApplication[];
  prefill:{experience?:string;studentWork?:string;platformAccounts?:string} };
type Values = { motivation: string; experience: string; contact: string; gender:string; politicalStatus:string; email:string; campusRank:string; learningContent:string; studentWork:string; platformAccounts:string };
export default function RecruitmentClient() {
  const [windows, setWindows] = useState<RecruitmentWindow[]>([]);
  const [me, setMe] = useState<Me | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reload, setReload] = useState(0);
  const [editing, setEditing] = useState<RecruitmentWindow | null>(null);
  const [viewing,setViewing] = useState<TeamApplication | null>(null);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState('');
  const [now, setNow] = useState(Date.now());
  const [form] = Form.useForm<Values>();
  useEffect(() => { const timer=setInterval(()=>setNow(Date.now()),1000); return ()=>clearInterval(timer); },[]);
  useEffect(() => {
    const controller=new AbortController(); setLoading(true); setError('');
    Promise.all([request.get('/team-recruitment/windows',{signal:controller.signal}),
      request.get('/team-recruitment/me',{signal:controller.signal}).catch(e=> { if(e.response?.status===401) return null; throw e; })])
      .then(([windowsResponse,meResponse])=> {
        if(windowsResponse.data.code!==200 || meResponse && meResponse.data.code!==200) throw new Error('申请信息加载失败');
        if(!controller.signal.aborted) { setWindows(windowsResponse.data.data); setMe(meResponse?.data.data || null); }
      }).catch(e=>{ if(!controller.signal.aborted) setError(teamError(e)); })
      .finally(()=>{if(!controller.signal.aborted) setLoading(false);});
    return ()=>controller.abort();
  },[reload]);
  const own = (id: number) => me?.applications.find(a=>a.window_id===id);
  const verified=me?.identity.account_status==='normal' && me.identity.school_name==='北京电子科技学院';
  const edit = (window: RecruitmentWindow) => {
    const application=own(window.id); setEditing(window); setSaveError('');
    form.setFieldsValue({motivation:application?.motivation || '',experience:application?.experience || me?.prefill.experience || '',contact:application?.contact || me?.identity.phone || '',
      gender:(me?.identity.gender===1?'男':me?.identity.gender===2?'女':application?.gender||''),politicalStatus:application?.political_status || '',
      email:me?.identity.email || application?.email || '',campusRank:application?.campus_rank || '',learningContent:application?.learning_content || '',
      studentWork:application?.student_work || me?.prefill.studentWork || '无',platformAccounts:application?.platform_accounts || me?.prefill.platformAccounts || ''});
  };
  const submit = async (values: Values) => {
    if(!editing) return; setSaving(true); setSaveError('');
    try {
      const response=await request.post('/team-recruitment/applications',{...values,windowId:editing.id,version:own(editing.id)?.version});
      if(response.data.code!==200) throw new Error(response.data.msg);
      message.success('申请已提交，请等待审核'); setEditing(null); setReload(n=>n+1);
    } catch(e) { setSaveError(teamError(e)); } finally { setSaving(false); }
  };
  const withdraw = async (application: TeamApplication) => {
    setSaving(true);
    try {
      const response=await request.post(`/team-recruitment/applications/${application.id}/withdraw`,{version:application.version});
      if(response.data.code!==200) throw new Error(response.data.msg);
      message.success('申请已撤回'); setReload(n=>n+1);
    } catch(e) { message.error(teamError(e)); } finally { setSaving(false); }
  };
  return <ConfigProvider locale={zhCN}><SiteHeader /><main style={{maxWidth:1100,margin:'110px auto 60px',padding:'0 20px'}}>
    <Typography.Title level={1}>申请加入 ACM 校队</Typography.Title>
    <Typography.Paragraph>在开放时间内提交申请，由校队管理员审核。以下时间均为北京时间。</Typography.Paragraph>
    {!loading && !me && <Alert type="info" showIcon title="登录并完成学生身份认证后即可申请" description={<Space><Link href="/login">登录</Link><Link href="/register">注册账号</Link></Space>} style={{marginBottom:20}} />}
    {me && !verified && <Alert type="info" showIcon title="请先完成北京电子科技学院学生身份认证" description="在个人主页提交学生卡，认证通过后可填写申请。" style={{marginBottom:20}} />}
    {me?.currentMember && <Alert type="success" showIcon title="你已是任期内的校队成员" description={<Link href="/community">进入校队交流区</Link>} style={{marginBottom:20}} />}
    {error && <Alert type="error" showIcon title={error} action={<Button onClick={()=>setReload(n=>n+1)}>重试</Button>} />}
    {loading ? <Spin /> : <Space orientation="vertical" size={18} style={{width:'100%'}}>
      {!error && windows.length===0 && <Empty description="暂未开放校队申请，请留意后续通知" />}
      {windows.map(window=> {
        const application=own(window.id), state=windowState(window,now);
        return <Card key={window.id} title={window.title} extra={<Tag color={state==='open'?'green':'default'}>{windowLabels[state]}</Tag>}>
          <Typography.Paragraph style={{whiteSpace:'pre-wrap'}}>{window.description}</Typography.Paragraph>
          <Descriptions column={1} size="small" items={[
            {key:'time',label:'申请时间',children:`${timeText(window.opens_at)} 至 ${timeText(window.closes_at)}`},
            {key:'term',label:'队员任期',children:`${window.member_start} 至 ${window.member_end}（${window.term}）`},
          ]} />
          <Space wrap style={{marginTop:18}}>
            {application && <Tag color={application.status==='approved'?'green':application.status==='rejected'?'red':'blue'}>{statusLabels[application.status]}</Tag>}
            <Button type="primary" disabled={!verified || me?.currentMember || state!=='open' || application?.status==='approved'} onClick={()=>edit(window)}>{application?.status==='pending'?'修改申请':application?'重新申请':'填写申请表'}</Button>
            {application?.status==='pending' && <Popconfirm title="撤回这份校队申请？" onConfirm={()=>withdraw(application)}><Button disabled={saving}>撤回申请</Button></Popconfirm>}
          </Space>
          {application?.review_note && <Typography.Paragraph style={{marginTop:12}}>审核意见：{application.review_note}</Typography.Paragraph>}
        </Card>;
      })}
      {!!me?.applications.length && <Card title="我的申请记录">
        {me.applications.map(application=><div key={application.id} style={{padding:'12px 0',borderBottom:'1px solid #eef1f5'}}>
          <Space wrap><Typography.Text strong>{application.title}</Typography.Text><Tag>{statusLabels[application.status]}</Tag><Typography.Text type="secondary">{timeText(application.created_at)}</Typography.Text><Button size="small" onClick={()=>setViewing(application)}>查看申请表</Button></Space>
          <Typography.Paragraph style={{margin:'8px 0',whiteSpace:'pre-wrap'}}>{application.motivation}</Typography.Paragraph>
          {application.review_note && <Typography.Text>审核意见：{application.review_note}</Typography.Text>}
          {application.status==='pending' && !windows.some(w=>w.id===application.window_id) && <Popconfirm title="撤回这份申请？" onConfirm={()=>withdraw(application)}><Button size="small" disabled={saving}>撤回</Button></Popconfirm>}
        </div>)}
      </Card>}
    </Space>}
    <Modal title={`ACM竞赛选拔报名表 · ${editing?.title || ''}`} open={!!editing} onCancel={()=>!saving && setEditing(null)}
      onOk={()=>form.submit()} okText="提交申请" confirmLoading={saving} okButtonProps={{disabled:!!editing && windowState(editing,now)!=='open'}} closable={!saving} maskClosable={!saving} width={720}>
      {me && <Typography.Paragraph>姓名：{me.identity.real_name}　学号：{me.identity.number_id}　学校：{me.identity.school_name}</Typography.Paragraph>}
      {editing && <Typography.Paragraph type="secondary">截止时间：{timeText(editing.closes_at)}（北京时间）</Typography.Paragraph>}
      {editing && windowState(editing,now)!=='open' && <Alert type="warning" title="申请时间已结束，无法提交" />}
      {saveError && <Alert type="error" showIcon title={saveError} />}
      <Typography.Paragraph type="secondary">账号资料自动带入；竞赛经历、学生工作及平台题量已根据站内记录预填，可按实际情况补充。题量使用网站当前统计。</Typography.Paragraph>
      <Form form={form} layout="vertical" onFinish={submit} disabled={saving}>
        <Row gutter={16}>
          <Col xs={24} sm={12}><Form.Item name="gender" label="性别" rules={[{required:true,whitespace:true}]}><Input maxLength={30} disabled={!!me?.identity.gender}/></Form.Item></Col>
          <Col xs={24} sm={12}><Form.Item name="politicalStatus" label="政治面貌" rules={[{required:true,whitespace:true}]}><Input maxLength={50} placeholder="例如：共青团员、群众"/></Form.Item></Col>
          <Col xs={24} sm={12}><Form.Item name="email" label="邮箱" rules={[{required:true},{type:'email',message:'请填写有效邮箱'}]}><Input maxLength={254} disabled={!!me?.identity.email}/></Form.Item></Col>
          <Col xs={24} sm={12}><Form.Item name="campusRank" label="校赛名次" rules={[{required:true,whitespace:true}]}><Input maxLength={160} placeholder="填写赛事名称及名次；未参赛填“未参赛”"/></Form.Item></Col>
        </Row>
        <Form.Item name="contact" label="联系方式" rules={[{required:true,whitespace:true}]}><Input maxLength={160} placeholder="手机号或 QQ 等常用联系方式"/></Form.Item>
        <Form.Item name="experience" label="主要竞赛经历" rules={[{required:true,whitespace:true}]}><Input.TextArea rows={3} maxLength={4000} showCount placeholder="比赛名称、时间及成绩；没有可填“无”"/></Form.Item>
        <Form.Item name="learningContent" label="近期竞赛学习内容" rules={[{required:true,whitespace:true}]}><Input.TextArea rows={3} maxLength={4000} showCount placeholder="近期学习的算法、训练内容及进度"/></Form.Item>
        <Form.Item name="studentWork" label="学生工作" rules={[{required:true,whitespace:true}]}><Input.TextArea rows={2} maxLength={2000} showCount placeholder="没有可填“无”"/></Form.Item>
        <Form.Item name="motivation" label="训练计划及竞赛目标" rules={[{required:true,whitespace:true}]}><Input.TextArea rows={3} maxLength={2000} showCount/></Form.Item>
        <Form.Item name="platformAccounts" label="各平台账户昵称及刷题量" rules={[{required:true,whitespace:true}]}><Input.TextArea rows={3} maxLength={4000} showCount placeholder="每个平台一行，例如：洛谷 / 用户名 / 100 题；尚无账号可如实填写"/></Form.Item>
      </Form>
    </Modal>
    <Modal title="ACM竞赛选拔报名表" open={!!viewing} onCancel={()=>setViewing(null)} footer={null} width={800}>{viewing&&<ApplicationDetails application={viewing}/>}</Modal>
  </main></ConfigProvider>;
}
