import type { Metadata } from "next";
import AdminLayoutClient from "./AdminLayoutClient";
import { TEAM_ICONS } from '@/lib/besti-brand';

export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: "控制面板",
  description: " ",
  keywords: [],
  openGraph: null as never,
  twitter: null as never,
  icons: TEAM_ICONS,
  robots: {
    index: false,
    follow: false,
    nocache: true,
    googleBot: {
      index: false,
      follow: false,
      noimageindex: true,
      "max-snippet": -1,
      "max-image-preview": "none",
      "max-video-preview": -1,
    },
  },
};

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return <AdminLayoutClient>{children}</AdminLayoutClient>;
}
