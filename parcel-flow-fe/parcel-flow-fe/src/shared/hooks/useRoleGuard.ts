"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import useAuth from "@/features/auth/hooks/useAuth";
import { UserRole } from "@/config/permissions";

/**
 * A hook to protect specific page routes by redirecting unauthorized roles.
 * @param allowedRoles Array of roles permitted to view the page.
 * @param redirectTo Optional redirect path (defaults to /dashboard).
 */
export default function useRoleGuard(allowedRoles: UserRole[], redirectTo: string = "/dashboard") {
  const router = useRouter();
  const { isAuthenticated, role, isLoading } = useAuth();

  useEffect(() => {
    if (!isLoading) {
      if (!isAuthenticated) {
        router.push("/login");
      } else if (role && !allowedRoles.includes(role)) {
        console.warn(`Access denied. Role ${role} is not in allowed roles:`, allowedRoles);
        router.push(redirectTo);
      }
    }
  }, [isAuthenticated, role, isLoading, allowedRoles, redirectTo, router]);

  return { 
    isAuthorized: isAuthenticated && role !== null && allowedRoles.includes(role), 
    isLoading 
  };
}
