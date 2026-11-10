import type { Metadata } from "next";
import Link from "next/link";
import SiteHeader from "../components/SiteHeader";
export const metadata: Metadata = { title: "赛事与登记 · BESTI ACM" };
export default function Page() {
  return (
    <>
      <SiteHeader />
      <main className="content-page-shell">
        <section className="content-hero content-hero-centered">
          <span className="bits-pill">BESTI ACM · COMPETITIONS</span>
          <h1 className="content-hero-title">赛事与登记</h1>
          <p>围绕校队训练与参赛，记录每个人的成长。</p>
        </section>
        <div
          style={{ maxWidth: 1000, margin: "0 auto", display: "grid", gap: 24 }}
        >
          <section className="bits-card" style={{ padding: 32 }}>
            <h2>校队统一组织</h2>
            <p>
              计算机工程师联盟主办的比赛，以及 ACM 校队带队参加的
              ICPC、CCPC、团体程序设计天梯赛。
            </p>
            <p>
              队长负责赛事安排、组队和统一登记。成员可在个人档案中查看自己的记录，补充成绩与证明材料。
            </p>
            <Link href="/team-space">进入校队档案与参赛登记 →</Link>
          </section>
          <section className="bits-card" style={{ padding: 32 }}>
            <h2>个人自愿登记</h2>
            <p>蓝桥杯、马蹄杯、百度之星、CCSP、CNCC。</p>
            <p>
              由成员本人自愿申请、维护参赛记录与奖项，不由队长统一登记。CNCC
              可登记参会经历；没有奖项的参会记录不计入获奖统计。
            </p>
            <p>本站登记用于校队档案管理；实际报名请按各赛事主办方要求办理。</p>
            <Link href="/team-space">登记我的参赛经历 →</Link>
          </section>
          <section className="bits-card" style={{ padding: 32 }}>
            <h2>成绩与交流</h2>
            <p>
              <Link href="/honor">查看历年成绩档案</Link> ·{" "}
              <Link href="/community">进入校队内部交流区</Link>
            </p>
          </section>
        </div>
      </main>
    </>
  );
}
