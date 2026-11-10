import ShowcaseClient from './ShowcaseClient';
import type { CadreMember, CarouselItem, EventItem, HonorItem, NoticeItem, TrainingItem } from './ShowcaseClient';

export const dynamic = 'force-dynamic';

type ApiRecord = Record<string, unknown>;
type ApiResponse = {
    code?: number;
    data?: ApiRecord | ApiRecord[] | string[] | { records?: ApiRecord[]; content?: string };
};

const isApiRecord = (item: unknown): item is ApiRecord => {
    return typeof item === 'object' && item !== null && !Array.isArray(item);
};

export default async function Page() {
    // SSR Data Fetching
    const fetchApi = async (path: string): Promise<ApiResponse | null> => {
        const baseUrl = process.env.API_URL || 'http://localhost:8080';
        try {
            const res = await fetch(`${baseUrl}${path}`, { next: { revalidate: 60 } });
            return await res.json();
        } catch (e) {
            console.error('Fetch error:', path, e);
            return null;
        }
    };

    const bgRes = await fetchApi('/intro/showcase_bg');
    const honorRes = await fetchApi('/intro/honors');
    const noticeRes = await fetchApi('/notice/list?type=notice&size=5');
    const newsRes = await fetchApi('/notice/list?type=news&size=5');
    const publicityRes = await fetchApi('/notice/list?type=publicity&size=5');
    const trainingRes = await fetchApi('/training/list');
    const eventRes = await fetchApi('/event/list');
    
    // 干事数据需要两步
    let cadres: CadreMember[] = [];
    const termRes = await fetchApi('/association-member/terms');
    const terms = Array.isArray(termRes?.data) ? termRes.data.filter((item): item is string => typeof item === 'string') : [];
    if (termRes?.code === 200 && terms.length > 0) {
        const latestTerm = terms.sort((a: string, b: string) => b.localeCompare(a))[0];
        const memberRes = await fetchApi(`/association-member/list?term=${encodeURIComponent(latestTerm)}`);
        if (memberRes?.code === 200 && Array.isArray(memberRes.data)) {
            cadres = memberRes.data.filter(isApiRecord) as unknown as CadreMember[];
        }
    }

    let initialBgs: CarouselItem[] = [];
    if (bgRes?.code === 200 && !Array.isArray(bgRes.data) && typeof bgRes.data === 'object' && typeof bgRes.data?.content === 'string') {
        try { initialBgs = JSON.parse(bgRes.data.content) as CarouselItem[]; } catch {}
    }

    let initialHonors: HonorItem[] = [];
    if (honorRes?.code === 200 && !Array.isArray(honorRes.data) && typeof honorRes.data === 'object' && typeof honorRes.data?.content === 'string') {
        try { initialHonors = JSON.parse(honorRes.data.content) as HonorItem[]; } catch {}
    }

    const getRecords = (res: ApiResponse | null): NoticeItem[] => {
        if (res?.code !== 200 || Array.isArray(res.data) || typeof res.data !== 'object' || !res.data) return [];
        return Array.isArray(res.data.records) ? (res.data.records as NoticeItem[]) : [];
    };
    const getList = (res: ApiResponse | null): ApiRecord[] => {
        return res?.code === 200 && Array.isArray(res.data) ? res.data.filter(isApiRecord) : [];
    };

    const initialNotices = getRecords(noticeRes);
    const initialNews = getRecords(newsRes);
    const initialPublicity = getRecords(publicityRes);
    const initialTrainings = getList(trainingRes).slice(0, 4) as unknown as TrainingItem[];
    const initialEvents = getList(eventRes).slice(0, 4) as unknown as EventItem[];

    return (
        <ShowcaseClient 
            initialBgs={initialBgs}
            initialHonors={initialHonors}
            initialNotices={initialNotices}
            initialNews={initialNews}
            initialPublicity={initialPublicity}
            initialTrainings={initialTrainings}
            initialEvents={initialEvents}
            initialCadres={cadres}
        />
    );
}
