"use client";

import { ReactNode, useState, useEffect } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useAppDispatch, useAppSelector } from "@/store/store";
import { setThemeMode } from "@/store/slices/themeSlice";
import { DASHBOARD_ROUTES } from "@/shared/routes/dashboard-routes";
import LoadingState from "@/shared/components/LoadingState";
import useAuth from "@/features/auth/hooks/useAuth";
import styles from "./DashboardLayout.module.scss";

interface DashboardLayoutProps {
  children: ReactNode;
}

export default function DashboardLayout({ children }: DashboardLayoutProps) {
  const pathname = usePathname();
  const router = useRouter();
  const dispatch = useAppDispatch();
  const user = useAppSelector((state) => state.auth.user);
  const isAuthenticated = useAppSelector((state) => state.auth.isAuthenticated);
  const isLoading = useAppSelector((state) => state.auth.isLoading);
  const themeMode = useAppSelector((state) => state.theme.mode);
  const [isSidebarOpen, setIsSidebarOpen] = useState(false);
  const { logout } = useAuth();

  useEffect(() => {
    if (!isLoading) {
      if (!isAuthenticated) {
        router.push("/login");
      } else if (user?.role === "SHIPPER") {
        router.push("/shipper/assignments");
      }
    }
  }, [isLoading, isAuthenticated, user, router]);

  /**
   * Delegates to useAuth().logout, which revokes the session server-side,
   * clears the stored JWTs, resets Redux and the query cache, then redirects.
   *
   * This used to dispatch logoutSuccess() and push /login by itself. That only
   * wiped the Redux slice: the access and refresh tokens stayed in
   * localStorage and the server session stayed alive for its full refresh TTL,
   * so "logging out" on a shared machine left a working credential behind and
   * the guard would bounce the next visitor straight back into the session.
   */
  const handleLogout = () => {
    void logout();
  };

  const toggleTheme = () => {
    dispatch(setThemeMode(themeMode === "light" ? "dark" : "light"));
  };

  const toggleSidebar = () => {
    setIsSidebarOpen(!isSidebarOpen);
  };

  // HUB_MANAGER is a seeded, first-class role, but it appeared in none of these
  // lists — a manager logged in successfully and got an empty sidebar over an
  // empty dashboard. Its entries follow ROLE_PERMISSIONS in config/permissions.ts:
  // the hub-operations surfaces (orders, parcels, hubs) but not route planning,
  // deliveries or user administration.
  const menuItems = [
    { name: "Overview", path: DASHBOARD_ROUTES.root, roles: ["ADMIN", "HUB_MANAGER", "HUB_STAFF", "DISPATCHER"] },
    { name: "Orders", path: DASHBOARD_ROUTES.orders, roles: ["ADMIN", "HUB_MANAGER", "HUB_STAFF", "DISPATCHER"] },
    { name: "Parcels", path: DASHBOARD_ROUTES.parcels, roles: ["ADMIN", "HUB_MANAGER", "HUB_STAFF", "DISPATCHER"] },
    { name: "Hubs", path: DASHBOARD_ROUTES.hubs, roles: ["ADMIN", "HUB_MANAGER"] },
    { name: "Route Planning", path: DASHBOARD_ROUTES.routes, roles: ["ADMIN", "DISPATCHER"] },
    { name: "Deliveries", path: DASHBOARD_ROUTES.delivery, roles: ["ADMIN", "DISPATCHER"] },
    { name: "Users", path: DASHBOARD_ROUTES.users, roles: ["ADMIN"] },
  ];

  const filteredMenuItems = menuItems.filter(
    (item) => user && item.roles.includes(user.role)
  );

  if (isLoading || !isAuthenticated || user?.role === "SHIPPER") {
    return <LoadingState message="Loading administrative portal..." fullPage />;
  }

  return (
    <div className={styles.wrapper}>
      {/* Mobile Sidebar Overlay */}
      {isSidebarOpen && (
        <div className={styles.overlay} onClick={() => setIsSidebarOpen(false)} />
      )}

      {/* Sidebar */}
      <aside className={`${styles.sidebar} ${isSidebarOpen ? styles.sidebarOpen : ""}`}>
        <div className={styles.logoWrapper}>
          <Link href="/dashboard" className={styles.logo} onClick={() => setIsSidebarOpen(false)}>
            <span>Parcel</span>Flow
          </Link>
        </div>
        
        <nav className={styles.nav}>
          {filteredMenuItems.map((item) => {
            const isActive = pathname === item.path;
            return (
              <Link 
                key={item.path} 
                href={item.path}
                className={`${styles.navItem} ${isActive ? styles.active : ""}`}
                onClick={() => setIsSidebarOpen(false)}
              >
                {item.name}
              </Link>
            );
          })}
        </nav>

        <div className={styles.sidebarFooter}>
          <button onClick={handleLogout} className={styles.logoutBtn}>
            Log Out
          </button>
        </div>
      </aside>

      {/* Main Content Area */}
      <div className={styles.contentWrapper}>
        <header className={styles.header}>
          <div className={styles.headerLeft}>
            <button 
              className={styles.sidebarToggle} 
              onClick={toggleSidebar}
              aria-label="Toggle Sidebar Menu"
            >
              <span></span>
              <span></span>
              <span></span>
            </button>
            <span className={styles.greeting}>
              Hello, {user?.fullName || "Staff"}
            </span>
            <span className={styles.roleBadge}>
              {user?.role || "Operator"}
            </span>
          </div>
          <div className={styles.headerRight}>
            <button onClick={toggleTheme} className={styles.themeToggle} aria-label="Toggle theme">
              {themeMode === "light" ? "🌙" : "☀️"}
            </button>
          </div>
        </header>

        <main className={styles.main}>
          {children}
        </main>
      </div>
    </div>
  );
}
