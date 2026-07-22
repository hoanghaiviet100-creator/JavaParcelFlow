"use client";

import { useEffect } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import Input from "@/shared/components/Input";
import Button from "@/shared/components/Button";
import { loginSchema, LoginSchemaType } from "@/features/auth/schemas/login.schema";
import useAuth from "@/features/auth/hooks/useAuth";
import styles from "./login.module.scss";

export default function LoginPage() {
  const router = useRouter();
  const { login, isLoggingIn, loginError, loginErrorCode, isAuthenticated, role, isLoading } =
    useAuth();

  useEffect(() => {
    if (isAuthenticated && !isLoading) {
      router.push(role === "SHIPPER" ? "/shipper/assignments" : "/dashboard");
    }
  }, [isAuthenticated, role, isLoading, router]);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginSchemaType>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  });

  const onSubmit = async (data: LoginSchemaType) => {
    try {
      await login(data);
    } catch {
      // errors surface via loginError / redirect handled in useAuth
    }
  };

  const mustChangePassword = loginErrorCode === "AUTH_PASSWORD_CHANGE_REQUIRED";
  const permanentlyLocked = loginErrorCode === "AUTH_ACCOUNT_PERMANENTLY_LOCKED";

  return (
    <div style={{ width: "100%" }}>
      <div style={{ textAlign: "center" }}>
        <h1 className={styles.title}>Welcome Back</h1>
        <p className={styles.subtitle}>Sign in to your staff or dispatcher account</p>
      </div>

      {loginError && (
        <div className={styles.errorAlert}>
          <span>⚠️</span>
          <span>{loginError}</span>
        </div>
      )}

      {mustChangePassword && (
        <div className={styles.errorAlert}>
          <span>🔑</span>
          <span>
            You must set a new password.{" "}
            <Link href="/change-password">Change password</Link>
          </span>
        </div>
      )}

      {permanentlyLocked && (
        <div className={styles.errorAlert}>
          <span>🔒</span>
          <span>
            This account is locked. <Link href="/locked">Learn more</Link>
          </span>
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
          label="Password"
          placeholder="••••••••"
          error={errors.password?.message}
          {...register("password")}
        />

        <Button
          type="submit"
          variant="primary"
          size="lg"
          loading={isLoggingIn}
          style={{ marginTop: "0.5rem", boxShadow: "0 4px 12px var(--color-glow)" }}
        >
          Sign In
        </Button>
      </form>

      <p className={styles.subtitle} style={{ textAlign: "center", marginTop: "1rem" }}>
        First time logging in with a temporary password?{" "}
        <Link href="/change-password">Set a new password</Link>
      </p>
    </div>
  );
}
