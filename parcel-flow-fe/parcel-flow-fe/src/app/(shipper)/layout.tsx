"use client";

import { ReactNode, useEffect } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAppDispatch, useAppSelector } from "@/store/store";
import { logoutSuccess } from "@/store/slices/authSlice";
import { setThemeMode } from "@/store/slices/themeSlice";
import LoadingState from "@/shared/components/LoadingState";
import styles from "./layout.module.scss";

interface LayoutProps {
  children: ReactNode;
}

export default function ShipperLayout({ children }: LayoutProps) {
  const router = useRouter();
  const dispatch = useAppDispatch();
  const user = useAppSelector((state) => state.auth.user);
  const isAuthenticated = useAppSelector((state) => state.auth.isAuthenticated);
  const isLoading = useAppSelector((state) => state.auth.isLoading);
  const themeMode = useAppSelector((state) => state.theme.mode);

  useEffect(() => {
    if (!isLoading) {
      if (!isAuthenticated) {
        router.push("/login");
      } else if (user?.role !== "SHIPPER") {
        router.push("/dashboard");
      }
    }
  }, [isLoading, isAuthenticated, user, router]);

  const handleLogout = () => {
    dispatch(logoutSuccess());
    router.push("/login");
  };

  const toggleTheme = () => {
    dispatch(setThemeMode(themeMode === "light" ? "dark" : "light"));
  };

  if (isLoading || !isAuthenticated || user?.role !== "SHIPPER") {
    return <LoadingState message="Loading courier portal..." fullPage />;
  }

  return (
    <div className={styles.wrapper}>
      <div className={styles.meshGlow} />
      
      <header className={styles.header}>
        <div className={styles.container}>
          <Link href="/shipper/assignments" className={styles.logo}>
            <span>Shipper</span>Flow
          </Link>
          <div className={styles.actions}>
            <button onClick={toggleTheme} className={styles.themeToggle} aria-label="Toggle theme">
              {themeMode === "light" ? "🌙" : "☀️"}
            </button>
            <button onClick={handleLogout} className={styles.logoutBtn}>
              Exit
            </button>
          </div>
        </div>
      </header>

      <div className={styles.subheader}>
        <div className={styles.container}>
          <span className={styles.driverName}>Active Courier: {user?.fullName || "Shipper"}</span>
        </div>
      </div>

      <main className={styles.main}>
        {children}
      </main>
    </div>
  );
}
