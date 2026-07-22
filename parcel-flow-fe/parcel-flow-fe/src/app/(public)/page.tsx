import Link from "next/link";
import styles from "./page.module.scss";

export default function HomePage() {
  return (
    <div className={styles.container}>
      {/* Hero Header Section */}
      <section className={styles.hero}>
        <div className={styles.eyebrow}>Logistics Flow Engine v2.4</div>
        <h1 className={styles.title}>
          Intelligent Parcel Transportation. <span>Orchestrated.</span>
        </h1>
        <p className={styles.subtitle}>
          A high-performance logistics management platform for automated parcel routing, 
          real-time scan tracking, and dispatching coordination across national hub networks.
        </p>
        
        <div className={styles.actions}>
          <Link href="/login" className={styles.btnPrimary}>
            Staff Portal
            <span className={styles.btnIcon}>↗</span>
          </Link>
          <Link href="/tracking" className={styles.btnSecondary}>
            Track Parcel
          </Link>
        </div>
      </section>

      {/* Asymmetrical Bento Grid */}
      <section className={styles.bentoGrid}>
        {/* Card 1: Network Hubs Status (span 8) */}
        <div className={`${styles.bentoCard} ${styles.span8}`}>
          <div className={styles.cardInner}>
            <div className={styles.cardHeader}>
              <div className={styles.cardTitle}>Live Scanning Network</div>
              <div className={styles.cardDesc}>
                Real-time operational status from registration counters and logistics distribution hubs.
              </div>
            </div>
            
            <div className={styles.networkDisplay}>
              <div className={styles.networkRow}>
                <div className={styles.nodeInfo}>
                  <span className={`${styles.dot} ${styles.pulse}`} />
                  <span>HN-CENTRAL-HUB-01</span>
                </div>
                <span className={styles.latency}>ONLINE (12ms)</span>
              </div>
              <div className={styles.networkRow}>
                <div className={styles.nodeInfo}>
                  <span className={`${styles.dot} ${styles.pulse}`} />
                  <span>DN-TRANSIT-HUB-02</span>
                </div>
                <span className={styles.latency}>ONLINE (18ms)</span>
              </div>
              <div className={styles.networkRow}>
                <div className={styles.nodeInfo}>
                  <span className={`${styles.dot} ${styles.pulse}`} />
                  <span>HCM-GATEWAY-HUB-03</span>
                </div>
                <span className={styles.latency}>ONLINE (15ms)</span>
              </div>
            </div>
          </div>
        </div>

        {/* Card 2: Performance Statistics (span 4) */}
        <div className={`${styles.bentoCard} ${styles.span4}`}>
          <div className={styles.cardInner}>
            <div className={styles.cardHeader}>
              <div className={styles.cardTitle}>Network Performance</div>
              <div className={styles.cardDesc}>
                Consolidated operational analytics.
              </div>
            </div>

            <div className={styles.statsRow}>
              <div className={styles.statItem}>
                <span className={styles.statVal}>99.98%</span>
                <span className={styles.statLabel}>SLA Success</span>
              </div>
              <div className={styles.statItem}>
                <span className={styles.statVal}>24.5k</span>
                <span className={styles.statLabel}>Parcels / Hr</span>
              </div>
            </div>
          </div>
        </div>

        {/* Card 3: Security & Verification (span 4) */}
        <div className={`${styles.bentoCard} ${styles.span4}`}>
          <div className={styles.cardInner}>
            <div className={styles.cardHeader}>
              <div className={styles.cardTitle}>Haptic Custody Transfers</div>
              <div className={styles.cardDesc}>
                Cryptographic signature scans record custody handovers between dispatchers, drivers, and hubs.
              </div>
            </div>
            {/* Small subtle visual indicator */}
            <div className={styles.statsRow}>
              <div className={styles.statItem}>
                <span className={styles.statVal}>SECURE</span>
                <span className={styles.statLabel}>Handshake State</span>
              </div>
            </div>
          </div>
        </div>

        {/* Card 4: Live Event Feed Log (span 8) */}
        <div className={`${styles.bentoCard} ${styles.span8}`}>
          <div className={styles.cardInner}>
            <div className={styles.cardHeader}>
              <div className={styles.cardTitle}>Real-time Activity Stream</div>
              <div className={styles.cardDesc}>
                Automated status stream of parcel routing and last-mile delivery dispatches.
              </div>
            </div>
            
            <div className={styles.feedLog}>
              <div className={`${styles.logEntry} ${styles.success}`}>
                <span className={styles.timestamp}>[01:40:02]</span>
                <span className={styles.message}>ROUTE PLAN PF-9824 RE-CALCULATED SUCCESSFULLY</span>
              </div>
              <div className={styles.logEntry}>
                <span className={styles.timestamp}>[01:39:15]</span>
                <span className={styles.message}>PARCEL #PF-6701 REGISTERED AT HN-CENTRAL-HUB-01</span>
              </div>
              <div className={styles.logEntry}>
                <span className={styles.timestamp}>[01:38:50]</span>
                <span className={styles.message}>DISPATCH TO SHIPPER #DISP-089 CONFIRMED IN HCM-GATEWAY</span>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
