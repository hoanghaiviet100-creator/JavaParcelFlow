"use client";

import { useState } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { httpClient } from "@/shared/api/http-client";
import LoadingState from "@/shared/components/LoadingState";
import ErrorState from "@/shared/components/ErrorState";
import EmptyState from "@/shared/components/EmptyState";

const PAGE_SIZE = 20;

/** Row shape of GET /api/v1/delivery-assignments (DeliveryAssignmentResponse). */
interface AssignmentRow {
  id: number;
  parcelId: number;
  parcelCode: string | null;
  parcelStatus: string | null;
  shipperId: number;
  status: string;
  assignmentType: string;
  assignmentReason: string | null;
  assignedAt: string;
  completedAt: string | null;
}

interface PageData {
  content: AssignmentRow[];
  totalElements: number;
  totalPages: number;
  number: number;
}

interface Envelope {
  data: PageData;
}

/**
 * Read-only assignment board backed by GET /api/v1/delivery-assignments.
 *
 * This page shipped as a placeholder even though the backend list endpoint
 * already existed and the dispatcher dashboard linked here. Assigning a parcel
 * to a shipper still has no API — see "Known limitations" in the root README.
 */
export default function DeliveryAssignmentListPage() {
  const [page, setPage] = useState(0);

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ["delivery-assignments", page],
    queryFn: () =>
      httpClient.get<Envelope>("/v1/delivery-assignments", {
        params: { page, size: PAGE_SIZE },
      }),
    retry: false,
  });

  const pageData = data?.data;
  const rows = pageData?.content ?? [];

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
      <div>
        <h1 style={{ fontFamily: "var(--font-be-vietnam-pro)", fontSize: "2rem", fontWeight: 800, letterSpacing: "-0.03em" }}>
          Delivery Assignments
        </h1>
        <p style={{ color: "var(--color-text-secondary)", fontSize: "0.875rem" }}>
          Last-mile handovers between hubs and couriers, newest first.
        </p>
      </div>

      {isLoading ? (
        <LoadingState message="Loading assignments..." />
      ) : isError ? (
        <ErrorState title="Could not load delivery assignments" onRetry={() => refetch()} />
      ) : rows.length === 0 ? (
        <EmptyState
          icon="🛵"
          title="No delivery assignments"
          description="Parcels handed to couriers will appear here."
        />
      ) : (
        <div style={{ border: "1px solid var(--color-border)", borderRadius: "var(--radius-card)", overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.875rem" }}>
            <thead>
              <tr style={{ backgroundColor: "var(--color-surface)", textAlign: "left" }}>
                <th style={{ padding: "0.75rem 1rem" }}>#</th>
                <th style={{ padding: "0.75rem 1rem" }}>Parcel</th>
                <th style={{ padding: "0.75rem 1rem" }}>Parcel Status</th>
                <th style={{ padding: "0.75rem 1rem" }}>Shipper</th>
                <th style={{ padding: "0.75rem 1rem" }}>Assignment</th>
                <th style={{ padding: "0.75rem 1rem" }}>Type</th>
                <th style={{ padding: "0.75rem 1rem" }}>Assigned</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((a) => (
                <tr key={a.id} style={{ borderTop: "1px solid var(--color-border)" }}>
                  <td style={{ padding: "0.75rem 1rem" }}>{a.id}</td>
                  <td style={{ padding: "0.75rem 1rem" }}>
                    {a.parcelCode ? (
                      <Link href={`/parcels/${a.parcelId}`} style={{ color: "var(--color-primary)" }}>
                        {a.parcelCode}
                      </Link>
                    ) : (
                      a.parcelId
                    )}
                  </td>
                  <td style={{ padding: "0.75rem 1rem" }}>{a.parcelStatus ?? "—"}</td>
                  <td style={{ padding: "0.75rem 1rem" }}>{a.shipperId}</td>
                  <td style={{ padding: "0.75rem 1rem" }}>{a.status}</td>
                  <td style={{ padding: "0.75rem 1rem" }}>{a.assignmentType}</td>
                  <td style={{ padding: "0.75rem 1rem" }}>{new Date(a.assignedAt).toLocaleString()}</td>
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
