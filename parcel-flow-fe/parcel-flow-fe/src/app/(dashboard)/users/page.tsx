"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import useRoleGuard from "@/shared/hooks/useRoleGuard";
import LoadingState from "@/shared/components/LoadingState";
import Input from "@/shared/components/Input";
import Button from "@/shared/components/Button";
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
    <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem", maxWidth: 640 }}>
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
    </div>
  );
}
