"use client";
import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Checkbox, Empty, Form, Input, Modal, Select, Space, Spin, Table, Tabs, Tag, Typography, Upload, message } from 'antd';
import { DownloadOutlined, UploadOutlined } from '@ant-design/icons';
import Link from 'next/link';
import SiteHeader from '../components/SiteHeader';
import { teamApi, teamError } from '@/lib/besti';
import { contestStageLabels } from '@/lib/besti-members';
import { downloadExcelTemplate, readExcelRows } from '@/utils/excelImport';

type Slot = { index: number; memberName: string; status: string; own: boolean; canClaim: boolean; claimId?: number; claimStatus?: string; claimVersion?: number };
type Honor = { uid: string; event?: string; title?: string; date?: string; academicYear?: string; family: string; tier?: string; team?: string; members?: string[]; official?: boolean; contestAward?: string; equivalentContestAward?: string; sourceHash: string; sources?: { name: string; url: string }[]; slots: Slot[] };
type Catalog = { items: Honor[]; total: number; realName: string; school: string; manager: boolean };
type Claim = { id: number; link_id: number; version: number; status: string; member_index: number; member_name: string; claimant_name: string; claimant_school: string; claimant_number: string; evidence_notes: string; evidence_url: string; review_note: string; updated_at: string; sourceChanged: boolean; honor: Honor };
type Assignment = { id: number; version: number; status: string; member_index: number; real_name: string; student_number: string; school_name: string; user_id?: number; sourceChanged: boolean; honor: Honor };
type RosterRow = { honorUid: string; memberIndex: number; school: string; studentNumber: string; realName: string };
type PreviewRow = { row: number; event?: string; team?: string; realName?: string; studentNumber?: string; status: string; message: string };
type Preview = { items: PreviewRow[]; errors: number; matched: number; waiting: number; token: string };
const labels: Record<string,string> = { pending:'待审核', approved:'已通过', rejected:'需补充材料', withdrawn:'已撤回', unassigned:'待认领', waiting:'待实名关联', linked:'已关联', revoked:'已撤销' };
const stageLabels: Record<string,string> = {...contestStageLabels,preliminary:'网络预选赛'};
const auditLabels: Record<string,string> = {claim:'提交认领',withdraw:'撤回申请',approve:'审核通过',reject:'退回申请',roster_import:'名单登记',identity_verified:'实名后自动关联',identity_changed:'实名信息变化',revoke:'撤销归属'};
const headers = ['战绩编号','队员序号','比赛名称','队伍名称','榜单姓名','学校','学号','姓名'];
const title = (h?: Honor) => h?.event || h?.title || '原始战绩暂不可用';
const badge = (h?: Honor) => h?.official === false
  ? <Tag color="orange">打星{h.equivalentContestAward ? ` · 等效${h.equivalentContestAward}` : '参赛'}</Tag>
  : <Tag color={h?.contestAward ? 'blue' : 'default'}>{h?.contestAward || '参赛记录'}</Tag>;
const sourceLinks = (h?: Honor) => <Space wrap>{h?.sources?.map(s => <a key={s.url} href={s.url} target="_blank" rel="noopener noreferrer">{s.name} ↗</a>)}</Space>;

export default function HistoryAwardsClient({admin = false}: {admin?: boolean}) {
  const [data,setData] = useState<Catalog>();
  const [busy,setBusy] = useState(false);
  const [error,setError] = useState('');
  const [keyword,setKeyword] = useState('');
  const [onlyMine,setOnlyMine] = useState(!admin);
  const [uid,setUid] = useState('');
  const [tab,setTab] = useState('catalog');
  const [claims,setClaims] = useState<Claim[]>([]);
  const [assignments,setAssignments] = useState<Assignment[]>([]);
  const [claimHonor,setClaimHonor] = useState<Honor>();
  const [review,setReview] = useState<Claim>();
  const [reviewNote,setReviewNote] = useState('');
  const [revoke,setRevoke] = useState<Assignment>();
  const [revokeNote,setRevokeNote] = useState('');
  const [single,setSingle] = useState<Honor>();
  const [importOpen,setImportOpen] = useState(false);
  const [roster,setRoster] = useState<RosterRow[]>([]);
  const [preview,setPreview] = useState<Preview>();
  const [saving,setSaving] = useState(false);
  const [auditRows,setAuditRows] = useState<Record<string,unknown>[] | null>(null);
  const [claimForm] = Form.useForm();
  const [singleForm] = Form.useForm();
  useEffect(() => {
    const selected=new URLSearchParams(window.location.search).get('uid') || '';
    if(selected) {setUid(selected);setOnlyMine(false);}
  },[]);
  const load = useCallback(async () => {
    setBusy(true);setError('');
    try {
      const query=new URLSearchParams({keyword,uid,mine:String(onlyMine)});
      const catalog=await teamApi<Catalog>('/history/catalog?'+query);
      if(admin && !catalog.manager) throw new Error('需要用户管理或系统管理权限');
      setData(catalog);
      if(tab==='claims' || tab==='review') setClaims(await teamApi<Claim[]>('/history/claims?review='+String(tab==='review')));
      if(tab==='links') setAssignments(await teamApi<Assignment[]>('/history/links'));
    } catch(e) {setError(teamError(e));} finally {setBusy(false);}
  },[admin,keyword,uid,onlyMine,tab]);
  useEffect(() => {void load();},[load]);
  const submitClaim = async (values: {memberIndex: number; notes: string; evidenceUrl?: string}) => {
    if(!claimHonor) return;
    setSaving(true);
    try {
      const slot=claimHonor.slots.find(s=>s.index===values.memberIndex);
      await teamApi('/history/claims','POST',{...values,honorUid:claimHonor.uid,sourceHash:claimHonor.sourceHash,version:slot?.claimVersion});
      message.success('认领申请已提交，等待管理员核验');setClaimHonor(undefined);setTab('claims');await load();
    } catch(e) {message.error(teamError(e));} finally {setSaving(false);}
  };
  const decide = async (approved: boolean) => {
    if(!review) return;
    if(!approved && !reviewNote.trim()) return void message.warning('请填写需补充的材料或退回原因');
    setSaving(true);
    try {
      await teamApi(`/history/claims/${review.id}/review`,'POST',{version:review.version,approved,note:reviewNote});
      message.success(approved?'已关联至成员个人档案':'已退回补充材料');setReview(undefined);await load();
    } catch(e) {message.error(teamError(e));} finally {setSaving(false);}
  };
  const exportRoster = async () => {
    try {
      const all=await teamApi<Catalog>('/history/catalog');
      downloadExcelTemplate({fileName:'BESTI_ACM_历史战绩归属名单.xlsx',headers,examples:all.items.flatMap(h=>h.slots.map(s=>[h.uid,s.index,title(h),h.team||'',s.memberName,'北京电子科技学院','',s.memberName]))});
    } catch(e) {message.error(teamError(e));}
  };
  const inspectRoster = async (rows: RosterRow[]) => {
    setSaving(true);setPreview(undefined);setRoster(rows);
    try {setPreview(await teamApi<Preview>('/history/roster/preview','POST',{rows}));setImportOpen(true);}
    catch(e) {message.error(teamError(e));} finally {setSaving(false);}
  };
  const readRoster = async (file: File) => {
    try {
      if(file.size>10*1024*1024) throw new Error('Excel 文件不能超过 10 MB');
      const rows=(await readExcelRows(file,headers)).filter(row=>row[6].trim()).map(row=>({honorUid:row[0],memberIndex:Number(row[1]),school:row[5],studentNumber:row[6],realName:row[7]}));
      if(!rows.length) throw new Error('请至少填写一行学号和姓名；未填学号的模板行会被跳过');
      await inspectRoster(rows);
    } catch(e) {message.error(teamError(e));}
    return false;
  };
  const applyRoster = async () => {
    if(!preview || preview.errors) return;
    setSaving(true);
    try {
      const result=await teamApi<{matched:number;waiting:number}>('/history/roster/import','POST',{rows:roster,token:preview.token});
      message.success(`已关联 ${result.matched} 个队员名额，${result.waiting} 个等待实名认证`);
      setImportOpen(false);setPreview(undefined);setRoster([]);setTab('links');await load();
    } catch(e) {message.error(teamError(e));setPreview(undefined);} finally {setSaving(false);}
  };
  const revokeLink = async () => {
    if(!revoke || !revokeNote.trim()) return void message.warning('请填写撤销原因');
    setSaving(true);
    try {await teamApi(`/history/links/${revoke.id}/revoke`,'POST',{version:revoke.version,note:revokeNote});message.success('已撤销归属');setRevoke(undefined);await load();}
    catch(e) {message.error(teamError(e));} finally {setSaving(false);}
  };
  const openClaim = (h: Honor) => {
    claimForm.resetFields();const available=h.slots.filter(s=>s.canClaim);
    claimForm.setFieldsValue({memberIndex:available.length===1?available[0].index:undefined});setClaimHonor(h);
  };
  const body=<>
    <Typography.Title level={2}>{admin?'历史战绩归属管理':'认领历史战绩'}</Typography.Title>
    <Typography.Paragraph type="secondary">将 ICPC、CCPC 历史战绩关联到实际参赛者。正式奖项、打星成绩和参赛经历分别保留原始记录。</Typography.Paragraph>
    <Space wrap style={{marginBottom:16}}>
      <Link href="/honor">查看学校历史战绩</Link><Link href="/team-space">我的参赛档案</Link>
      {!admin && data?.manager && <Link href="/admin/history-awards">管理归属与审核</Link>}
    </Space>
    {error && <Alert type="error" showIcon title={error} action={<Space><Link href="/login">登录</Link><Button onClick={()=>void load()}>重试</Button></Space>} style={{marginBottom:16}} />}
    {data && <>
      <Alert type="info" showIcon style={{marginBottom:20}} title={admin?'完整名单按学校、学号和姓名精确匹配；成员自行认领需人工审核。':'请认领你本人参加的比赛，说明队伍、参赛身份并提供证明链接。审核通过后进入个人档案。'} description={admin?'未注册或尚未通过实名审核的成员会保留为待关联，在身份审核通过后自动补关联。':'普通参赛学生也可以认领奖项；认领不会授予校队成员资格。'} />
      <Tabs activeKey={tab} onChange={setTab} items={[{key:'catalog',label:'战绩档案'},...(!admin?[{key:'claims',label:'我的认领申请'}]:[{key:'review',label:'认领审核'},{key:'links',label:'已登记归属'}])]} />
      <Spin spinning={busy}>
        {tab==='catalog' && <>
          <Space wrap style={{marginBottom:20}}>
            <Input.Search allowClear placeholder="比赛、队伍或队员姓名" onSearch={setKeyword} style={{width:280}} />
            {!admin && <Checkbox checked={onlyMine} onChange={e=>setOnlyMine(e.target.checked)}>只看同名或已申请的战绩</Checkbox>}
            {uid && <Button onClick={()=>setUid('')}>查看全部战绩</Button>}
            {admin && <><Button icon={<DownloadOutlined />} onClick={()=>void exportRoster()}>下载完整名单模板</Button><Button icon={<UploadOutlined />} onClick={()=>{setPreview(undefined);setRoster([]);setImportOpen(true);}}>导入归属名单</Button></>}
          </Space>
          <Table<Honor> rowKey="uid" dataSource={data.items} pagination={{pageSize:15,showSizeChanger:true,showTotal:n=>`共 ${n} 条战绩`}} scroll={{x:950}} locale={{emptyText:<Empty description={onlyMine?'没有同名战绩，可取消“只看同名”筛选查找':'当前筛选下没有历史战绩'} />}} columns={[
            {title:'比赛',render:(_,h)=><><strong>{title(h)}</strong><div><Tag>{h.family}</Tag><Tag>{stageLabels[h.tier||'']||h.tier}</Tag>{h.date||h.academicYear}</div></>},
            {title:'队伍与队员',render:(_,h)=><><div>{h.team||'未记录队名'}</div><Space direction="vertical" size={2}>{h.slots.map(s=><span key={s.index}>{s.memberName||`待补充队员 ${s.index}`} {s.own?<Tag color="green">已关联本人</Tag>:<Tag>{labels[s.claimStatus||s.status]||s.status}</Tag>}</span>)}</Space></>},
            {title:'比赛结果',render:(_,h)=>badge(h)},
            {title:'归属',width:150,render:(_,h)=>admin?<Button onClick={()=>{singleForm.resetFields();singleForm.setFieldsValue({school:'北京电子科技学院'});setSingle(h);}}>登记归属</Button>:<Button type="primary" disabled={!h.slots.some(s=>s.canClaim)} onClick={()=>openClaim(h)}>认领战绩</Button>},
          ]} expandable={{expandedRowRender:h=><Space direction="vertical"><span>来源：{sourceLinks(h)}</span>{!h.members?.length && <span>公开榜单没有队员姓名，认领时须补充身份与参赛证明，由管理员核验。</span>}<span>战绩编号：{h.uid}</span></Space>}} />
        </>}
        {(tab==='claims'||tab==='review') && <Table<Claim> rowKey="id" dataSource={claims} pagination={{pageSize:15}} scroll={{x:1000}} columns={[
          {title:'战绩',render:(_,c)=><><strong>{title(c.honor)}</strong><div>{c.honor?.team} · 队员 {c.member_index}</div>{badge(c.honor)}</>},
          ...(admin?[{title:'申请人',render:(_:unknown,c:Claim)=><>{c.claimant_name}<div>{c.claimant_school}</div><div>{c.claimant_number}</div></>}]:[]),
          {title:'状态',render:(_,c)=><><Tag color={c.status==='approved'?'green':c.status==='pending'?'blue':'default'}>{labels[c.status]}</Tag>{c.sourceChanged&&<Tag color="orange">原始战绩已更新</Tag>}<div>{c.review_note}</div></>},
          {title:'操作',render:(_,c)=><Space><Button onClick={()=>{setReview(c);setReviewNote('');}}>查看{admin&&c.status==='pending'?'并审核':''}</Button>{!admin&&c.status==='pending'&&<Button onClick={async()=>{try{await teamApi(`/history/claims/${c.id}/withdraw`,'POST',{version:c.version});message.success('已撤回');await load();}catch(e){message.error(teamError(e));}}}>撤回</Button>}</Space>},
        ]} />}
        {tab==='links' && <Table<Assignment> rowKey="id" dataSource={assignments} pagination={{pageSize:15}} scroll={{x:1050}} columns={[
          {title:'战绩',render:(_,r)=><><strong>{title(r.honor)}</strong><div>{r.honor?.team} · 队员 {r.member_index}</div>{badge(r.honor)}</>},
          {title:'归属身份',render:(_,r)=><>{r.real_name}<div>{r.school_name}</div><div>{r.student_number}</div></>},
          {title:'状态',render:(_,r)=><><Tag color={r.status==='linked'?'green':'orange'}>{labels[r.status]}</Tag>{r.sourceChanged&&<div><Tag color="orange">来源已更新，请核查</Tag></div>}{r.user_id&&<div><Link href={`/profile/${r.user_id}`}>查看个人档案</Link></div>}</>},
          {title:'操作',render:(_,r)=><Space><Button onClick={async()=>{try{setAuditRows(await teamApi<Record<string,unknown>[]>(`/history/links/${r.id}/audit`));}catch(e){message.error(teamError(e));}}}>操作记录</Button><Button danger onClick={()=>{setRevoke(r);setRevokeNote('');}}>撤销归属</Button></Space>},
        ]} />}
      </Spin>
    </>}
    <Modal title="认领历史战绩" open={!!claimHonor} onCancel={()=>setClaimHonor(undefined)} footer={null} destroyOnHidden>
      <Typography.Paragraph><strong>{title(claimHonor)}</strong><br/>{claimHonor?.team} {badge(claimHonor)}</Typography.Paragraph>
      <Typography.Paragraph>实名姓名：{data?.realName}。{sourceLinks(claimHonor)}</Typography.Paragraph>
      <Form form={claimForm} layout="vertical" onFinish={submitClaim}>
        <Form.Item name="memberIndex" label="榜单队员名额" rules={[{required:true,message:'请选择你本人对应的名额'}]}><Select options={claimHonor?.slots.filter(s=>s.canClaim).map(s=>({value:s.index,label:s.memberName||`待补充队员 ${s.index}`}))} /></Form.Item>
        <Form.Item name="notes" label="参赛身份与证明说明" rules={[{required:true,min:8,max:4000,message:'请填写至少 8 个字的参赛说明'}]}><Input.TextArea rows={4} placeholder="说明参赛年份、队伍、榜单上的姓名，以及可供核验的报名、证书或成绩材料。" maxLength={4000} showCount /></Form.Item>
        <Form.Item name="evidenceUrl" label="证明链接（可选）"><Input placeholder="证书验真、官方报名名单或其他证明材料链接" maxLength={1000} /></Form.Item>
        <Button type="primary" htmlType="submit" loading={saving}>提交认领申请</Button>
      </Form>
    </Modal>
    <Modal title={admin?'核验历史战绩认领':'我的认领申请'} open={!!review} onCancel={()=>setReview(undefined)} footer={admin&&review?.status==='pending'?<Space><Button danger loading={saving} onClick={()=>void decide(false)}>退回补充材料</Button><Button type="primary" loading={saving} disabled={review?.sourceChanged} onClick={()=>void decide(true)}>核验通过并关联</Button></Space>:null}>
      {review&&<Space direction="vertical" style={{width:'100%'}}>
        <strong>{title(review.honor)}</strong><span>{review.honor?.team} · 队员 {review.member_index} · {review.member_name||'榜单未列姓名'}</span>{badge(review.honor)}
        <span>认领人：{review.claimant_name} · {review.claimant_school} · {review.claimant_number}</span>
        <div style={{whiteSpace:'pre-wrap'}}>证明说明：{review.evidence_notes}</div>
        {review.evidence_url&&<a href={review.evidence_url} target="_blank" rel="noopener noreferrer">查看证明链接 ↗</a>}
        <span>原始榜单：{sourceLinks(review.honor)}</span>
        {review.sourceChanged&&<Alert type="warning" title="原始战绩已发生变化，请退回后重新提交认领。" />}
        {review.review_note&&<span>审核意见：{review.review_note}</span>}
        {admin&&review.status==='pending'&&<Input.TextArea value={reviewNote} onChange={e=>setReviewNote(e.target.value)} rows={3} maxLength={1000} placeholder="核验依据；退回时请写明需要补充的材料" />}
      </Space>}
    </Modal>
    <Modal title="登记队员归属" open={!!single} onCancel={()=>setSingle(undefined)} footer={null}>
      <Typography.Paragraph><strong>{title(single)}</strong><br/>{single?.team}</Typography.Paragraph>
      <Form form={singleForm} layout="vertical" onFinish={async(values)=>{if(!single)return;const h=single;setSingle(undefined);await inspectRoster([{honorUid:h.uid,...values}]);}}>
        <Form.Item name="memberIndex" label="队员名额" rules={[{required:true}]}><Select onChange={index=>singleForm.setFieldValue('realName',single?.slots.find(s=>s.index===index)?.memberName)} options={single?.slots.map(s=>({value:s.index,label:`${s.index} · ${s.memberName||'榜单未列姓名'}（${labels[s.status]}）`}))} /></Form.Item>
        <Form.Item name="school" label="学校" rules={[{required:true}]}><Input readOnly /></Form.Item>
        <Form.Item name="studentNumber" label="学号" rules={[{required:true}]}><Input maxLength={50} placeholder="用于与实名账号精确匹配" /></Form.Item>
        <Form.Item name="realName" label="姓名" rules={[{required:true}]}><Input maxLength={100} /></Form.Item>
        <Button type="primary" htmlType="submit" loading={saving}>预览身份匹配</Button>
      </Form>
    </Modal>
    <Modal title="导入历史战绩归属名单" width={1000} open={importOpen} onCancel={()=>setImportOpen(false)} footer={preview?<Button type="primary" loading={saving} disabled={preview.errors>0} onClick={()=>void applyRoster()}>确认登记 {preview.items.length} 个队员名额</Button>:null}>
      <Alert type="info" showIcon title="下载完整名单模板，补全学号和姓名。未填写学号的行会被跳过。" description="已通过实名审核且学校、学号、姓名完全一致的账号会自动关联；其余名单等待成员完成实名审核。此操作只关联历史战绩。" style={{marginBottom:16}} />
      <Space wrap style={{marginBottom:16}}><Button icon={<DownloadOutlined />} onClick={()=>void exportRoster()}>下载完整名单模板</Button><Upload accept=".xlsx,.xls" showUploadList={false} beforeUpload={readRoster}><Button icon={<UploadOutlined />} loading={saving}>选择名单并预检</Button></Upload></Space>
      {preview&&<><Alert style={{marginBottom:16}} showIcon type={preview.errors?'error':'success'} title={`精确匹配 ${preview.matched} 人次，待实名关联 ${preview.waiting} 人次，错误 ${preview.errors} 行`} />
        <Table<PreviewRow> size="small" rowKey="row" dataSource={preview.items} pagination={{pageSize:10}} scroll={{x:750}} columns={[{title:'Excel 行',dataIndex:'row'},{title:'比赛',dataIndex:'event'},{title:'队伍',dataIndex:'team'},{title:'姓名',dataIndex:'realName'},{title:'学号',dataIndex:'studentNumber'},{title:'匹配结果',dataIndex:'message',render:(v,r)=><span style={{color:r.status==='error'?'#cf1322':undefined}}>{v}</span>}]} /></>}
    </Modal>
    <Modal title="撤销历史战绩归属" open={!!revoke} onCancel={()=>setRevoke(undefined)} onOk={()=>void revokeLink()} okText="确认撤销" okButtonProps={{danger:true,loading:saving}}>
      <Typography.Paragraph>{title(revoke?.honor)} · {revoke?.real_name}</Typography.Paragraph><Input.TextArea value={revokeNote} onChange={e=>setRevokeNote(e.target.value)} rows={3} maxLength={1000} placeholder="请填写撤销原因，操作会保留审计记录" />
    </Modal>
    <Modal title="归属操作记录" open={auditRows!==null} onCancel={()=>setAuditRows(null)} footer={null} width={800}><Table rowKey="id" dataSource={auditRows||[]} pagination={{pageSize:8}} columns={[{title:'时间',dataIndex:'created_at',render:v=>String(v)},{title:'操作',dataIndex:'action',render:v=>auditLabels[String(v)]||String(v)},{title:'说明',dataIndex:'detail'}]} /></Modal>
  </>;
  return admin?<div style={{padding:'8px 0'}}>{body}</div>:<><SiteHeader /><main className="content-page-shell"><Card style={{maxWidth:1300,margin:'0 auto'}}>{body}</Card></main></>;
}
