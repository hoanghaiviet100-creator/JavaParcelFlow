"use client";

import { useState } from "react";
import Link from "next/link";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import useRoleGuard from "@/shared/hooks/useRoleGuard";
import LoadingState from "@/shared/components/LoadingState";
import ErrorState from "@/shared/components/ErrorState";
import EmptyState from "@/shared/components/EmptyState";
import Input from "@/shared/components/Input";
import Button from "@/shared/components/Button";
import { listUsersApi } from "@/features/users/api/users.api";
import {
  createUserSchema,
  CreateUserSchemaType,
} from "@/features/users/schemas/create-user.schema";
import { createUserApi } from "@/features/users/api/create-user.api";
import { ApiError } from "@/shared/api/api-error";

const ROLES = ["ADMIN", "HUB_MANAGER", "HUB_STAFF", "DISPATCHER", "SHIPPER"] as const;

export default function UsersListPage() {
  const { isAuthorized, isLoading } = useRoleGuard(["ADMIN"]);
  const [banner, setBanner] = useState<{ kind: "ok" | "err"; text: string } | null>(null);
  const queryClient = useQueryClient();

  const usersQuery = useQuery({
    queryKey: ["users"],
    queryFn: () => listUsersApi(0, 50),
    enabled: isAuthorized,
    retry: false,
  });

  const {
    register,
    handleSubmit,
    reset,
    watch,
    formState: { errors },
  } = useForm<CreateUserSchemaType>({
    resolver: zodResolver(createUserSchema),
    defaultValues: { fullName: "", email: "", phone: "", roleCode: "HUB_STAFF", hubId: "" },
  });

  const selectedRole = watch("roleCode");

  const mutation = useMutation({
    mutationFn: (data: CreateUserSchemaType) =>
      createUserApi({
        fullName: data.fullName,
        email: data.email,
        phone: data.phone || undefined,
        roleCode: data.roleCode,
        hubId: data.hubId ? Number(data.hubId) : undefined,
      }),
    onSuccess: (res) => {
      setBanner({
        kind: "ok",
        text: `Account created for ${res.data.email}. A temporary password was emailed to them.`,
      });
      reset();
      // Pull the new account into the table below without a page reload.
      queryClient.invalidateQueries({ queryKey: ["users"] });
    },
    onError: (err) => {
      setBanner({
        kind: "err",
        text: err instanceof ApiError ? err.message : "Could not create the account.",
      });
    },
  });

  if (isLoading || !isAuthorized) {
    return <LoadingState message="Verifying user access permissions..." />;
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
      <div>
        <h1 style={{ fontSize: "2rem", fontWeight: 800, letterSpacing: "-0.03em" }}>
          User Accounts
        </h1>
        <p style={{ color: "var(--color-text-secondary)", fontSize: "0.875rem" }}>
          Create staff, dispatcher, and courier accounts. A secure temporary password is
          generated and emailed automatically; the user must change it on first login.
        </p>
      </div>

      {banner && (
        <div
          style={{
            padding: "0.75rem 1rem",
            borderRadius: "var(--radius-card)",
            border: "1px solid var(--color-border)",
            background:
              banner.kind === "ok" ? "rgba(16,185,129,0.1)" : "rgba(239,68,68,0.1)",
            color: banner.kind === "ok" ? "#065f46" : "#991b1b",
            fontSize: "0.875rem",
          }}
        >
          {banner.text}
        </div>
      )}

      <form
        onSubmit={handleSubmit((d) => {
          setBanner(null);
          mutation.mutate(d);
        })}
        style={{
          display: "flex",
          flexDirection: "column",
          gap: "1rem",
          border: "1px solid var(--color-border)",
          borderRadius: "var(--radius-card)",
          padding: "1.5rem",
          backgroundColor: "var(--color-surface)",
          maxWidth: 640,
        }}
      >
        <Input label="Full Name" placeholder="Nguyen Van A" error={errors.fullName?.message} {...register("fullName")} />
        <Input type="email" label="Email" placeholder="name@parcelflow.com" error={errors.email?.message} {...register("email")} />
        <Input label="Phone (optional)" placeholder="0901234567" error={errors.phone?.message} {...register("phone")} />

        <div style={{ display: "flex", flexDirection: "column", gap: "0.375rem" }}>
          <label style={{ fontSize: "0.875rem", fontWeight: 600 }}>Role</label>
          <select
            {...register("roleCode")}
            style={{
              padding: "0.625rem 0.75rem",
              borderRadius: "var(--radius-input, 8px)",
              border: "1px solid var(--color-border)",
              background: "var(--color-background)",
              color: "var(--color-text-primary)",
            }}
          >
            {ROLES.map((r) => (
              <option key={r} value={r}>
                {r}
              </option>
            ))}
          </select>
          {errors.roleCode && (
            <span style={{ color: "#ef4444", fontSize: "0.8125rem" }}>{errors.roleCode.message}</span>
          )}
        </div>

        {selectedRole === "SHIPPER" && (
          <Input
            label="Hub ID (required for SHIPPER)"
            placeholder="1"
            error={errors.hubId?.message}
            {...register("hubId")}
          />
        )}

        <Button type="submit" variant="primary" size="lg" loading={mutation.isPending}>
          Create Account
        </Button>
      </form>

      <div>
        <h2 style={{ fontSize: "1.25rem", fontWeight: 700, marginBottom: "0.75rem" }}>
          Existing accounts
        </h2>

        {usersQuery.isLoading ? (
          <LoadingState message="Loading accounts..." />
        ) : usersQuery.isError ? (
          <ErrorState title="Could not load accounts" onRetry={() => usersQuery.refetch()} />
        ) : (usersQuery.data?.data.content ?? []).length === 0 ? (
          <EmptyState icon="👥" title="No accounts yet" description="Created accounts appear here." />
        ) : (
          <div style={{ border: "1px solid var(--color-border)", borderRadius: "var(--radius-card)", backgroundColor: "var(--color-surface)", overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.875rem" }}>
              <thead>
                <tr style={{ textAlign: "left", color: "var(--color-text-secondary)" }}>
                  <th style={{ padding: "0.75rem 1rem" }}>Name</th>
                  <th style={{ padding: "0.75rem 1rem" }}>Email</th>
                  <th style={{ padding: "0.75rem 1rem" }}>Role</th>
                  <th style={{ padding: "0.75rem 1rem" }}>Hub</th>
                  <th style={{ padding: "0.75rem 1rem" }}>Status</th>
                </tr>
              </thead>
              <tbody>
                {(usersQuery.data?.data.content ?? []).map((u) => (
                  <tr key={u.id} style={{ borderTop: "1px solid var(--color-border)" }}>
                    <td style={{ padding: "0.75rem 1rem", fontWeight: 600 }}>
                      <Link href={`/users/${u.id}`} style={{ color: "var(--color-primary)" }}>
                        {u.fullName}
                      </Link>
                    </td>
                    <td style={{ padding: "0.75rem 1rem", color: "var(--color-text-secondary)" }}>{u.email}</td>
                    <td style={{ padding: "0.75rem 1rem" }}>{u.roleCode ?? "—"}</td>
                    <td style={{ padding: "0.75rem 1rem" }}>{u.hubId ?? "—"}</td>
                    <td style={{ padding: "0.75rem 1rem" }}>
                      {!u.active
                        ? `Locked${u.lockReason ? ` (${u.lockReason})` : ""}`
                        : u.mustChangePassword
                        ? "Awaiting password change"
                        : "Active"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
