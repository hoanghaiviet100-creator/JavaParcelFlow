import { ReactNode } from "react";
import DashboardLayout from "@/shared/layouts/DashboardLayout";

interface LayoutProps {
  children: ReactNode;
}

export default function Layout({ children }: LayoutProps) {
  return <DashboardLayout>{children}</DashboardLayout>;
}
