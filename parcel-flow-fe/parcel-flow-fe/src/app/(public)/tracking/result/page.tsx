"use client";

import { useSearchParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { getTrackingApi } from "@/features/tracking/api/get-tracking.api";
import LoadingState from "@/shared/components/LoadingState";
import styles from "./result.module.scss";

export default function TrackingResultPage() {
  const searchParams = useSearchParams();
  const code = searchParams.get("code") || "";
  const phone = searchParams.get("phone") || undefined;

  const { data, isLoading, isError } = useQuery({
    queryKey: ["tracking-search", code, phone],
    queryFn: () => getTrackingApi(code, phone),
    enabled: !!code,
    retry: false,
  });

  const formatTimestamp = (isoString: string) => {
    try {
      const date = new Date(isoString);
      return date.toLocaleString("en-US", {
        month: "short",
        day: "numeric",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
        hour12: false,
      });
    } catch {
      return isoString;
    }
  };

  const getStatusText = (status: string) => {
    switch (status) {
      case "CREATED":
        return "Order Registered";
      case "HUB_INBOUND":
        return "Received at Transit Hub";
      case "HUB_OUTBOUND":
        return "Departed Transit Hub";
      case "OUT_FOR_DELIVERY":
        return "Out for Delivery";
      case "DELIVERED":
        return "Delivered Successfully";
      case "FAILED":
        return "Delivery Attempt Failed";
      default:
        return status;
    }
  };

  if (isLoading) {
    return <LoadingState message="Locating shipment details..." fullPage />;
  }

  const tracking = data?.data?.tracking;

  if (isError || !tracking) {
    return (
      <div className={styles.errorState}>
        <div style={{ fontSize: "3rem" }}>🔍</div>
        <h2 style={{ fontSize: "1.5rem", fontWeight: 800, letterSpacing: "-0.02em" }}>Shipment Not Found</h2>
        <p style={{ color: "var(--color-text-secondary)", fontSize: "0.875rem", lineHeight: 1.5 }}>
          We could not find any active logistics order matching code{" "}
          <strong style={{ color: "var(--color-text-primary)" }}>&quot;{code}&quot;</strong>. 
          Please double check the spelling and try again.
        </p>
        <Link href="/tracking" className={styles.backLink} style={{ fontSize: "1rem", color: "var(--color-primary)" }}>
          ← Back to Search
        </Link>
      </div>
    );
  }

  return (
    <div className={styles.container}>
      <div>
        <Link href="/tracking" className={styles.backLink}>
          ← Back to Search
        </Link>
        <div className={styles.header}>
          <div className={styles.titleBlock}>
            <h1 className={styles.title}>Shipment Status</h1>
            <p className={styles.subtitle}>
              Code: <strong style={{ color: "var(--color-text-primary)" }}>{tracking.orderCode}</strong>
            </p>
          </div>
          <span className={`${styles.statusBadge} ${styles[`status${tracking.status}`]}`}>
            {getStatusText(tracking.status)}
          </span>
        </div>
      </div>

      <div className={styles.grid}>
        {/* Left Side: Order & Parcel details */}
        <div className={styles.detailsCol}>
          {/* Card 1: Route chain */}
          <div className={styles.card}>
            <h3 className={styles.cardTitle}>Transit Route Path</h3>
            <div className={styles.addressRow}>
              <div className={styles.addressNode}>
                <div className={styles.nodeDotActive} />
                <div className={styles.nodeContent}>
                  <span className={styles.nodeLabel}>Sender Info</span>
                  <span className={styles.nodeValue}>{tracking.senderName}</span>
                  <span className={styles.nodeSub}>{tracking.senderAddress}</span>
                </div>
              </div>
              <div className={styles.addressNode}>
                <div className={styles.nodeDot} />
                <div className={styles.nodeContent}>
                  <span className={styles.nodeLabel}>Receiver Info</span>
                  <span className={styles.nodeValue}>{tracking.receiverName}</span>
                  <span className={styles.nodeSub}>{tracking.receiverAddress}</span>
                </div>
              </div>
            </div>
          </div>

          {/* Card 2: Parcel Breakdowns */}
          <div className={styles.card}>
            <h3 className={styles.cardTitle}>Parcel Manifest ({tracking.parcels.length})</h3>
            <div className={styles.parcelList}>
              {tracking.parcels.map((parcel, idx) => (
                <div key={idx} className={styles.parcelItem}>
                  <div className={styles.parcelInfo}>
                    <span className={styles.parcelCode}>{parcel.parcelCode}</span>
                    <span className={styles.parcelDesc}>{parcel.description}</span>
                  </div>
                  <span className={styles.parcelWeight}>{parcel.weight} kg</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Right Side: Timeline steps */}
        <div className={styles.card}>
          <h3 className={styles.cardTitle}>Custody & Scanning History</h3>
          <div className={styles.timeline}>
            {tracking.events.map((event, idx) => {
              const isCurrent = idx === 0;
              return (
                <div key={event.id} className={styles.timelineItem}>
                  <div className={isCurrent ? styles.timelineDotActive : styles.timelineDot} />
                  <div className={styles.timelineContent}>
                    <div className={styles.timelineHeader}>
                      <span className={styles.timelineStatus}>
                        {getStatusText(event.status)}
                      </span>
                      <span className={styles.timelineTime}>
                        {formatTimestamp(event.timestamp)}
                      </span>
                    </div>
                    <span className={styles.timelineLocation}>
                      📍 {event.locationName}
                    </span>
                    <p className={styles.timelineDesc}>{event.description}</p>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
