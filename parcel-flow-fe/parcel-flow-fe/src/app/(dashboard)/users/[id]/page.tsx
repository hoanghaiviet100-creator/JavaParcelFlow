"use client";

import { use, useState } from "react";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import useRoleGuard from "@/shared/hooks/useRoleGuard";
import LoadingState from "@/shared/components/LoadingState";
import ErrorState from "@/shared/components/ErrorState";
import Button from "@/shared/components/Button";
import { ApiError } from "@/shared/api/api-error";
import {
  getUserApi,
  resendTempPasswordApi,
  unlockUserApi,
} from "@/features/users/api/users.api";

interface PageProps {
  params: Promise<{ id: string }>;
}

const card: React.CSSProperties = {
  border: "1px solid var(--color-border)",
  borderRadius: "var(--radius-card)",
  padding: "1.5rem",
  backgroundColor: "var(--color-surface)",
};

const grid: React.CSSProperties = {
  display: "grid",
  gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))",
  gap: "1.25rem",
};

function Field({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "0.25rem" }}>
      <span
        style={{
          fontSize: "0.7rem",
          letterSpacing: "0.06em",
          textTransform: "uppercase",
          color: "var(--color-text-secondary)",
        }}
      >
        {label}
      </span>
      <span style={{ fontSize: "0.9375rem" }}>{value ?? "—"}</span>
    </div>
  );
}

function formatTime(iso?: string | null) {
  if (!iso) return "—";
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString();
}

/**
 * Account detail, backed by GET /api/v1/users/{id}.
 *
 * The unlock and resend-temp-password endpoints already existed but nothing in
 * the UI called them, so an account locked out by repeated failed logins could
 * only be freed with a direct API call. Both are wired up here.
 *
 * params is a Promise in this Next.js version and is unwrapped with React's
 * `use()` — the documented pattern for reading route params inside a Client
 * Component (node_modules/next/dist/docs, dynamic-routes).
 */
export default function UserDetailPage({ params }: PageProps) {
  const { id } = use(params);
  const { isAuthorized, isLoading: guardLoading } = useRoleGuard(["ADMIN"]);
  const queryClient = useQueryClient();
  const [banner, setBanner] = useState<{ kind: "ok" | "err"; text: string } | null>(null);

  const userQuery = useQuery({
    queryKey: ["user", id],
    queryFn: () => getUserApi(id),
    enabled: isAuthorized && !!id,
    retry: false,
  });

  const afterAction = (text: string) => {
    setBanner({ kind: "ok", text });
    queryClient.invalidateQueries({ queryKey: ["user", id] });
    queryClient.invalidateQueries({ queryKey: ["users"] });
  };

  const onActionError = (err: unknown) =>
    setBanner({
      kind: "err",
      text: err instanceof ApiError ? err.message : "The action could not be completed.",
    });

  const unlock = useMutation({
    mutationFn: () => unlockUserApi(id),
    onSuccess: () => afterAction("Account unlocked. Any live session was revoked."),
    onError: onActionError,
  });

  const resend = useMutation({
    mutationFn: () => resendTempPasswordApi(id),
    onSuccess: () =>
      afterAction("A new temporary password was emailed. The previous one no longer works."),
    onError: onActionError,
  });

  if (guardLoading || !isAuthorized) {
    return <LoadingState message="Verifying user access permissions..." />;
  }

  const user = userQuery.data?.data;

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
      <div>
        <Link href="/users" style={{ color: "var(--color-primary)", fontSize: "0.875rem" }}>
          ← Back to Users
        </Link>
        <h1
          style={{
            fontFamily: "var(--font-be-vietnam-pro)",
            fontSize: "2rem",
            fontWeight: 800,
            letterSpacing: "-0.03em",
            marginTop: "0.5rem",
          }}
        >
          Account Detail
        </h1>
        {user && (
          <p style={{ color: "var(--color-text-secondary)", fontSize: "0.875rem" }}>
            {user.fullName} — {user.email}
          </p>
        )}
      </div>

      {banner && (
        <div
          style={{
            padding: "0.75rem 1rem",
            borderRadius: "var(--radius-card)",
            border: "1px solid var(--color-border)",
            background: banner.kind === "ok" ? "rgba(16,185,129,0.1)" : "rgba(239,68,68,0.1)",
            color: banner.kind === "ok" ? "#065f46" : "#991b1b",
            fontSize: "0.875rem",
          }}
        >
          {banner.text}
        </div>
      )}

      {userQuery.isLoading ? (
        <LoadingState message="Loading account..." />
      ) : userQuery.isError || !user ? (
        <ErrorState title="Could not load this account" onRetry={() => userQuery.refetch()} />
      ) : (
        <>
          <div style={{ ...card, ...grid }}>
            <Field label="Full name" value={user.fullName} />
            <Field label="Email" value={user.email} />
            <Field label="Phone" value={user.phone} />
            <Field label="Role" value={user.roleCode} />
            <Field
              label="Home hub"
              value={
                user.hubId ? (
                  <Link href={`/hubs/${user.hubId}`} style={{ color: "var(--color-primary)" }}>
                    Hub {user.hubId}
                  </Link>
                ) : (
                  "—"
                )
              }
            />
            <Field label="Created" value={formatTime(user.createdAt)} />
          </div>

          <div style={card}>
            <h2 style={{ fontSize: "1rem", fontWeight: 700, marginBottom: "1rem" }}>
              Access status
            </h2>

            <div style={grid}>
              <Field label="Account" value={user.active ? "Active" : "Locked"} />
              <Field label="Must change password" value={user.mustChangePassword ? "Yes" : "No"} />
              <Field label="Temp password expires" value={formatTime(user.passwordExpiresAt)} />
              <Field label="Lock reason" value={user.lockReason} />
              <Field label="Locked at" value={formatTime(user.lockedAt)} />
            </div>

            <div style={{ display: "flex", gap: "0.75rem", marginTop: "1.5rem", flexWrap: "wrap" }}>
              <Button
                variant="primary"
                onClick={() => {
                  setBanner(null);
                  unlock.mutate();
                }}
                loading={unlock.isPending}
                disabled={user.active}
              >
                {user.active ? "Account is not locked" : "Unlock account"}
              </Button>

              <Button
                variant="secondary"
                onClick={() => {
                  setBanner(null);
                  resend.mutate();
                }}
                loading={resend.isPending}
              >
                Resend temporary password
              </Button>
            </div>

            <p
              style={{
                color: "var(--color-text-secondary)",
                fontSize: "0.8125rem",
                marginTop: "0.75rem",
              }}
            >
              Resending replaces the current password and forces a change at next sign-in.
            </p>
          </div>
        </>
      )}
    </div>
  );
}
