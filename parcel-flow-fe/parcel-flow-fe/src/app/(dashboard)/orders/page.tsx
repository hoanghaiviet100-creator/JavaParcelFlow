"use client";

import { useState } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { listOrdersApi } from "@/features/orders/api/orders.api";
import LoadingState from "@/shared/components/LoadingState";
import ErrorState from "@/shared/components/ErrorState";
import EmptyState from "@/shared/components/EmptyState";

const PAGE_SIZE = 20;

export default function OrdersListPage() {
  const [page, setPage] = useState(0);

  const { data, isLoading, isError, refetch, isFetching } = useQuery({
    queryKey: ["orders", page],
    queryFn: () => listOrdersApi(page, PAGE_SIZE),
    retry: false,
  });

  const pageData = data?.data;
  const orders = pageData?.content ?? [];

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <div>
          <h1 style={{ fontFamily: "var(--font-be-vietnam-pro)", fontSize: "2rem", fontWeight: 800, letterSpacing: "-0.03em" }}>
            Orders Database
          </h1>
          <p style={{ color: "var(--color-text-secondary)", fontSize: "0.875rem" }}>
            View and manage logistics orders registered at all terminals.
          </p>
        </div>
        <Link
          href="/orders/create"
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
          Create Order
        </Link>
      </div>

      {isLoading ? (
        <LoadingState message="Loading orders..." />
      ) : isError ? (
        <ErrorState title="Could not load orders" onRetry={() => refetch()} />
      ) : orders.length === 0 ? (
        <EmptyState
          icon="📦"
          title="No orders yet"
          description="Registered logistics orders will appear here once created."
        />
      ) : (
        <div
          style={{
            border: "1px solid var(--color-border)",
            borderRadius: "var(--radius-card)",
            backgroundColor: "var(--color-surface)",
            overflowX: "auto",
          }}
        >
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.875rem" }}>
            <thead>
              <tr style={{ textAlign: "left", color: "var(--color-text-secondary)" }}>
                <th style={{ padding: "0.75rem 1rem" }}>Order Code</th>
                <th style={{ padding: "0.75rem 1rem" }}>Status</th>
                <th style={{ padding: "0.75rem 1rem" }}>Weight (kg)</th>
                <th style={{ padding: "0.75rem 1rem" }}>COD</th>
                <th style={{ padding: "0.75rem 1rem" }}>Created</th>
              </tr>
            </thead>
            <tbody>
              {orders.map((o) => (
                <tr key={o.id} style={{ borderTop: "1px solid var(--color-border)" }}>
                  <td style={{ padding: "0.75rem 1rem", fontWeight: 600 }}>
                    <Link href={`/orders/${o.id}`} style={{ color: "var(--color-primary)" }}>
                      {o.orderCode}
                    </Link>
                  </td>
                  <td style={{ padding: "0.75rem 1rem" }}>{o.status}</td>
                  <td style={{ padding: "0.75rem 1rem" }}>{o.totalWeight ?? "—"}</td>
                  <td style={{ padding: "0.75rem 1rem" }}>{o.codAmount ?? "—"}</td>
                  <td style={{ padding: "0.75rem 1rem", color: "var(--color-text-secondary)" }}>
                    {new Date(o.createdAt).toLocaleString()}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {pageData && pageData.totalPages > 1 && (
        <div style={{ display: "flex", gap: "0.75rem", alignItems: "center", justifyContent: "flex-end" }}>
          <button
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0 || isFetching}
            style={paginationBtn(page === 0)}
          >
            Previous
          </button>
          <span style={{ fontSize: "0.875rem", color: "var(--color-text-secondary)" }}>
            Page {pageData.page + 1} of {pageData.totalPages}
          </span>
          <button
            onClick={() => setPage((p) => (pageData && p + 1 < pageData.totalPages ? p + 1 : p))}
            disabled={!pageData || page + 1 >= pageData.totalPages || isFetching}
            style={paginationBtn(!pageData || page + 1 >= pageData.totalPages)}
          >
            Next
          </button>
        </div>
      )}
    </div>
  );
}

function paginationBtn(disabled: boolean): React.CSSProperties {
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
