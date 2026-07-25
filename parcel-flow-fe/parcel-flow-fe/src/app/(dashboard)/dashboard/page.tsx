"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { useAppSelector } from "@/store/store";
import { getStatsOverviewApi } from "@/features/stats/api/stats.api";
import styles from "./dashboard.module.scss";

export default function DashboardPage() {
  const user = useAppSelector((state) => state.auth.user);
  const role = user?.role || "ADMIN";

  // Live counts for the KPI cards. These used to be hard-coded ("142 registered
  // today"); every figure below now comes from GET /api/v1/stats/overview.
  const { data, isLoading } = useQuery({
    queryKey: ["stats-overview"],
    queryFn: getStatsOverviewApi,
    retry: false,
  });
  const stats = data?.data;

  // While loading (or if the call fails) show a dash rather than a wrong number.
  const fmt = (n: number | undefined) =>
    isLoading ? "…" : n === undefined ? "—" : n.toLocaleString();

  // ==========================================
  // VIEW RENDERERS BY ROLE
  // ==========================================

  const renderAdminDashboard = () => {
    const kpiData = [
      { title: "Logistics Users", val: fmt(stats?.totalUsers), icon: "👤", note: "Accounts across all roles" },
      { title: "Operational Hubs", val: fmt(stats?.activeHubs), icon: "🏢", note: "Active hubs in the network" },
      { title: "Total Orders", val: fmt(stats?.totalOrders), icon: "📦", note: `${fmt(stats?.ordersToday)} created today` },
      { title: "Pending Deliveries", val: fmt(stats?.pendingDeliveries), icon: "⚡", note: "Parcels out for last-mile" },
    ];

    const quickActions = [
      { title: "User Management", desc: "Audit account permissions, courier profiles, and dispatcher rights.", link: "/users", label: "Manage Users" },
      { title: "Hub Inventory", desc: "Control sorting bins, warehouse capacity, and node registry.", link: "/hubs", label: "View Hubs" },
      { title: "Operations Console", desc: "Monitor all active parcel routes, logistics orders, and courier runs.", link: "/orders", label: "View Orders" },
    ];

    return (
      <>
        <div className={styles.header}>
          <h1 className={styles.title}>System Admin Console</h1>
          <p className={styles.subtitle}>
            Global operations overview, user controls, and hub networks metrics.
          </p>
        </div>

        <div className={styles.kpiGrid}>
          {kpiData.map((kpi, index) => (
            <div key={index} className={styles.kpiCard}>
              <div className={styles.cardInner}>
                <div className={styles.cardHeader}>
                  <span className={styles.cardTitle}>{kpi.title}</span>
                  <span className={styles.iconWrapper}>{kpi.icon}</span>
                </div>
                <div className={styles.value}>{kpi.val}</div>
                <div className={styles.footer}>
                  <span>{kpi.note}</span>
                </div>
              </div>
            </div>
          ))}
        </div>

        <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
          <h2 className={styles.sectionTitle}>System Governance Panels</h2>
          <div className={styles.bentoLayout}>
            {quickActions.map((action, index) => (
              <div key={index} className={styles.bentoCard}>
                <div className={styles.cardInner}>
                  <div className={styles.cardHeader} style={{ gap: "0.5rem" }}>
                    <span className={styles.cardTitle} style={{ fontSize: "1rem", color: "var(--color-text-primary)" }}>
                      {action.title}
                    </span>
                    <span className={styles.cardDesc} style={{ minHeight: "60px", color: "var(--color-text-secondary)", fontSize: "0.875rem" }}>
                      {action.desc}
                    </span>
                  </div>
                  <Link href={action.link} className={styles.btnAction}>
                    {action.label}
                    <span>→</span>
                  </Link>
                </div>
              </div>
            ))}
          </div>
        </div>
      </>
    );
  };

  const renderHubStaffDashboard = () => {
    const kpiData = [
      { title: "Orders Today", val: fmt(stats?.ordersToday), icon: "📝", note: "Created across the network today" },
      { title: "Pending Inbound", val: fmt(stats?.parcelsInboundPending), icon: "📥", note: "Parcels awaiting an intake scan" },
      { title: "In Transit", val: fmt(stats?.parcelsInTransit), icon: "📤", note: "Parcels moving between hubs" },
    ];

    const quickActions = [
      { title: "New Order Intake", desc: "Create a new logistical parcel shipment and output custom barcodes.", link: "/orders/create", label: "Create Order" },
      { title: "Inbound/Outbound Scanner", desc: "Open dynamic barcode scanner module to register package arrivals or handovers.", link: "/parcels/scan", label: "Open Scanner" },
      { title: "Hub Custody Logs", desc: "Verify parcel timeline history and physical custody logs of your local hub.", link: "/parcels", label: "View Custody Queue" },
    ];

    return (
      <>
        <div className={styles.header}>
          <h1 className={styles.title}>Hub Scanning & Intake Desk</h1>
          <p className={styles.subtitle}>
            Register intake and hand-offs, and follow parcels through your hub.
          </p>
        </div>

        <div className={styles.kpiGrid}>
          {kpiData.map((kpi, index) => (
            <div key={index} className={styles.kpiCard}>
              <div className={styles.cardInner}>
                <div className={styles.cardHeader}>
                  <span className={styles.cardTitle}>{kpi.title}</span>
                  <span className={styles.iconWrapper}>{kpi.icon}</span>
                </div>
                <div className={styles.value}>{kpi.val}</div>
                <div className={styles.footer}>
                  <span>{kpi.note}</span>
                </div>
              </div>
            </div>
          ))}
        </div>

        <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
          <h2 className={styles.sectionTitle}>Local Actions Panel</h2>
          <div className={styles.bentoLayout}>
            {quickActions.map((action, index) => (
              <div key={index} className={styles.bentoCard}>
                <div className={styles.cardInner}>
                  <div className={styles.cardHeader} style={{ gap: "0.5rem" }}>
                    <span className={styles.cardTitle} style={{ fontSize: "1rem", color: "var(--color-text-primary)" }}>
                      {action.title}
                    </span>
                    <span className={styles.cardDesc} style={{ minHeight: "60px", color: "var(--color-text-secondary)", fontSize: "0.875rem" }}>
                      {action.desc}
                    </span>
                  </div>
                  <Link href={action.link} className={styles.btnAction}>
                    {action.label}
                    <span>→</span>
                  </Link>
                </div>
              </div>
            ))}
          </div>
        </div>
      </>
    );
  };

  const renderDispatcherDashboard = () => {
    const kpiData = [
      { title: "Route Plans", val: fmt(stats?.openRoutePlans), icon: "🗺️", note: "Plans in the system" },
      { title: "Awaiting Route", val: fmt(stats?.parcelsWaitingForRoute), icon: "📦", note: "Parcels needing a route" },
      { title: "Open Assignments", val: fmt(stats?.openAssignments), icon: "🚛", note: "Deliveries in progress" },
    ];

    const quickActions = [
      { title: "Route Optimization", desc: "Build optimized transit path plans, configure hubs sequence, and compute mileage costs.", link: "/routes", label: "Plan Routes" },
      { title: "Courier Run Assignments", desc: "Assign pending packages to active couriers and dispatch drivers.", link: "/delivery", label: "Dispatch Deliveries" },
      { title: "Parcel Queue", desc: "Inspect shipping routes status and track transit locations.", link: "/parcels", label: "Inspect Parcels" },
    ];

    return (
      <>
        <div className={styles.header}>
          <h1 className={styles.title}>Transit Coordination Dashboard</h1>
          <p className={styles.subtitle}>
            Dispatcher console for route assignments, delivery optimization, and schedules.
          </p>
        </div>

        <div className={styles.kpiGrid}>
          {kpiData.map((kpi, index) => (
            <div key={index} className={styles.kpiCard}>
              <div className={styles.cardInner}>
                <div className={styles.cardHeader}>
                  <span className={styles.cardTitle}>{kpi.title}</span>
                  <span className={styles.iconWrapper}>{kpi.icon}</span>
                </div>
                <div className={styles.value}>{kpi.val}</div>
                <div className={styles.footer}>
                  <span>{kpi.note}</span>
                </div>
              </div>
            </div>
          ))}
        </div>

        <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
          <h2 className={styles.sectionTitle}>Routing Dispatch Controls</h2>
          <div className={styles.bentoLayout}>
            {quickActions.map((action, index) => (
              <div key={index} className={styles.bentoCard}>
                <div className={styles.cardInner}>
                  <div className={styles.cardHeader} style={{ gap: "0.5rem" }}>
                    <span className={styles.cardTitle} style={{ fontSize: "1rem", color: "var(--color-text-primary)" }}>
                      {action.title}
                    </span>
                    <span className={styles.cardDesc} style={{ minHeight: "60px", color: "var(--color-text-secondary)", fontSize: "0.875rem" }}>
                      {action.desc}
                    </span>
                  </div>
                  <Link href={action.link} className={styles.btnAction}>
                    {action.label}
                    <span>→</span>
                  </Link>
                </div>
              </div>
            ))}
          </div>
        </div>
      </>
    );
  };

  return (
    <div className={styles.container}>
      {role === "ADMIN" && renderAdminDashboard()}
      {/*
        HUB_MANAGER shares the hub-operations view with HUB_STAFF: its extra
        permissions are administrative rather than a different daily console.
        Without this branch a manager saw a blank page — no role matched.
      */}
      {(role === "HUB_STAFF" || role === "HUB_MANAGER") && renderHubStaffDashboard()}
      {role === "DISPATCHER" && renderDispatcherDashboard()}
    </div>
  );
}
