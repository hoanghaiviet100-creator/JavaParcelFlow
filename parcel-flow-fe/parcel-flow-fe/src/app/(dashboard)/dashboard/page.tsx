"use client";

import Link from "next/link";
import { useAppSelector } from "@/store/store";
import styles from "./dashboard.module.scss";

export default function DashboardPage() {
  const user = useAppSelector((state) => state.auth.user);
  const role = user?.role || "ADMIN";

  // ==========================================
  // VIEW RENDERERS BY ROLE
  // ==========================================

  const renderAdminDashboard = () => {
    const kpiData = [
      { title: "Active Logistics Users", val: "48", icon: "👤", note: "12 drivers active right now" },
      { title: "Operational Hubs", val: "6", icon: "🏢", note: "All regional hubs online" },
      { title: "Total Dynamic Orders", val: "1,249", icon: "📦", note: "+12% increase from yesterday" },
      { title: "Pending Deliveries", val: "184", icon: "⚡", note: "98.8% on-time SLA status" },
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
      { title: "Registered Today", val: "142", icon: "📝", note: "Parcels signed in at counter" },
      { title: "Pending Inbound Scans", val: "38", icon: "📥", note: "Transit parcels arriving soon" },
      { title: "Outbound Dispatches", val: "84", icon: "📤", note: "Handed over to transit couriers" },
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
            Active location: <strong style={{ color: "var(--color-primary)" }}>HN-CENTRAL-HUB-01</strong>
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
      { title: "Open Dispatch Queues", val: "8", icon: "🗺️", note: "Routing tables recalculating..." },
      { title: "Unrouted Parcels", val: "67", icon: "📦", note: "Awaiting sorting assignments" },
      { title: "Active Transit Runs", val: "14", icon: "🚛", note: "Hub-to-hub trucks in transit" },
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
      {role === "HUB_STAFF" && renderHubStaffDashboard()}
      {role === "DISPATCHER" && renderDispatcherDashboard()}
    </div>
  );
}
