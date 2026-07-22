import { ReactNode } from "react";
import AuthLayout from "@/shared/layouts/AuthLayout";

interface LayoutProps {
  children: ReactNode;
}

export default function Layout({ children }: LayoutProps) {
  return <AuthLayout>{children}</AuthLayout>;
}
