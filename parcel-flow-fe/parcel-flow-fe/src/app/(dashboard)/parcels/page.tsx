"use client";

import { useState } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { listParcelsApi } from "@/features/parcels/api/parcels.api";
import LoadingState from "@/shared/components/LoadingState";
import ErrorState from "@/shared/components/ErrorState";
import EmptyState from "@/shared/components/EmptyState";

const PAGE_SIZE = 20;

export default function ParcelsListPage() {
  const [page, setPage] = useState(0);

  const { data, isLoading, isError, refetch, isFetching } = useQuery({
    queryKey: ["parcels", page],
    queryFn: () => listParcelsApi(page, PAGE_SIZE),
    retry: false,
  });

  const pageData = data?.data;
  const parcels = pageData?.content ?? [];

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <div>
          <h1 style={{ fontFamily: "var(--font-be-vietnam-pro)", fontSize: "2rem", fontWeight: 800, letterSpacing: "-0.03em" }}>
            Parcels Registry
          </h1>
          <p style={{ color: "var(--color-text-secondary)", fontSize: "0.875rem" }}>
            Operational overview of all physical package items in transit nodes.
          </p>
        </div>
        <Link
          href="/dashboard/parcels/scan"
          style={{
            backgroundColor: "var(--color-primary)",
            color: "#FFF",
            padding: "0.625rem 1.25rem",
            borderRadius: "var(--radius-button, 12px)",
            fontWeight: 600,
            fontSize: "0.875rem",
            textDecoration: "none",
          }}
        >
          Scan Barcode
        </Link>
      </div>

      {isLoading ? (
        <LoadingState message="Loading parcels..." />
      ) : isError ? (
        <ErrorState title="Could not load parcels" onRetry={() => refetch()} />
      ) : parcels.length === 0 ? (
        <EmptyState icon="📦" title="No parcels yet" description="Registered packages will appear here." />
      ) : (
        <div style={{ border: "1px solid var(--color-border)", borderRadius: "var(--radius-card)", backgroundColor: "var(--color-surface)", overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.875rem" }}>
            <thead>
              <tr style={{ textAlign: "left", color: "var(--color-text-secondary)" }}>
                <th style={{ padding: "0.75rem 1rem" }}>Parcel Code</th>
                <th style={{ padding: "0.75rem 1rem" }}>Weight (kg)</th>
                <th style={{ padding: "0.75rem 1rem" }}>Status</th>
              </tr>
            </thead>
            <tbody>
              {parcels.map((p) => (
                <tr key={p.id} style={{ borderTop: "1px solid var(--color-border)" }}>
                  <td style={{ padding: "0.75rem 1rem", fontWeight: 600 }}>
                    <Link href={`/dashboard/parcels/${p.id}`} style={{ color: "var(--color-primary)" }}>
                      {p.parcelCode}
                    </Link>
                  </td>
                  <td style={{ padding: "0.75rem 1rem" }}>{p.weight}</td>
                  <td style={{ padding: "0.75rem 1rem" }}>{p.status}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {pageData && pageData.totalPages > 1 && (
        <div style={{ display: "flex", gap: "0.75rem", alignItems: "center", justifyContent: "flex-end" }}>
          <button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0 || isFetching} style={pageBtn(page === 0)}>
            Previous
          </button>
          <span style={{ fontSize: "0.875rem", color: "var(--color-text-secondary)" }}>
            Page {pageData.page + 1} of {pageData.totalPages}
          </span>
          <button onClick={() => setPage((p) => (p + 1 < pageData.totalPages ? p + 1 : p))} disabled={page + 1 >= pageData.totalPages || isFetching} style={pageBtn(page + 1 >= pageData.totalPages)}>
            Next
          </button>
        </div>
      )}
    </div>
  );
}

function pageBtn(disabled: boolean): React.CSSProperties {
  return {
    padding: "0.5rem 1rem",
    borderRadius: "var(--radius-button, 8px)",
    border: "1px solid var(--color-border)",
    background: "var(--color-surface)",
    color: "var(--color-text-primary)",
    fontSize: "0.875rem",
    cursor: disabled ? "not-allowed" : "pointer",
    opacity: disabled ? 0.5 : 1,
  };
}
