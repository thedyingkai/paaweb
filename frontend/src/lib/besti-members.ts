export const memberRoles = [
  { value: 'science-club', organization: '科学之光技术联盟', title: '社团负责人', label: '（科学之光技术联盟）社团负责人' },
  { value: 'science-lead', organization: '科学之光技术联盟', title: '主要负责人', label: '（科学之光技术联盟）主要负责人' },
  { value: 'engineer-lead', organization: '计算机工程师联盟', title: '负责人', label: '（计算机工程师联盟）负责人' },
  { value: 'team-captain', organization: 'ACM 校队', title: '校队队长', label: '校队队长' },
  { value: 'team-member', organization: 'ACM 校队', title: '队员', label: '队员' },
];

export const memberOrganizations = ['科学之光技术联盟', '计算机工程师联盟', 'ACM 校队'];
export const awardLevels = ['省部级', '国家级', '校级'];
export const awardGrades = ['一等奖', '二等奖', '三等奖', '特等奖', '优秀奖'];
export const xcpcAwardGrades: Record<string, string> = { '金牌': '一等奖', '银牌': '二等奖', '铜牌': '三等奖', '优胜奖': '优秀奖' };
export const xcpcAwards = Object.keys(xcpcAwardGrades);
export const contestStageLabels: Record<string, string> = { regional: '区域赛', final: '总决赛', invitational: '邀请赛', national: '国赛', provincial: '省赛' };

export function awardStages(family?: string) {
  if (family === 'ICPC' || family === 'CCPC') return [
    { value: 'regional', label: '区域赛', level: '国家级' },
    { value: 'final', label: '总决赛', level: '国家级' },
    { value: 'invitational', label: '邀请赛', level: '省部级' },
  ];
  if (family === '蓝桥杯') return [
    { value: 'national', label: '国赛', level: '省部级' },
    { value: 'provincial', label: '省赛', level: '校级' },
  ];
  return [];
}

export function memberRoleKey(organization?: string, title?: string) {
  return memberRoles.find(role => role.organization === organization && role.title === title)?.value || '';
}

export function positionLabel(organization = '', title = '', status?: string) {
  const name = status === 'former' && !title.startsWith('前') ? `前${title}` : title;
  return organization && organization !== 'ACM 校队' ? `（${organization}）${name}` : name;
}

export const positionStatusLabels: Record<string, string> = {
  current: '现任', former: '已卸任', upcoming: '待任职', unknown: '任期待补充',
};

export function currentAcademicTerm() {
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit' }).formatToParts(new Date());
  const year = Number(today.find(part => part.type === 'year')?.value);
  const month = Number(today.find(part => part.type === 'month')?.value);
  const start = month >= 9 ? year : year - 1;
  return `${start}-${start + 1}学年`;
}

export function academicTermDates(term: string) {
  const match = /^(\d{4})-(\d{4})学年$/.exec(term);
  if (!match || Number(match[2]) !== Number(match[1]) + 1) return {};
  return { startDate: `${match[1]}-09-01`, endDate: `${match[2]}-08-31` };
}
