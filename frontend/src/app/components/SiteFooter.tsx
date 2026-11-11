import Link from 'next/link';
import TeamBrand from './TeamBrand';

const links = [
  { label: '洛谷', href: 'https://www.luogu.com.cn/' },
  { label: '牛客', href: 'https://ac.nowcoder.com/' },
  { label: 'AtCoder', href: 'https://atcoder.jp/' },
  { label: 'Codeforces', href: 'https://codeforces.com/' },
  { label: 'VJudge', href: 'https://vjudge.net/' },
];

export default function SiteFooter() {
  let vpLink: { label: string; href: string }[] = [];
  try {
    const url = new URL(process.env.PUBLIC_VP_SITE_URL || '');
    if (url.protocol === 'https:' || url.protocol === 'http:') {
      vpLink = [{ label: 'VP系统', href: url.href }];
    }
  } catch {
    // An unset optional integration does not add a footer link.
  }
  return (
    <footer className="site-footer">
      <div className="site-footer-inner">
        <div className="site-footer-main">
          <div className="site-footer-left">
            <div className="site-footer-intro">
              <Link href="/" className="site-footer-brand" aria-label="北京电子科技学院 ACM 校队首页">
                <TeamBrand size={64} />
              </Link>
              <span>编程、算法竞赛与工程实践的学习平台</span>
            </div>
            <nav className="site-footer-links" aria-label="学习平台">
              <div className="site-footer-link-group">
                <h2 className="site-footer-link-title">学习平台</h2>
                <div className="site-footer-link-items">
                  {[...links, ...vpLink].map(({ label, href }) => (
                    <a key={href} href={href} target="_blank" rel="noreferrer">{label}</a>
                  ))}
                </div>
              </div>
            </nav>
          </div>
        </div>
        <div className="site-footer-meta"><span>北京电子科技学院 ACM 校队</span></div>
      </div>
    </footer>
  );
}
