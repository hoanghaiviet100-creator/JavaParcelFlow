"use client";

import { useQuery } from "@tanstack/react-query";
import useRoleGuard from "@/shared/hooks/useRoleGuard";
import { listHubsApi } from "@/features/hubs/api/hubs.api";
import LoadingState from "@/shared/components/LoadingState";
import ErrorState from "@/shared/components/ErrorState";
import EmptyState from "@/shared/components/EmptyState";

export default function HubsListPage() {
  const { isAuthorized, isLoading: guardLoading } = useRoleGuard(["ADMIN"]);

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ["hubs"],
    queryFn: listHubsApi,
    enabled: isAuthorized,
    retry: false,
  });

  if (guardLoading || !isAuthorized) {
    return <LoadingState message="Verifying user access permissions..." />;
  }

  const hubs = data?.data ?? [];

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
      <div>
        <h1 style={{ fontFamily: "var(--font-be-vietnam-pro)", fontSize: "2rem", fontWeight: 800, letterSpacing: "-0.03em" }}>
          Logistics Hubs
        </h1>
        <p style={{ color: "var(--color-text-secondary)", fontSize: "0.875rem" }}>
          Manage transit locations, distribution nodes, and hub inventories.
        </p>
      </div>

      {isLoading ? (
        <LoadingState message="Loading hubs..." />
      ) : isError ? (
        <ErrorState title="Could not load hubs" onRetry={() => refetch()} />
      ) : hubs.length === 0 ? (
        <EmptyState icon="🏢" title="No hubs found" description="Registered hubs will appear here." />
      ) : (
        <div style={{ border: "1px solid var(--color-border)", borderRadius: "var(--radius-card)", backgroundColor: "var(--color-surface)", overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.875rem" }}>
            <thead>
              <tr style={{ textAlign: "left", color: "var(--color-text-secondary)" }}>
                <th style={{ padding: "0.75rem 1rem" }}>Code</th>
                <th style={{ padding: "0.75rem 1rem" }}>Name</th>
                <th style={{ padding: "0.75rem 1rem" }}>Type</th>
                <th style={{ padding: "0.75rem 1rem" }}>Address</th>
                <th style={{ padding: "0.75rem 1rem" }}>Active</th>
              </tr>
            </thead>
            <tbody>
              {hubs.map((h) => (
                <tr key={h.id} style={{ borderTop: "1px solid var(--color-border)" }}>
                  <td style={{ padding: "0.75rem 1rem", fontWeight: 600 }}>{h.code}</td>
                  <td style={{ padding: "0.75rem 1rem" }}>{h.name}</td>
                  <td style={{ padding: "0.75rem 1rem" }}>{h.type}</td>
                  <td style={{ padding: "0.75rem 1rem", color: "var(--color-text-secondary)" }}>{h.addressLine}</td>
                  <td style={{ padding: "0.75rem 1rem" }}>{h.isActive ? "Yes" : "No"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
