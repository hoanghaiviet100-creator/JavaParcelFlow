"use client";

import { use } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import useRoleGuard from "@/shared/hooks/useRoleGuard";
import { getHubApi, listHubsApi } from "@/features/hubs/api/hubs.api";
import LoadingState from "@/shared/components/LoadingState";
import ErrorState from "@/shared/components/ErrorState";

interface PageProps {
  params: Promise<{ id: string }>;
}

const card: React.CSSProperties = {
  border: "1px solid var(--color-border)",
  borderRadius: "var(--radius-card)",
  padding: "1.5rem",
  backgroundColor: "var(--color-surface)",
};

function Field({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "0.25rem" }}>
      <span style={{ fontSize: "0.7rem", letterSpacing: "0.06em", textTransform: "uppercase", color: "var(--color-text-secondary)" }}>
        {label}
      </span>
      <span style={{ fontSize: "0.9375rem" }}>{value ?? "—"}</span>
    </div>
  );
}

/**
 * Hub detail, backed by GET /api/v1/hubs/{id}.
 *
 * The endpoint existed from the start; this page was a placeholder reading
 * "operational metrics will load here", so the hub list led nowhere. The parent
 * hub is resolved from the registry list (a handful of rows, already cached by
 * the list page) because the API returns only parentHubId.
 */
export default function HubDetailPage({ params }: PageProps) {
  const { id } = use(params);
  const { isAuthorized, isLoading: guardLoading } = useRoleGuard(["ADMIN"]);

  const hubQuery = useQuery({
    queryKey: ["hub", id],
    queryFn: () => getHubApi(id),
    enabled: isAuthorized && !!id,
    retry: false,
  });

  const hubsQuery = useQuery({
    queryKey: ["hubs"],
    queryFn: listHubsApi,
    enabled: isAuthorized,
    retry: false,
  });

  if (guardLoading || !isAuthorized) {
    return <LoadingState message="Verifying user access permissions..." />;
  }

  const hub = hubQuery.data?.data;
  const parent = hub?.parentHubId
    ? hubsQuery.data?.data.find((h) => h.id === hub.parentHubId)
    : undefined;
  const children = hubsQuery.data?.data.filter((h) => h.parentHubId === hub?.id) ?? [];

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
      <div>
        <Link href="/hubs" style={{ color: "var(--color-primary)", fontSize: "0.875rem" }}>
          ← Back to Hubs
        </Link>
        <h1 style={{ fontFamily: "var(--font-be-vietnam-pro)", fontSize: "2rem", fontWeight: 800, letterSpacing: "-0.03em", marginTop: "0.5rem" }}>
          Hub Details
        </h1>
        {hub && (
          <p style={{ color: "var(--color-text-secondary)", fontSize: "0.875rem" }}>
            {hub.code} — {hub.name}
          </p>
        )}
      </div>

      {hubQuery.isLoading ? (
        <LoadingState message="Loading hub..." />
      ) : hubQuery.isError || !hub ? (
        <ErrorState title="Could not load this hub" onRetry={() => hubQuery.refetch()} />
      ) : (
        <>
          <div style={{ ...card, display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: "1.25rem" }}>
            <Field label="Code" value={hub.code} />
            <Field label="Name" value={hub.name} />
            <Field label="Type" value={hub.type} />
            <Field label="Phone" value={hub.phone} />
            <Field label="Status" value={hub.isActive ? "Active" : "Inactive"} />
            <Field label="Parent hub" value={parent ? `${parent.code} — ${parent.name}` : "None (top level)"} />
          </div>

          <div style={card}>
            <h2 style={{ fontSize: "1rem", fontWeight: 700, marginBottom: "1rem" }}>Location</h2>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: "1.25rem" }}>
              <Field label="Address" value={hub.addressLine} />
              <Field label="Ward ID" value={hub.wardId} />
              <Field label="District ID" value={hub.districtId} />
              <Field label="Province ID" value={hub.provinceId} />
            </div>
          </div>

          <div style={card}>
            <h2 style={{ fontSize: "1rem", fontWeight: 700, marginBottom: "1rem" }}>
              Branch hubs ({children.length})
            </h2>
            {children.length === 0 ? (
              <p style={{ color: "var(--color-text-secondary)", fontSize: "0.875rem" }}>
                No hubs report to this one.
              </p>
            ) : (
              <ul style={{ display: "flex", flexDirection: "column", gap: "0.5rem", listStyle: "none", padding: 0 }}>
                {children.map((c) => (
                  <li key={c.id}>
                    <Link href={`/hubs/${c.id}`} style={{ color: "var(--color-primary)" }}>
                      {c.code}
                    </Link>{" "}
                    <span style={{ color: "var(--color-text-secondary)" }}>— {c.name}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </>
      )}
    </div>
  );
}
