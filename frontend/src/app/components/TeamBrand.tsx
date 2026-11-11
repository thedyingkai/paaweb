import Image from 'next/image';
import { TEAM_LOGO_ALT, TEAM_LOGO_SRC } from '@/lib/besti-brand';

type LogoProps = { size?: number; priority?: boolean; className?: string };

export function TeamLogo({ size = 48, priority = false, className = '' }: LogoProps) {
  return (
    <Image
      src={TEAM_LOGO_SRC}
      alt={TEAM_LOGO_ALT}
      width={size}
      height={size}
      sizes={`${size}px`}
      priority={priority}
      className={`team-logo ${className}`}
      style={{ width: size, height: size, objectFit: 'contain', borderRadius: '50%', flexShrink: 0 }}
    />
  );
}

export default function TeamBrand({
  size = 48,
  priority = false,
  caption = '北京电子科技学院 ACM 校队',
}: LogoProps & { caption?: string }) {
  return (
    <span className="team-brand">
      <TeamLogo size={size} priority={priority} />
      <span className="team-brand-copy">
        <strong>BESTI ACM</strong>
        <small>{caption}</small>
      </span>
    </span>
  );
}
