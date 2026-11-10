"use client";

/* eslint-disable react-hooks/set-state-in-effect, @next/next/no-img-element */

import { useEffect, useState } from 'react';

interface PageLoaderProps {
  loading: boolean;
  initialOnly?: boolean;
}

const FADE_DURATION = 260;

// Declare the global property
declare global {
  interface Window {
    __paa_first_load_done__?: boolean;
  }
}

const PageLoader = ({ loading, initialOnly = true }: PageLoaderProps) => {
  // Page-level data requests do not use the full-screen logo. The only caller
  // that opts out is GlobalRouteLoader, which owns the single welcome animation.
  const [visible, setVisible] = useState(() => !initialOnly && loading);
  const [fade, setFade] = useState(false);

  useEffect(() => {
    if (typeof window === 'undefined') return;

    if (initialOnly) {
      setVisible(false);
      return;
    }

    if (loading) {
      setVisible(true);
      setFade(false);
    } else {
      setFade(true);
      const timer = setTimeout(() => {
        setVisible(false);
      }, FADE_DURATION);
      return () => clearTimeout(timer);
    }
  }, [initialOnly, loading]);

  if (!visible) return null;

  return (
    <div className={`loader-container ${fade ? 'fade-out' : ''}`}>
      <div style={{ position: 'relative', display: 'flex', justifyContent: 'center', alignItems: 'center', width: 200, height: 200 }}>
        {/* Scanner ring */}
        <div className="loader-ring" style={{ position: 'absolute', width: 170, height: 170, borderRadius: '50%' }} />
        <div
          className="loader-logo-fallback"
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
        {/* Logo */}
        <img
          src="/besti-acm-mark.svg"
          alt="BESTI ACM"
          className="loader-logo"
          style={{ position: 'relative', width: 120, height: 120, borderRadius: 0, objectFit: 'contain', background: '#fff', padding: 4 }}
          onError={(e) => {
            e.currentTarget.style.display = 'none';
          }}
        />
      </div>
      <div className="showcase-font-body" style={{ marginTop: 14, fontSize: 13, color: '#8c8c8c', letterSpacing: 2 }}>
        Loading
      </div>
    </div>
  );
};

export default PageLoader;
