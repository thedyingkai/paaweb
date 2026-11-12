"use client";
import { Descriptions } from 'antd';
import type { TeamApplication } from './recruitment';
export default function ApplicationDetails({ application: a }: { application: TeamApplication }) {
  const fields: [string,string,number?][] = [
    ['姓名',a.real_name],['性别',a.gender],['学号',a.student_number],['政治面貌',a.political_status],
    ['邮箱',a.email],['校赛名次',a.campus_rank],['联系方式',a.contact,2],
    ['主要竞赛经历',a.experience,2],['近期竞赛学习内容',a.learning_content,2],['学生工作',a.student_work,2],
    ['训练计划及竞赛目标',a.motivation,2],['各平台账户昵称及刷题量',a.platform_accounts,2],
  ];
  return <Descriptions bordered size="small" column={{xs:1,sm:2}} style={{marginBottom:20}} items={fields.map(([label,value,span])=>({
    key:label,label,span:span||1,children:<span style={{whiteSpace:'pre-wrap',overflowWrap:'anywhere'}}>{value||'未填写'}</span>,
  }))} />;
}
