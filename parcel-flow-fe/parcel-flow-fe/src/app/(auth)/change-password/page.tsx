"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import Input from "@/shared/components/Input";
import Button from "@/shared/components/Button";
import {
  changePasswordSchema,
  ChangePasswordSchemaType,
} from "@/features/auth/schemas/change-password.schema";
import { changePasswordApi } from "@/features/auth/api/change-password.api";
import { ApiError } from "@/shared/api/api-error";
import styles from "../login/login.module.scss";

export default function ChangePasswordPage() {
  const router = useRouter();
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ChangePasswordSchemaType>({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: { email: "", currentPassword: "", newPassword: "", confirmPassword: "" },
  });

  const mutation = useMutation({
    mutationFn: (data: ChangePasswordSchemaType) =>
      changePasswordApi({
        email: data.email,
        currentPassword: data.currentPassword,
        newPassword: data.newPassword,
      }),
    onSuccess: () => {
      router.push("/login?changed=1");
    },
    onError: (err) => {
      setServerError(
        err instanceof ApiError ? err.message : "Could not change password. Please try again."
      );
    },
  });

  const onSubmit = (data: ChangePasswordSchemaType) => {
    setServerError(null);
    mutation.mutate(data);
  };

  return (
    <div style={{ width: "100%" }}>
      <div style={{ textAlign: "center" }}>
        <h1 className={styles.title}>Change Your Password</h1>
        <p className={styles.subtitle}>
          Enter your temporary password and choose a new strong password.
        </p>
      </div>

      {serverError && (
        <div className={styles.errorAlert}>
          <span>⚠️</span>
          <span>{serverError}</span>
        </div>
      )}

      <form onSubmit={handleSubmit(onSubmit)} className={styles.form}>
        <Input
          type="email"
          label="Email Address"
          placeholder="name@parcelflow.com"
          error={errors.email?.message}
          {...register("email")}
        />
        <Input
          type="password"
          label="Temporary / Current Password"
          placeholder="••••••••"
          error={errors.currentPassword?.message}
          {...register("currentPassword")}
        />
        <Input
          type="password"
          label="New Password"
          placeholder="••••••••"
          error={errors.newPassword?.message}
          {...register("newPassword")}
        />
        <Input
          type="password"
          label="Confirm New Password"
          placeholder="••••••••"
          error={errors.confirmPassword?.message}
          {...register("confirmPassword")}
        />

        <Button type="submit" variant="primary" size="lg" loading={mutation.isPending}>
          Update Password
        </Button>
      </form>

      <p className={styles.subtitle} style={{ textAlign: "center", marginTop: "1rem" }}>
        <Link href="/login">Back to sign in</Link>
      </p>
    </div>
  );
}
