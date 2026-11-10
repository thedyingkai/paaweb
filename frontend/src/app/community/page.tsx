import type { Metadata } from "next";
import CommunityClient from "./CommunityClient";
export const metadata: Metadata = {
  title: "校队交流区 · BESTI ACM",
  robots: { index: false, follow: false },
};
export default function Page() {
  return <CommunityClient />;
}
