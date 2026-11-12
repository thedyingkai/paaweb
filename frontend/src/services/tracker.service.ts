import axios from 'axios';
const API_PREFIX = '/api';

export interface PlatformStat {
  name: string;
  count: number;
  color: string;
}

export interface ChartScope {
  difficultyDistribution: number[]; 
  timeLabels: string[];
  timeValues: number[];
}

export interface DashboardData {
  totalSolved: number;
  last7DaysSolved: number;
  last30DaysSolved: number;
  lastYearSolved: number;
  platformStats: PlatformStat[];
  scope7Days: ChartScope;
  scope30Days: ChartScope;
  scope1Year: ChartScope;
  dailyHeatmap: [string, number][]; 
  maxDifficultyHeatmap: [string, number][];
}

export interface UpcomingContest {
  upcomingContestsId: number;
  contestsName: string;
  contestsStarttime: string;
  url: string;
  sourceId: number;
}

export interface SubmissionQuery {
  sourceId?: number;
  difficultyRange?: string; 
  keyword?: string;
  problemId?: string;
  startTime?: string; 
  endTime?: string;  
  page: number;
  size: number;
}

export interface OjAccounts {
  codeforcesId?: string;
  atcoderId?: string;
  newcoderId?: string;
  luoguId?: string;
  cwnuojId?: string;
  qojId?: string;
  ucupId?: string;
}

export const TrackerService = {
  getOjAccounts: async (targetUserId?: string) => {
    let userId = targetUserId;
    if (!userId) {
      const userStr = localStorage.getItem('user_info');
      userId = userStr ? JSON.parse(userStr).userId : '';
    }
    return axios.get(`${API_PREFIX}/user/profile/${userId}`);
  },

  refreshTracker: async (platforms: string[], luoguClientId?: string, qojCookie?: string) => {
    const body = new URLSearchParams({ platforms: platforms.join(',') });
    if (luoguClientId?.trim()) body.set('luoguClientId', luoguClientId.trim());
    if (qojCookie?.trim()) body.set('qojCookie', qojCookie.trim());
    return axios.post(`${API_PREFIX}/tracker/refresh`, body);
  },

  getDashboard: async (userId?: string | number) => {
    return axios.get(`${API_PREFIX}/tracker/dashboard`, { params: { userId } });
  },

  getUpcomingContests: async () => {
    return axios.get(`${API_PREFIX}/tracker/upcoming-contests`);
  },

  searchSubmissions: async (query: SubmissionQuery) => {
    return axios.post(`${API_PREFIX}/tracker/submissions`, query);
  }
};
