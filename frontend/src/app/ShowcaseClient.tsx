"use client";
import React, { useEffect, useState, useRef, useCallback } from 'react';
import { Card, Row, Col, Typography, Button, Empty, Avatar, Tag } from 'antd';
import { useRouter } from 'next/navigation';
import { 
    TrophyOutlined,
    SettingOutlined, 
    ArrowRightOutlined, 
    ArrowDownOutlined, 
    UserOutlined, 
    DoubleRightOutlined,
    GlobalOutlined,
    NotificationOutlined,
    BookOutlined,
    CalendarOutlined,
    ClockCircleOutlined,
    FileTextOutlined,
    FireOutlined,
} from '@ant-design/icons';
import axios from 'axios';
import dayjs from 'dayjs';
import SiteHeader from './components/SiteHeader';
import { TeamLogo } from './components/TeamBrand';
import { memberRoles, memberRoleKey, positionLabel } from '@/lib/besti-members';
import Link from 'next/link';

const { Title } = Typography;

const defaultBgs = ['/paa-background.svg'];

export interface CarouselItem {
    imageUrl: string;
    linkUrl: string;
    title?: string;
    theme?: 'light' | 'dark';
}

export interface HonorItem {
    imageUrl: string;
    title?: string;
    award?: string;
    contestAward?: string;
    awardLevel?: string;
    awardGrade?: string;
    team?: string;
    sources?: {name: string; url: string}[];
}

export interface NoticeItem {
    infoId: number;
    title: string;
    createTime: string;
    type: string;
}

export interface EventItem {
    signupMode?: string;
    eventId: number;
    name: string;
    signupStart: string;
    signupEnd: string;
    eventStart: string;
    eventEnd: string;
    status?: number;
}

export interface TrainingItem {
    trainId: number;
    name: string;
    signupStart: string;
    signupEnd: string;
    trainStart: string;
    trainEnd: string;
    termName?: string;
    teacherName?: string;
    publisherName?: string;
    signupCount?: number;
    maxCount?: number;
}

export interface CadreMember {
    memberId: number;
    userId?: number;
    realName: string;
    department: string;
    position: string;
    displayPosition?: string;
    positionStatus?: string;
    startDate?: string;
    endDate?: string;
    sortOrder?: number;
    avatarUrl?: string;
    introduction?: string;
}

interface ShowcaseProps {
    initialBgs: CarouselItem[];
    initialHonors: HonorItem[];
    initialNotices: NoticeItem[];
    initialNews: NoticeItem[];
    initialPublicity: NoticeItem[];
    initialTrainings: TrainingItem[];
    initialEvents: EventItem[];
    initialCadres: CadreMember[];
}

const defaultDeptOrder = memberRoles.map(role => role.value);

const sortCadres = (members: CadreMember[]) => {
    return [...members].sort((a, b) => {
        const indexA = defaultDeptOrder.indexOf(memberRoleKey(a.department, a.position));
        const indexB = defaultDeptOrder.indexOf(memberRoleKey(b.department, b.position));

        if (indexA !== -1 && indexB !== -1 && indexA !== indexB) return indexA - indexB;
        if (indexA !== -1 && indexB === -1) return -1;
        if (indexA === -1 && indexB !== -1) return 1;
        if (a.department !== b.department) return String(a.department).localeCompare(String(b.department));
        return (a.sortOrder || 0) - (b.sortOrder || 0);
    });
};

const sortDepartments = (departments: string[]) => {
    return [...departments].sort((a, b) => {
        const indexA = defaultDeptOrder.indexOf(a);
        const indexB = defaultDeptOrder.indexOf(b);
        if (indexA !== -1 && indexB !== -1) return indexA - indexB;
        if (indexA !== -1) return -1;
        if (indexB !== -1) return 1;
        return a.localeCompare(b);
    });
};

const getTrainingStatus = (item: TrainingItem) => {
    const now = dayjs();
    if (now.isBefore(item.signupStart)) return { text: '报名未开始', color: 'orange', order: 2 };
    if (now.isBefore(item.signupEnd)) return { text: '报名进行中', color: 'green', order: 1 };
    return { text: '报名已结束', color: 'red', order: 3 };
};

const getEventStatus = (item: EventItem) => {
    const now = dayjs();
  if (item.signupMode === 'roster' && now.isBefore(item.eventStart)) return { text: '名单已确定', color: 'blue', status: 'waiting', order: 3 };
  if (item.signupMode === 'roster') return now.isBefore(item.eventEnd) ? { text: '正在比赛中', color: 'red', status: 'ongoing', order: 4 } : { text: '已结束', color: 'default', status: 'ended', order: 5 };
    if (now.isBefore(item.signupStart)) return { text: '报名未开始', color: 'orange', order: 2 };
    if (!item.signupEnd || now.isBefore(item.signupEnd)) return { text: '报名进行中', color: 'green', order: 1 };
    if (now.isBefore(item.eventStart)) return { text: '等待开赛', color: 'blue', order: 3 };
    if (now.isBefore(item.eventEnd)) return { text: '正在比赛中', color: 'red', order: 4 };
    return { text: '比赛已结束', color: 'default', order: 5 };
};

const sortTrainings = (items: TrainingItem[]) => {
    return [...items].sort((a, b) => {
        const statusA = getTrainingStatus(a);
        const statusB = getTrainingStatus(b);
        if (statusA.order !== statusB.order) return statusA.order - statusB.order;
        return dayjs(a.signupEnd).valueOf() - dayjs(b.signupEnd).valueOf();
    });
};

const sortEvents = (items: EventItem[]) => {
    return [...items].sort((a, b) => {
        const statusA = getEventStatus(a);
        const statusB = getEventStatus(b);
        if (statusA.order !== statusB.order) return statusA.order - statusB.order;
        return dayjs(a.eventStart).valueOf() - dayjs(b.eventStart).valueOf();
    });
};

const ShowcaseHome: React.FC<ShowcaseProps> = ({
    initialBgs,
    initialHonors,
    initialNotices,
    initialNews,
    initialPublicity,
    initialTrainings,
    initialEvents,
    initialCadres
}) => {

    const router = useRouter();
    const navigate = router.push;
    const sectionRefs = useRef<Array<HTMLElement | null>>([]);
    
    // States
    // 首屏和站内切换统一由 GlobalRouteLoader 负责，避免首页接口请求缓慢时
    // 再次叠加一个长时间的 LOGO 加载层。
    const [loadingVisible, setLoadingVisible] = useState(false);
    const [loadingFade, setLoadingFade] = useState(false);
    const [activeIndex, setActiveIndex] = useState(0);
    // Keep sections revealed once they have entered the viewport.  On mobile,
    // section heights are content-driven, so deriving this from viewport-height
    // multiples can leave otherwise visible content permanently transparent.
    const [visibleSections, setVisibleSections] = useState<Set<number>>(() => new Set([0]));
    const [carouselList, setCarouselList] = useState<CarouselItem[]>(initialBgs || []);
    const [currentBgIndex, setCurrentBgIndex] = useState(0);
    const [honorsList, setHonorsList] = useState<HonorItem[]>(initialHonors || []);
    const [noticeList, setNoticeList] = useState<NoticeItem[]>(initialNotices || []);
    const [newsList, setNewsList] = useState<NoticeItem[]>(initialNews || []);
    const [publicityList, setPublicityList] = useState<NoticeItem[]>(initialPublicity || []);
    const [trainingList, setTrainingList] = useState<TrainingItem[]>(initialTrainings || []);
    const [localEvents, setLocalEvents] = useState<EventItem[]>(initialEvents || []);
    const initialSortedCadres = sortCadres(initialCadres || []);
    const initialDepartments = sortDepartments(Array.from(new Set(initialSortedCadres.map((m) => memberRoleKey(m.department, m.position)))));
    const [cadreList, setCadreList] = useState<CadreMember[]>(initialSortedCadres);
    const [selectedDept, setSelectedDept] = useState<string>(initialDepartments[0] || '');
    const backgroundImageCount = carouselList.length > 0 ? carouselList.length : defaultBgs.length;
    const backgroundSlideCount = backgroundImageCount + 1;

    // Fetch data
    useEffect(() => {
        let cancelled = false;

        const hideLoading = () => {
            if (cancelled) return;
            if (typeof window !== 'undefined') {
                window.__paa_first_load_done__ = true;
            }
            setLoadingFade(true);
            const removeTimer = setTimeout(() => {
                setLoadingVisible(false);
            }, 800);
            return () => clearTimeout(removeTimer);
        };

        // 1. Fetch ShowcaseHome background carousel images
        const bgRequest = axios.get('/api/intro/showcase_bg').then(res => {
            if (res.data.code === 200 && res.data.data) {
                try {
                    const parsed = JSON.parse(res.data.data.content);
                    if (Array.isArray(parsed) && parsed.length > 0) {
                        setCarouselList(parsed);
                    }
                } catch (e) {
                    console.error("首屏背景轮播图解析失败", e);
                }
            }
        });

        // 2. Fetch honors
        const honorsRequest = axios.get('/api/intro/honors').then(res => {
            if (res.data.code === 200 && res.data.data) {
                try {
                    const parsed: { uid: string; imageUrl: string; title?: string; academicYear?: string; sortOrder?: number }[] = JSON.parse(res.data.data.content);
                    if (Array.isArray(parsed) && parsed.length > 0) {
                        // Find latest academic year
                        const years = [...new Set(parsed.map(h => h.academicYear).filter(Boolean) as string[])].sort((a, b) => b.localeCompare(a));
                        const latestYear = years[0];
                        // Filter to latest year (or all if none tagged), then sort by sortOrder asc
                        const filtered = latestYear
                            ? parsed.filter(h => h.academicYear === latestYear)
                            : parsed;
                        const sorted = [...filtered].sort((a, b) => (a.sortOrder ?? 9999) - (b.sortOrder ?? 9999));
                        setHonorsList(sorted);
                    }
                } catch (e) {
                    console.error("荣誉墙解析失败", e);
                }
            }
        });

        // 3. Fetch notices, news, publicity
        const newsRequest = Promise.all([
            axios.get('/api/notice/list', { params: { type: 'notice', size: 5 } }),
            axios.get('/api/notice/list', { params: { type: 'news', size: 5 } }),
            axios.get('/api/notice/list', { params: { type: 'publicity', size: 5 } })
        ]).then(([noticeRes, newsRes, publicityRes]) => {
            if (noticeRes.data.code === 200 && noticeRes.data.data) {
                setNoticeList(noticeRes.data.data.records || []);
            }
            if (newsRes.data.code === 200 && newsRes.data.data) {
                setNewsList(newsRes.data.data.records || []);
            }
            if (publicityRes.data.code === 200 && publicityRes.data.data) {
                setPublicityList(publicityRes.data.data.records || []);
            }
        }).catch(error => {
            console.error("校队要闻加载失败", error);
        });

        // 4. Fetch trainings and contests
        const eventsRequest = Promise.allSettled([
            axios.get('/api/training/list'),
            axios.get('/api/event/list')
        ]).then(([trainingRes, eventRes]) => {
            if (trainingRes.status === 'fulfilled' && trainingRes.value.data.code === 200) {
                setTrainingList(sortTrainings(trainingRes.value.data.data || []).slice(0, 4));
            }
            if (eventRes.status === 'fulfilled' && eventRes.value.data.code === 200) {
                setLocalEvents(sortEvents(eventRes.value.data.data || []).slice(0, 4));
            }
        }).catch(error => {
            console.error("培训赛事加载失败", error);
        });

        // 5. Fetch member positions
        const cadresRequest = axios.get('/api/association-member/terms').then(termsRes => {
            if (termsRes.data.code === 200 && termsRes.data.data.length > 0) {
                const latestTerm = termsRes.data.data.sort((a: string, b: string) => b.localeCompare(a))[0];
                return axios.get(`/api/association-member/list?term=${encodeURIComponent(latestTerm)}`).then(res => {
                    if (res.data.code === 200 && res.data.data) {
                        const members = sortCadres(res.data.data as CadreMember[]);
                        setCadreList(members);

                        if (members.length > 0) {
                            const uniqueDepts = Array.from(new Set(members.map((m) => memberRoleKey(m.department, m.position))));
                            const sortedDepts = sortDepartments(uniqueDepts);
                            setSelectedDept(sortedDepts[0]);
                        }
                    }
                });
            }
        });

        Promise.allSettled([bgRequest, honorsRequest, newsRequest, eventsRequest, cadresRequest]).then(() => {
            hideLoading();
        });

        return () => {
            cancelled = true;
        };
    }, []);

    // Index 0 is the welcome slide over the global dynamic background.
    useEffect(() => {
        if (backgroundSlideCount <= 1) return;
        const bgTimer = window.setInterval(() => {
            setCurrentBgIndex(prev => (prev + 1) % backgroundSlideCount);
        }, 6000);
        return () => window.clearInterval(bgTimer);
    }, [backgroundSlideCount]);

    const setSectionRef = useCallback((index: number) => (element: HTMLElement | null) => {
        sectionRefs.current[index] = element;
    }, []);

    // Track the section currently crossing the middle of the viewport.  This
    // works for both desktop full-screen sections and mobile content-sized ones.
    const handleScroll = useCallback(() => {
        const firstSection = sectionRefs.current[0];
        if (firstSection && window.scrollY <= Math.max(2, firstSection.offsetHeight * 0.35)) {
            setActiveIndex((currentIndex) => currentIndex === 0 ? currentIndex : 0);
            return;
        }

        const anchor = window.innerHeight * 0.45;
        let index = 0;
        let closestDistance = Number.POSITIVE_INFINITY;

        sectionRefs.current.forEach((section, sectionIndex) => {
            if (!section) return;
            const rect = section.getBoundingClientRect();
            if (rect.top <= anchor && rect.bottom >= anchor) {
                index = sectionIndex;
                closestDistance = 0;
                return;
            }
            if (closestDistance > 0) {
                const distance = Math.abs(rect.top - anchor);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    index = sectionIndex;
                }
            }
        });

        setActiveIndex((currentIndex) => currentIndex === index ? currentIndex : index);
    }, []);

    useEffect(() => {
        window.addEventListener('scroll', handleScroll, { passive: true });
        return () => window.removeEventListener('scroll', handleScroll);
    }, [handleScroll]);

    useEffect(() => {
        const observer = new IntersectionObserver((entries) => {
            const enteredSections = entries
                .filter((entry) => entry.isIntersecting)
                .map((entry) => Number(entry.target.getAttribute('data-showcase-section')))
                .filter(Number.isInteger);

            if (enteredSections.length === 0) return;
            setVisibleSections((currentSections) => {
                const nextSections = new Set(currentSections);
                enteredSections.forEach((index) => nextSections.add(index));
                return nextSections.size === currentSections.size ? currentSections : nextSections;
            });
        }, { threshold: 0.01, rootMargin: '0px 0px -5% 0px' });

        sectionRefs.current.forEach((section) => {
            if (section) observer.observe(section);
        });

        return () => observer.disconnect();
    }, []);

    const scrollToSection = (index: number) => {
        sectionRefs.current[index]?.scrollIntoView({
            behavior: 'smooth',
            block: 'start'
        });
    };

    const formatCadrePosition = (member: CadreMember) => {
        return member.displayPosition || positionLabel(member.department, member.position, member.positionStatus);
    };

    // Header styling variables: premium light glassmorphism
    const headerBg = activeIndex === 0
        ? 'linear-gradient(90deg, rgba(255,255,255,0.54), rgba(246,252,255,0.42))'
        : 'linear-gradient(90deg, rgba(255,255,255,0.78), rgba(247,252,255,0.64))';
        
    const headerBorder = activeIndex === 0
        ? '1px solid rgba(120, 180, 220, 0.16)'
        : '1px solid rgba(120, 180, 220, 0.24)';
        
    const headerBrandColor = '#2c3e50';

    const headerLinkColor = (isActive = false) => {
        if (isActive) return '#1890ff';
        return '#595959';
    };

    const overlayBg = currentBgIndex === 0
        ? 'rgba(255, 255, 255, 0)'
        : 'rgba(255, 255, 255, 0.08)';

    // Font color and outline styles (Welcome page is ALWAYS clean light theme style now)
    const mainTitleColor = '#1f1f1f';
    const mainTitleOutline = 'none';
    const gradientTextStroke = 'none';

    const subtitleColor = '#595959';
    const subtitleOutline = 'none';

    const tagBg = 'rgba(24, 144, 255, 0.08)';
    const tagBorder = '1px solid rgba(24, 144, 255, 0.15)';
    const tagTextColor = '#1890ff';

    const infoBtnStyle = { 
        height: 48, 
        padding: '0 28px', 
        fontSize: 16, 
        border: '1px solid #d9d9d9', 
        background: 'rgba(255, 255, 255, 0.8)', 
        color: '#262626', 
        backdropFilter: 'blur(8px)' 
    };

    const scrollTextShadow = 'none';
    const scrollTextColor = '#8c8c8c';
    const gearsShadow = 'none';

    const noticeGroups = [
        { title: '通知', icon: <NotificationOutlined />, color: '#1890ff', list: noticeList, type: 'notice' },
        { title: '新闻', icon: <FireOutlined />, color: '#ff4d4f', list: newsList, type: 'news' },
        { title: '公示', icon: <FileTextOutlined />, color: '#52c41a', list: publicityList, type: 'publicity' }
    ];

    const getNoticeRoute = (type: string, id: number) => {
        if (type === 'news') return `/news/${id}`;
        if (type === 'publicity') return `/publicity/${id}`;
        return `/notice/${id}`;
    };

    const getNoticeListRoute = (type: string) => {
        if (type === 'news') return '/news';
        if (type === 'publicity') return '/publicity';
        return '/notice';
    };

    const renderEmpty = (text: string) => (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={text} style={{ padding: '20px 0' }} />
    );

    const renderNoticeCard = (item: NoticeItem, color: string, type: string) => (
        <Link
            key={item.infoId}
            href={getNoticeRoute(type, item.infoId)}
            style={{
                display: 'block',
                color: 'inherit',
                textDecoration: 'none',
                padding: '14px 0',
                borderBottom: '1px solid rgba(0,0,0,0.06)',
                cursor: 'pointer'
            }}
        >
            <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
                <div style={{
                    width: 46,
                    height: 46,
                    borderRadius: 8,
                    background: `${color}14`,
                    color,
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0
                }}>
                    <span style={{ fontSize: 16, fontWeight: 800, lineHeight: 1 }}>{dayjs(item.createTime).format('DD')}</span>
                    <span style={{ fontSize: 10, marginTop: 3 }}>{dayjs(item.createTime).format('MM月')}</span>
                </div>
                <div style={{ minWidth: 0, flex: 1 }}>
                    <h3 style={{
                        fontSize: 15,
                        fontWeight: 700,
                        color: '#262626',
                        margin: '0 0 6px',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap'
                    }}>
                        {item.title}
                    </h3>
                    <span style={{ color: '#8c8c8c', fontSize: 12 }}>
                        <CalendarOutlined /> {dayjs(item.createTime).format('YYYY-MM-DD')}
                    </span>
                </div>
            </div>
        </Link>
    );

    const renderTrainingCard = (item: TrainingItem) => {
        const status = getTrainingStatus(item);
        return (
            <Link key={item.trainId} href={`/training/${item.trainId}`} style={{ display: 'block', color: 'inherit', textDecoration: 'none', padding: '12px 0', borderBottom: '1px solid rgba(0,0,0,0.06)', cursor: 'pointer' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10, marginBottom: 8, minWidth: 0 }}>
                    <h3 style={{ fontSize: 15, fontWeight: 700, color: '#262626', margin: 0, minWidth: 0, flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{item.name}</h3>
                    <Tag color={status.color} style={{ flexShrink: 0, margin: 0 }}>{status.text}</Tag>
                </div>
                <div style={{ color: '#8c8c8c', fontSize: 12, lineHeight: 1.8 }}>
                    <div><UserOutlined /> 讲师：{item.teacherName || '待定'}</div>
                    <div><ClockCircleOutlined /> 开课：{dayjs(item.trainStart).format('MM-DD HH:mm')}</div>
                </div>
            </Link>
        );
    };

    const renderLocalEventCard = (item: EventItem) => {
        const status = getEventStatus(item);
        return (
            <Link key={item.eventId} href={`/contest/signup/${item.eventId}`} style={{ display: 'block', color: 'inherit', textDecoration: 'none', padding: '12px 0', borderBottom: '1px solid rgba(0,0,0,0.06)', cursor: 'pointer' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10, marginBottom: 8, minWidth: 0 }}>
                    <h3 style={{ fontSize: 15, fontWeight: 700, color: '#262626', margin: 0, minWidth: 0, flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{item.name}</h3>
                    <Tag color={status.color} style={{ flexShrink: 0, margin: 0 }}>{status.text}</Tag>
                </div>
                <div style={{ color: '#8c8c8c', fontSize: 12 }}>
                    <ClockCircleOutlined /> 比赛：{dayjs(item.eventStart).format('MM-DD HH:mm')}
                </div>
            </Link>
        );
    };

    return (
        <div className="showcase-root" style={{ position: 'relative', width: '100%', minHeight: '100vh', overflow: 'visible' }}>
            {/* 艺术字体与样式样式引入 */}
            <style>{`
                
                .showcase-font-title {
                    font-family: 'Space Grotesk', 'Outfit', -apple-system, sans-serif;
                    letter-spacing: -0.5px;
                }
                .showcase-font-body {
                    font-family: 'Outfit', -apple-system, sans-serif;
                }
                
                .showcase-header a {
                    transition: color 0.3s ease, opacity 0.3s ease;
                }
                .showcase-header a:hover {
                    color: #1890ff !important;
                    opacity: 1 !important;
                }
                
                .showcase-scroll-container {
                    height: auto;
                    overflow: visible;
                    scroll-behavior: smooth;
                    position: relative;
                    z-index: 10;
                }
                .showcase-scroll-container::-webkit-scrollbar {
                    width: 6px;
                }
                .showcase-scroll-container::-webkit-scrollbar-thumb {
                    background: rgba(24, 144, 255, 0.3);
                    border-radius: 3px;
                }
                .showcase-scroll-container::-webkit-scrollbar-track {
                    background: transparent;
                }
                .showcase-cadres-scroll::-webkit-scrollbar {
                    width: 6px;
                }
                .showcase-cadres-scroll::-webkit-scrollbar-thumb {
                    background: rgba(24, 144, 255, 0.3);
                    border-radius: 3px;
                }
                .showcase-cadres-scroll::-webkit-scrollbar-track {
                    background: transparent;
                }
                
                .showcase-section {
                    height: 100vh;
                    position: relative;
                    overflow: hidden;
                    display: flex;
                    flex-direction: column;
                    justify-content: center;
                    box-sizing: border-box;
                    padding: 72px 20px 20px 20px;
                }

                .showcase-schedule-grid {
                    display: grid;
                    grid-template-columns: repeat(3, minmax(0, 1fr));
                    gap: 20px;
                    align-items: stretch;
                }
                .showcase-schedule-column {
                    min-width: 0;
                    height: 100%;
                }
                .showcase-schedule-card-title {
                    min-width: 0;
                    flex: 1;
                }
                
                /* Responsive layout for mobile */
                @media (max-width: 768px) {
                    .showcase-scroll-container {
                        overflow: visible;
                    }
                    .aurora-container {
                        position: absolute !important;
                        top: 60px !important;
                        left: 0 !important;
                        height: auto !important;
                        min-height: 0 !important;
                        aspect-ratio: 16 / 9;
                        max-height: none;
                        margin-top: 0;
                        background: transparent !important;
                    }
                    .aurora-container .bg-slider-image {
                        background-size: cover;
                        background-repeat: no-repeat;
                        background-position: center center;
                    }
                    .aurora-container .noise-overlay,
                    .aurora-container .aurora-blob {
                        display: none;
                    }
                    .showcase-section {
                        height: auto;
                        min-height: 0;
                        padding: 48px 16px;
                    }
                    .showcase-section.showcase-hero-section {
                        width: 100%;
                        aspect-ratio: 16 / 9;
                        min-height: auto;
                        margin-top: 60px;
                        padding: 8px 44px;
                    }
                    .showcase-hero-content {
                        width: 100% !important;
                        padding: 8px 0 !important;
                    }
                    .showcase-hero-tag {
                        padding: 1px 8px !important;
                        margin-bottom: 7px !important;
                        font-size: 11px !important;
                        line-height: 18px !important;
                    }
                    .showcase-hero-title {
                        margin-bottom: 5px !important;
                        font-size: clamp(24px, 7vw, 34px) !important;
                        line-height: 1.1 !important;
                    }
                    .showcase-hero-subtitle {
                        margin-bottom: 12px !important;
                        font-size: clamp(12px, 3.5vw, 15px) !important;
                        line-height: 1.3 !important;
                        letter-spacing: 1.5px !important;
                    }
                    .showcase-hero-action .ant-btn {
                        height: 32px !important;
                        padding-inline: 14px !important;
                        font-size: 12px !important;
                    }
                    .showcase-scroll-cue {
                        display: none !important;
                    }
                    .showcase-section:not(.showcase-hero-section) .ant-typography {
                        font-size: 22px !important;
                        line-height: 1.25 !important;
                    }
                    .showcase-section-heading-row {
                        align-items: flex-start !important;
                        gap: 12px;
                    }
                    .showcase-section-heading-row > div {
                        min-width: 0;
                    }
                    .showcase-section-heading-row .ant-btn {
                        padding-inline: 4px;
                    }
                    .showcase-section .ant-card-body {
                        padding: 18px 16px !important;
                    }
                    .showcase-cadres-scroll {
                        max-height: none !important;
                        overflow-y: visible !important;
                    }
                    .showcase-schedule-grid {
                        grid-template-columns: 1fr;
                        gap: 16px;
                    }
                    .showcase-nav-links {
                        display: none !important;
                    }
                    .showcase-header {
                        padding: 0 16px !important;
                    }
                }

                /* Staggered revealing animation keyframes */
                .reveal-item {
                    opacity: 0;
                    transform: translateY(40px);
                    transition: opacity 0.8s cubic-bezier(0.16, 1, 0.3, 1), transform 0.8s cubic-bezier(0.16, 1, 0.3, 1);
                }
                .section-active .reveal-item {
                    opacity: 1;
                    transform: translateY(0);
                }
                .reveal-delay-1 { transition-delay: 0.1s; }
                .reveal-delay-2 { transition-delay: 0.2s; }
                .reveal-delay-3 { transition-delay: 0.3s; }
                .reveal-delay-4 { transition-delay: 0.4s; }
                .reveal-delay-5 { transition-delay: 0.5s; }
                
                /* Glowing soft aurora blobs */
                .aurora-blob {
                    position: absolute;
                    width: 550px;
                    height: 550px;
                    border-radius: 50%;
                    filter: blur(140px);
                    opacity: 0.13;
                    mix-blend-mode: multiply;
                    pointer-events: none;
                    z-index: 2;
                }
                .aurora-blue {
                    background: #1890ff;
                    top: -10%;
                    left: -5%;
                    animation: float-blob-1 22s infinite alternate ease-in-out;
                }
                .aurora-purple {
                    background: #722ed1;
                    bottom: -15%;
                    right: -5%;
                    animation: float-blob-2 26s infinite alternate ease-in-out;
                }
                .aurora-cyan {
                    background: #13c2c2;
                    top: 35%;
                    right: 25%;
                    animation: float-blob-3 18s infinite alternate ease-in-out;
                }
                
                @keyframes float-blob-1 {
                    0% { transform: translate(0, 0) scale(1); }
                    50% { transform: translate(120px, 80px) scale(1.15); }
                    100% { transform: translate(40px, 160px) scale(0.9); }
                }
                @keyframes float-blob-2 {
                    0% { transform: translate(0, 0) scale(1.1); }
                    50% { transform: translate(-140px, -100px) scale(0.85); }
                    100% { transform: translate(-80px, 40px) scale(1.2); }
                }
                @keyframes float-blob-3 {
                    0% { transform: translate(0, 0) scale(0.9); }
                    50% { transform: translate(80px, -60px) scale(1.1); }
                    100% { transform: translate(-40px, -120px) scale(1); }
                }
                
                /* Background image crossfade slider */
                .bg-slider-image {
                    position: absolute;
                    top: 0;
                    left: 0;
                    width: 100%;
                    height: 100%;
                    background-size: cover;
                    background-position: center;
                    transition: opacity 1.8s ease-in-out, transform 8s ease-out;
                    opacity: 0;
                    z-index: 1;
                }
                .bg-slider-image.active {
                    opacity: 1.0;
                    transform: scale(1.05);
                }

                /* Gear rotating animations */
                @keyframes rotate-cw {
                    0% { transform: rotate(0deg); }
                    100% { transform: rotate(360deg); }
                }
                @keyframes rotate-ccw {
                    0% { transform: rotate(0deg); }
                    100% { transform: rotate(-360deg); }
                }
                .spin-cw {
                    animation: rotate-cw 12s linear infinite;
                }
                .spin-ccw {
                    animation: rotate-ccw 9s linear infinite;
                }
                
                /* Loader animations */
                @keyframes scan-ring {
                    0% { transform: rotate(0deg) scale(1); }
                    50% { transform: rotate(180deg) scale(1.04); }
                    100% { transform: rotate(360deg) scale(1); }
                }
                @keyframes logo-pulse {
                    0% { transform: scale(1); box-shadow: 0 0 0 0 rgba(24, 144, 255, 0.4); }
                    50% { transform: scale(1.05); box-shadow: 0 0 20px 8px rgba(24, 144, 255, 0.2); }
                    100% { transform: scale(1); box-shadow: 0 0 0 0 rgba(24, 144, 255, 0.4); }
                }
                .loader-ring {
                    animation: scan-ring 2.5s cubic-bezier(0.4, 0, 0.2, 1) infinite;
                    border: 3px solid transparent;
                    border-top-color: #1890ff;
                    border-bottom-color: #13c2c2;
                }
                .loader-logo {
                    animation: logo-pulse 2s ease-in-out infinite;
                }
            `}</style>

            {/* 1. Loading Screen */}
            {loadingVisible && (
                <div className={`loader-container ${loadingFade ? 'fade-out' : ''}`}>
                    <div style={{ position: 'relative', display: 'flex', justifyContent: 'center', alignItems: 'center', width: 200, height: 200 }}>
                        {/* CSS drawn scanner ring */}
                        <div className="loader-ring" style={{ position: 'absolute', width: 170, height: 170, borderRadius: '50%' }} />
                        <div
                            aria-hidden="true"
                            style={{
                                position: 'absolute',
                                width: 120,
                                height: 120,
                                borderRadius: '50%',
                                background: 'conic-gradient(from 35deg, #1890ff, #13c2c2, #69c0ff, #1890ff)',
                                boxShadow: 'inset 0 0 0 10px #fff, 0 14px 34px rgba(24, 144, 255, 0.22)',
                            }}
                        />
                        {/* Logo image in public */}
                        <TeamLogo size={120} priority className="loader-logo" />
                    </div>
                    <div className="showcase-font-body" style={{ marginTop: 14, fontSize: 13, color: '#8c8c8c', letterSpacing: 2 }}>
                        Loading
                    </div>
                </div>
            )}

            {/* 2. Global Ferrofluid background and first-screen image carousel container */}
            <div className="aurora-container" style={{
                position: 'absolute',
                width: '100%',
                height: '100vh',
                top: 0,
                left: 0,
                overflow: 'hidden',
                background: 'transparent',
                zIndex: 1
            }}>
                <div className="noise-overlay" style={{ opacity: 0, transition: 'opacity 1s ease-in-out' }} />

                {/* Background layers only visible on the first screen (fades out as you scroll down) */}
                <div style={{
                    opacity: activeIndex === 0 ? 1 : 0,
                    transition: 'opacity 0.8s ease-in-out',
                    width: '100%',
                    height: '100%',
                    position: 'relative',
                    zIndex: 2
                }}>
                    {/* Welcome Slide Background (Slide 0) */}
                    <div 
                        className={`bg-slider-image ${currentBgIndex === 0 ? 'active' : ''}`}
                        style={{
                            background: 'transparent',
                            zIndex: 1
                        }}
                    >
                        {/* Grid overlay */}
                        <div style={{
                            position: 'absolute',
                            top: 0,
                            left: 0,
                            width: '100%',
                            height: '100%',
                            backgroundImage: 'linear-gradient(rgba(0,0,0,0.02) 1px, transparent 1px), linear-gradient(90deg, rgba(0,0,0,0.02) 1px, transparent 1px)',
                            backgroundSize: '50px 50px',
                            backgroundPosition: 'center center',
                            opacity: 0.6
                        }} />
                    </div>

                    {/* Image Slides */}
                    {carouselList.length > 0 ? (
                        carouselList.map((item, idx) => (
                            <div
                                key={idx}
                                className={`bg-slider-image ${idx === currentBgIndex - 1 ? 'active' : ''}`}
                                style={{ backgroundImage: `url(${item.imageUrl})` }}
                            />
                        ))
                    ) : (
                        defaultBgs.map((url, idx) => (
                            <div
                                key={idx}
                                className={`bg-slider-image ${idx === currentBgIndex - 1 ? 'active' : ''}`}
                                style={{ backgroundImage: `url(${url})` }}
                            />
                        ))
                    )}
                    
                    {/* Glassmorphism translucent backdrop overlay to maintain high-end look and readability */}
                    <div style={{
                        position: 'absolute',
                        top: 0,
                        left: 0,
                        width: '100%',
                        height: '100%',
	                        background: overlayBg,
                        zIndex: 2,
                        transition: 'background 0.5s ease-in-out'
                    }} />
                    
                    {/* Colorful Aurora Blobs floating around */}
                    <div className="aurora-blob aurora-blue" style={{ opacity: 0, transition: 'opacity 1.5s ease-in-out' }} />
                    <div className="aurora-blob aurora-purple" style={{ opacity: 0, transition: 'opacity 1.5s ease-in-out' }} />
                    <div className="aurora-blob aurora-cyan" style={{ opacity: 0, transition: 'opacity 1.5s ease-in-out' }} />
                </div>
            </div>

            <SiteHeader onBrandClick={() => scrollToSection(0)} />

            {/* 4. Free scrolling sections container */}
            <div 
                className="showcase-scroll-container"
                onScroll={handleScroll}
            >
                {/* Section 1: Hero Welcome Screen */}
                <section ref={setSectionRef(0)} data-showcase-section="0" className={`showcase-section showcase-hero-section ${visibleSections.has(0) ? 'section-active' : ''}`} style={{ zIndex: 10, textAlign: 'center', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <div style={{
                            background: 'transparent',
                            padding: '48px 40px',
                            maxWidth: 880,
                            width: 'calc(100% - 48px)',
                            margin: '0 auto',
                            display: 'flex',
                            flexDirection: 'column',
                            alignItems: 'center',
                            zIndex: 5,
                            opacity: activeIndex === 0 && currentBgIndex === 0 ? 1 : 0,
                            transform: activeIndex === 0 && currentBgIndex === 0 ? 'translateY(0)' : 'translateY(-20px)',
                            transition: 'opacity 0.8s cubic-bezier(0.16, 1, 0.3, 1), transform 0.8s cubic-bezier(0.16, 1, 0.3, 1)',
                            pointerEvents: activeIndex === 0 && currentBgIndex === 0 ? 'auto' : 'none'
                        }} className="reveal-item showcase-hero-content">
                        <Tag 
                            className="reveal-item showcase-hero-tag"
                            style={{ 
                                fontSize: 13, 
                                padding: '4px 16px', 
                                borderRadius: 20, 
                                marginBottom: 20, 
                                border: tagBorder, 
                                background: tagBg, 
                                color: tagTextColor, 
                                fontWeight: 600,
                                transition: 'all 0.5s ease'
                            }}
                        >
                            北京电子科技学院 · ACM 校队
                        </Tag>
                        
                        <h1 
                            className="reveal-item reveal-delay-1 showcase-font-title showcase-hero-title"
                            style={{ 
                                fontSize: 'calc(24px + 2.5vw)', 
                                fontWeight: 800, 
                                color: mainTitleColor, 
                                lineHeight: 1.2, 
                                margin: '0 0 16px 0', 
                                textShadow: mainTitleOutline,
                                transition: 'all 0.5s ease'
                            }}
                        >
                            Welcome to <br/>
                            <span 
                                style={{ 
                                    background: 'linear-gradient(90deg, #1890ff 0%, #13c2c2 100%)', 
                                    WebkitBackgroundClip: 'text', 
                                    WebkitTextFillColor: 'transparent',
                                    WebkitTextStroke: gradientTextStroke,
                                    transition: 'all 0.5s ease'
                                }}
                            >
                                BESTI ACM
                            </span>
                        </h1>
                        
                        <p 
                            className="reveal-item reveal-delay-2 showcase-font-title showcase-hero-subtitle"
                            style={{ 
                                fontSize: 'calc(14px + 0.6vw)', 
                                color: subtitleColor, 
                                fontWeight: 600, 
                                letterSpacing: 3, 
                                marginBottom: 36, 
                                textShadow: subtitleOutline,
                                transition: 'all 0.5s ease'
                            }}
                        >
                            欢迎来到北京电子科技学院 ACM 校队
                        </p>
                        
                        <div className="reveal-item reveal-delay-3 showcase-hero-action" style={{ display: 'flex', gap: 16, flexWrap: 'wrap', justifyContent: 'center' }}>
                            <Link href="/intro">
                                <Button 
                                    size="large" 
                                    shape="round" 
                                    style={infoBtnStyle}
                                >
                                    了解校队简介
                                </Button>
                            </Link>
                        </div>
                    </div>

                    {/* Rotating SVG gears indicator at bottom */}
                    <div className="reveal-item reveal-delay-4 showcase-scroll-cue" style={{
                            position: 'absolute',
                            bottom: '40px',
                            left: '50%',
                            display: 'flex',
                            flexDirection: 'column',
                            alignItems: 'center',
                            cursor: 'pointer',
                            zIndex: 30,
                            opacity: activeIndex === 0 && currentBgIndex === 0 ? 1 : 0,
                            transform: activeIndex === 0 && currentBgIndex === 0 ? 'translateX(-50%) translateY(0)' : 'translateX(-50%) translateY(20px)',
                            transition: 'opacity 0.8s cubic-bezier(0.16, 1, 0.3, 1), transform 0.8s cubic-bezier(0.16, 1, 0.3, 1)',
                            pointerEvents: activeIndex === 0 && currentBgIndex === 0 ? 'auto' : 'none'
                        }} onClick={() => scrollToSection(1)}>
                        <div 
                            style={{ 
                                display: 'flex', 
                                alignItems: 'center', 
                                height: 40, 
                                width: 80, 
                                justifyContent: 'center', 
                                position: 'relative',
                                filter: gearsShadow,
                                transition: 'filter 0.5s ease'
                            }}
                        >
                            <SettingOutlined className="spin-cw" style={{ fontSize: 32, color: '#1890ff', position: 'absolute', left: 16 }} />
                            <SettingOutlined className="spin-ccw" style={{ fontSize: 20, color: '#13c2c2', position: 'absolute', left: 42, top: 4 }} />
                        </div>
                        <span 
                            className="showcase-font-body" 
                            style={{ 
                                fontSize: 12, 
                                color: scrollTextColor, 
                                letterSpacing: 2, 
                                marginTop: 4, 
                                display: 'flex', 
                                alignItems: 'center', 
                                gap: 4,
                                textShadow: scrollTextShadow,
                                transition: 'all 0.5s ease'
                            }}
                        >
                            下滑探索 <ArrowDownOutlined style={{ fontSize: 10 }} />
                        </span>
                    </div>
                </section>

                {/* Section 2: Association Honors */}
                <section ref={setSectionRef(1)} data-showcase-section="1" className={`showcase-section ${visibleSections.has(1) ? 'section-active' : ''}`} style={{ zIndex: 10 }}>
                    <div style={{ maxWidth: 1200, margin: '0 auto', width: '100%' }}>
                        <div style={{ textAlign: 'center', marginBottom: 32 }} className="reveal-item">
                            <Title level={2} className="showcase-font-title" style={{ fontSize: '32px', fontWeight: 800, letterSpacing: 2 }}>荣誉墙</Title>
                            <div style={{ width: 48, height: 4, background: '#1890ff', margin: '12px auto 0', borderRadius: 2 }} />
                        </div>

                        {honorsList.length === 0 ? (
                            <div className="reveal-item reveal-delay-1">
                                <Empty description="暂无荣誉记录，可在管理后台发布" />
                            </div>
                        ) : (
                            <Row gutter={[24, 24]} className="reveal-item reveal-delay-1" justify="center">
                                {honorsList.filter(item => item.awardGrade || item.award || item.imageUrl).slice(0, 3).map((item, idx) => (
                                    <Col xs={24} md={8} key={idx}>
                                        <Card
                                            hoverable
                                            cover={
                                                <div style={{ height: 200, overflow: 'hidden', position: 'relative' }}>
                                                    {item.imageUrl ? <img
                                                        alt={item.title} 
                                                        src={item.imageUrl} 
                                                        style={{ width: '100%', height: '100%', objectFit: 'cover', transition: 'transform 0.5s' }} 
                                                        className="honor-img"
                                                    /> : <div style={{height:'100%',display:'flex',flexDirection:'column',alignItems:'center',justifyContent:'center',gap:14,background:'linear-gradient(135deg,#123c6a,#397797)',color:'white'}}><TrophyOutlined style={{fontSize:58}}/><span style={{fontSize:26,fontWeight:700}}>{item.contestAward || item.award || (item.awardLevel && item.awardGrade ? item.awardLevel + item.awardGrade : '')}</span>{(item.contestAward || item.award) && item.awardLevel && item.awardGrade && <span>学校认定：{item.awardLevel}{item.awardGrade}</span>}</div>}
                                                </div>
                                            }
                                            style={{ borderRadius: 12, overflow: 'hidden', border: 'none', background: 'rgba(255,255,255,0.7)', backdropFilter: 'blur(8px)', boxShadow: '0 4px 20px rgba(0,0,0,0.03)' }}
                                            bodyStyle={{ padding: 20 }}
                                        >
                                            <Card.Meta 
                                                title={<span style={{ fontSize: 16, fontWeight: 700, color: '#262626', whiteSpace: 'normal', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', minHeight: 48 }}>{item.title}</span>} 
                                                description={<span style={{ fontSize: 13, color: '#65758b' }}>{item.team || '校队历年竞赛成绩与获奖记录'}</span>}
                                            />
                                        </Card>
                                    </Col>
                                ))}
                            </Row>
                        )}
                        
                        <div style={{ textAlign: 'center', marginTop: 32 }} className="reveal-item reveal-delay-2">
                            <Link href="/honor">
                                <Button 
                                    type="dashed" 
                                    shape="round" 
                                    icon={<DoubleRightOutlined />} 
                                    style={{ padding: '0 24px', height: 40, background: 'rgba(255,255,255,0.5)', borderColor: '#d9d9d9' }}
                                >
                                    查看全部荣誉
                                </Button>
                            </Link>
                        </div>
                    </div>
                </section>

                {/* Section 3: Association News */}
                <section ref={setSectionRef(2)} data-showcase-section="2" className={`showcase-section ${visibleSections.has(2) ? 'section-active' : ''}`} style={{ zIndex: 10 }}>
                    <div style={{ maxWidth: 1200, margin: '0 auto', width: '100%' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: 30, borderBottom: '1px solid rgba(0,0,0,0.06)', paddingBottom: 14 }} className="reveal-item showcase-section-heading-row">
                            <div>
                                <Title level={2} className="showcase-font-title" style={{ fontSize: '32px', fontWeight: 800, margin: 0 }}>校队要闻</Title>
                                <p style={{ color: '#8c8c8c', margin: '8px 0 0 0', fontSize: 14 }}>实时获取通知、新闻、公示三类主要内容</p>
                            </div>
                            <Link href="/notice">
                                <Button type="link" icon={<ArrowRightOutlined />} style={{ color: '#1890ff', fontSize: 15, fontWeight: 600 }}>
                                    全部要闻
                                </Button>
                            </Link>
                        </div>

                        <Row gutter={[20, 20]} className="reveal-item reveal-delay-1">
                            {noticeGroups.map((group) => (
                                <Col xs={24} sm={24} md={8} key={group.type}>
                                    <Card
                                        style={{ height: '100%', borderRadius: 12, border: 'none', background: 'rgba(255,255,255,0.72)', backdropFilter: 'blur(8px)', boxShadow: '0 4px 20px rgba(0,0,0,0.025)' }}
                                        bodyStyle={{ padding: '22px 22px 16px' }}
                                    >
                                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                                            <div style={{ display: 'flex', alignItems: 'center', gap: 10, fontSize: 18, fontWeight: 800, color: '#262626' }}>
                                                <span style={{ color: group.color }}>{group.icon}</span>
                                                {group.title}
                                            </div>
                                            <Link href={getNoticeListRoute(group.type)}>
                                                <Button type="link" size="small" style={{ paddingInline: 0, color: group.color }}>
                                                    更多
                                                </Button>
                                            </Link>
                                        </div>
                                        {group.list.length > 0
                                            ? group.list.slice(0, 5).map((item) => renderNoticeCard(item, group.color, group.type))
                                            : renderEmpty(`暂无${group.title}`)}
                                    </Card>
                                </Col>
                            ))}
                        </Row>
                    </div>
                </section>

                {/* Section 4: Trainings and Contests */}
                <section ref={setSectionRef(3)} data-showcase-section="3" className={`showcase-section ${visibleSections.has(3) ? 'section-active' : ''}`} style={{ zIndex: 10 }}>
                    <div style={{ maxWidth: 1200, margin: '0 auto', width: '100%' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: 30, borderBottom: '1px solid rgba(0,0,0,0.06)', paddingBottom: 14 }} className="reveal-item showcase-section-heading-row">
                            <div>
                                <Title level={2} className="showcase-font-title" style={{ fontSize: '32px', fontWeight: 800, margin: 0 }}>近期安排</Title>
                                <p style={{ color: '#8c8c8c', margin: '8px 0 0 0', fontSize: 14 }}>校队培训、统一组织赛事与个人自愿登记</p>
                            </div>
                            <Link href="/training">
                                <Button type="link" icon={<ArrowRightOutlined />} style={{ color: '#1890ff', fontSize: 15, fontWeight: 600 }}>
                                    查看培训
                                </Button>
                            </Link>
                        </div>

                        <div className="showcase-schedule-grid reveal-item reveal-delay-1">
                            <div className="showcase-schedule-column">
                                <Card style={{ height: '100%', borderRadius: 12, border: 'none', background: 'rgba(255,255,255,0.72)', backdropFilter: 'blur(8px)', boxShadow: '0 4px 20px rgba(0,0,0,0.025)' }} bodyStyle={{ padding: '22px 22px 16px' }}>
                                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                                        <div className="showcase-schedule-card-title" style={{ display: 'flex', alignItems: 'center', gap: 10, fontSize: 18, fontWeight: 800, color: '#262626' }}>
                                            <BookOutlined style={{ color: '#722ed1' }} /> 近期培训
                                        </div>
                                        <Link href="/training"><Button type="link" size="small" style={{ paddingInline: 0 }}>全部</Button></Link>
                                    </div>
                                    {trainingList.length > 0 ? trainingList.slice(0, 4).map(renderTrainingCard) : renderEmpty('暂无培训')}
                                </Card>
                            </div>
                            <div className="showcase-schedule-column">
                                <Card style={{ height: '100%', borderRadius: 12, border: 'none', background: 'rgba(255,255,255,0.72)', backdropFilter: 'blur(8px)', boxShadow: '0 4px 20px rgba(0,0,0,0.025)' }} bodyStyle={{ padding: '22px 22px 16px' }}>
                                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                                        <div className="showcase-schedule-card-title" style={{ display: 'flex', alignItems: 'center', gap: 10, fontSize: 18, fontWeight: 800, color: '#262626' }}>
                                            <TrophyOutlined style={{ color: '#faad14' }} /> 校队组织赛事
                                        </div>
                                        <Link href="/contest/signup"><Button type="link" size="small" style={{ paddingInline: 0 }}>全部</Button></Link>
                                    </div>
                                    {localEvents.length > 0 ? localEvents.slice(0, 4).map(renderLocalEventCard) : renderEmpty('暂无线下赛')}
                                </Card>
                            </div>
                            <div className="showcase-schedule-column">
                                <Card style={{ height: '100%', borderRadius: 12, border: 'none', background: 'rgba(255,255,255,0.72)', backdropFilter: 'blur(8px)', boxShadow: '0 4px 20px rgba(0,0,0,0.025)' }} bodyStyle={{ padding: '22px 22px 16px' }}>
                                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                                        <div className="showcase-schedule-card-title" style={{ display: 'flex', alignItems: 'center', gap: 10, fontSize: 18, fontWeight: 800, color: '#262626' }}>
                                            <GlobalOutlined style={{ color: '#13c2c2' }} /> 个人自愿登记
                                        </div>
                                        <Link href="/team-space"><Button type="link" size="small" style={{ paddingInline: 0 }}>全部</Button></Link>
                                    </div>
                                    <p style={{lineHeight:1.9}}>其他比赛由成员自愿登记。</p><Link href="/competitions">查看赛事范围与登记方式 →</Link>
                                </Card>
                            </div>
                        </div>
                    </div>
                </section>

                {/* Section 5: Association Members */}
                <section ref={setSectionRef(4)} data-showcase-section="4" className={`showcase-section ${visibleSections.has(4) ? 'section-active' : ''}`} style={{ zIndex: 10 }}>
                    <div style={{ maxWidth: 1200, margin: '0 auto', width: '100%' }}>
                        <div style={{ textAlign: 'center', marginBottom: 32 }} className="reveal-item">
                            <Title level={2} className="showcase-font-title" style={{ fontSize: '32px', fontWeight: 800, letterSpacing: 2 }}>校队成员</Title>
                            <p style={{ color: '#8c8c8c', margin: '8px 0 0 0', fontSize: 14 }}>一起训练、比赛，记录每一段校队经历</p>
                            <div style={{ width: 48, height: 4, background: '#13c2c2', margin: '12px auto 0', borderRadius: 2 }} />
                        </div>

                        {cadreList.length === 0 ? (
                            <div className="reveal-item reveal-delay-1">
                                <Empty description="暂无成员职务数据" />
                            </div>
                        ) : (
                            <>
                                {/* Department Switch Tabs */}
                                <div style={{ display: 'flex', justifyContent: 'center', gap: 10, marginBottom: 22, flexWrap: 'wrap' }} className="reveal-item reveal-delay-1">
                                    {Array.from(new Set(cadreList.map(m => memberRoleKey(m.department, m.position))))
                                        .sort((a, b) => {
                                            const defaultDeptOrder = memberRoles.map(role => role.value);
                                            const indexA = defaultDeptOrder.indexOf(a);
                                            const indexB = defaultDeptOrder.indexOf(b);
                                            if (indexA !== -1 && indexB !== -1) return indexA - indexB;
                                            if (indexA !== -1) return -1;
                                            if (indexB !== -1) return 1;
                                            return a.localeCompare(b);
                                        })
                                        .map(dept => (
                                            <Button 
                                                key={dept}
                                                type={selectedDept === dept ? "primary" : "default"}
                                                shape="round"
                                                onClick={() => setSelectedDept(dept)}
                                                style={selectedDept === dept ? {
                                                    background: 'linear-gradient(135deg, #13c2c2 0%, #1890ff 100%)',
                                                    border: 'none',
                                                    boxShadow: '0 4px 10px rgba(24, 144, 255, 0.2)',
                                                    color: '#fff',
                                                    fontWeight: 600
                                                } : {
                                                    background: 'rgba(255, 255, 255, 0.6)',
                                                    backdropFilter: 'blur(5px)',
                                                    border: '1px solid #d9d9d9',
                                                    color: '#595959'
                                                }}
                                            >
                                                {memberRoles.find(role => role.value === dept)?.label || dept}
                                            </Button>
                                        ))
                                    }
                                </div>

                                {/* Active Department Members Grid */}
                                <Row gutter={[20, 20]} className="reveal-item reveal-delay-2 showcase-cadres-scroll" justify="center" style={{ maxHeight: '55vh', overflowY: 'auto', padding: '10px 4px' }}>
                                    {cadreList
                                        .filter(member => memberRoleKey(member.department, member.position) === selectedDept)
                                        .map((member) => {
                                            const tagColor = member.positionStatus === 'former' ? 'default' : 'blue';

                                            return (
                                                <Col xs={24} sm={12} md={6} key={member.memberId}>
                                                    <Card
                                                        hoverable
                                                        bodyStyle={{ padding: '24px 20px', textAlign: 'center' }}
                                                        style={{ borderRadius: 12, border: 'none', background: 'rgba(255,255,255,0.7)', backdropFilter: 'blur(8px)', boxShadow: '0 4px 24px rgba(0,0,0,0.02)' }}
                                                    >
                                                        <Avatar 
                                                            size={80} 
                                                            src={member.avatarUrl || '/avatar-default.svg'} 
                                                            style={{ border: '3px solid #fff', boxShadow: '0 4px 10px rgba(0,0,0,0.06)', marginBottom: 16 }}
                                                        />
                                                        <div style={{ fontSize: 18, fontWeight: 700, color: '#262626', marginBottom: 6 }}>
                                                            {member.userId ? (
                                                                <a 
                                                                    href={`/profile/${member.userId}`} 
                                                                    style={{ color: '#262626', textDecoration: 'none' }}
                                                                    onClick={(e) => { e.stopPropagation(); navigate(`/profile/${member.userId}`); }}
                                                                >
                                                                    {member.realName}
                                                                </a>
                                                            ) : (
                                                                member.realName
                                                            )}
                                                        </div>
                                                        <div style={{ marginBottom: 12 }}>
                                                            <Tag color={tagColor} style={{ padding: '2px 10px', borderRadius: 4, fontSize: 11, fontWeight: 600 }}>
                                                                {formatCadrePosition(member)}
                                                            </Tag>
                                                        </div>
                                                        <p style={{ 
                                                            color: '#8c8c8c', 
                                                            fontSize: 13, 
                                                            fontStyle: member.introduction ? 'italic' : 'normal',
                                                            margin: 0,
                                                            height: 38,
                                                            overflow: 'hidden',
                                                            display: '-webkit-box',
                                                            WebkitLineClamp: 2,
                                                            WebkitBoxOrient: 'vertical',
                                                            lineHeight: 1.4
                                                        }}>
                                                            {member.introduction || '“这个成员很懒，什么都没留下”'}
                                                        </p>
                                                    </Card>
                                                </Col>
                                            );
                                        })
                                    }
                                </Row>
                            </>
                        )}
                        
                        <div style={{ textAlign: 'center', marginTop: 32 }} className="reveal-item reveal-delay-2">
                            <Link href="/member">
                                <Button 
                                    type="dashed" 
                                    shape="round" 
                                    icon={<UserOutlined />} 
                                    style={{ padding: '0 24px', height: 40, background: 'rgba(255,255,255,0.5)', borderColor: '#d9d9d9' }}
                                >
                                    了解历届校队成员
                                </Button>
                            </Link>
                        </div>
                    </div>
                </section>

            </div>

            {/* Global Loader CSS injection */}
            <style>{`
                .loader-container {
                    position: fixed;
                    top: 0;
                    left: 0;
                    width: 100%;
                    height: 100%;
                    background: #ffffff;
                    display: flex;
                    flex-direction: column;
                    justify-content: center;
                    align-items: center;
                    z-index: 9999;
                    transition: opacity 0.8s cubic-bezier(0.16, 1, 0.3, 1);
                }
                .loader-container.fade-out {
                    opacity: 0;
                    pointer-events: none;
                }
                 .honor-img:hover {
                    transform: scale(1.08);
                }
                .noise-overlay {
                    position: absolute;
                    top: 0;
                    left: 0;
                    width: 100%;
                    height: 100%;
                    pointer-events: none;
                    z-index: 3;
                    background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noiseFilter'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.65' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noiseFilter)'/%3E%3C/svg%3E");
                }
            `}</style>
        </div>
    );
};

export default ShowcaseHome;
