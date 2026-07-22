"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { httpClient } from "@/shared/api/http-client";
import LoadingState from "@/shared/components/LoadingState";
import ErrorState from "@/shared/components/ErrorState";
import EmptyState from "@/shared/components/EmptyState";

const PAGE_SIZE = 20;

/** Row shape of GET /api/v1/route-plans (RoutePlanResponse; steps omitted in the list). */
interface RoutePlanRow {
  id: number;
  parcelId: number;
  plannedBy: number;
  status: string;
  createdAt: string;
  approvedAt: string | null;
}

interface PageData {
  content: RoutePlanRow[];
  totalElements: number;
  totalPages: number;
  number: number;
}

interface Envelope {
  data: PageData;
}

/**
 * Read-only route-plan list backed by GET /api/v1/route-plans.
 *
 * This page shipped as a placeholder ("plans will display here") even though
 * the backend list endpoint already existed and the dashboard linked here.
 * It now renders what the API returns. Creating a plan still has no API —
 * see "Known limitations" in the root README.
 */
export default function RoutePlansListPage() {
  const [page, setPage] = useState(0);

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ["route-plans", page],
    queryFn: () =>
      httpClient.get<Envelope>("/v1/route-plans", { params: { page, size: PAGE_SIZE } }),
    retry: false,
  });

  const pageData = data?.data;
  const plans = pageData?.content ?? [];

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
      <div>
        <h1 style={{ fontFamily: "var(--font-be-vietnam-pro)", fontSize: "2rem", fontWeight: 800, letterSpacing: "-0.03em" }}>
          Transit Routes
        </h1>
        <p style={{ color: "var(--color-text-secondary)", fontSize: "0.875rem" }}>
          Multi-hub route plans for parcels in the network.
        </p>
      </div>

      {isLoading ? (
        <LoadingState message="Loading route plans..." />
      ) : isError ? (
        <ErrorState title="Could not load route plans" onRetry={() => refetch()} />
      ) : plans.length === 0 ? (
        <EmptyState
          icon="🗺️"
          title="No route plans yet"
          description="No parcel route plans exist in the system yet."
        />
      ) : (
        <div style={{ border: "1px solid var(--color-border)", borderRadius: "var(--radius-card)", overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.875rem" }}>
            <thead>
              <tr style={{ backgroundColor: "var(--color-surface)", textAlign: "left" }}>
                <th style={{ padding: "0.75rem 1rem" }}>Plan #</th>
                <th style={{ padding: "0.75rem 1rem" }}>Parcel ID</th>
                <th style={{ padding: "0.75rem 1rem" }}>Status</th>
                <th style={{ padding: "0.75rem 1rem" }}>Created</th>
                <th style={{ padding: "0.75rem 1rem" }}>Approved</th>
              </tr>
            </thead>
            <tbody>
              {plans.map((p) => (
                <tr key={p.id} style={{ borderTop: "1px solid var(--color-border)" }}>
                  <td style={{ padding: "0.75rem 1rem" }}>{p.id}</td>
                  <td style={{ padding: "0.75rem 1rem" }}>{p.parcelId}</td>
                  <td style={{ padding: "0.75rem 1rem" }}>{p.status}</td>
                  <td style={{ padding: "0.75rem 1rem" }}>{new Date(p.createdAt).toLocaleString()}</td>
                  <td style={{ padding: "0.75rem 1rem" }}>
                    {p.approvedAt ? new Date(p.approvedAt).toLocaleString() : "—"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {pageData && pageData.totalPages > 1 && (
        <div style={{ display: "flex", gap: "0.5rem", justifyContent: "center" }}>
          <button disabled={page === 0} onClick={() => setPage(page - 1)}>← Prev</button>
          <span style={{ alignSelf: "center", fontSize: "0.875rem" }}>
            Page {pageData.number + 1} / {pageData.totalPages}
          </span>
          <button disabled={page + 1 >= pageData.totalPages} onClick={() => setPage(page + 1)}>Next →</button>
        </div>
      )}
    </div>
  );
}
