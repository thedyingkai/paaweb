import type { Metadata } from "next";
import TeamSpaceClient from "./TeamSpaceClient";
export const metadata: Metadata = {
  title: "我的校队档案 · BESTI ACM",
  robots: { index: false, follow: false },
};
export default function Page() {
  return <TeamSpaceClient />;
}
