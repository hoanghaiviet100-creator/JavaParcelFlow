import { ReactNode } from "react";
import PublicLayout from "@/shared/layouts/PublicLayout";

interface LayoutProps {
  children: ReactNode;
}

export default function Layout({ children }: LayoutProps) {
  return <PublicLayout>{children}</PublicLayout>;
}
