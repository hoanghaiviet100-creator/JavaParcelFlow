"use client";

import { ReactNode, useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import { useAppDispatch } from "@/store/store";
import { loginSuccess, logoutSuccess, setLoading } from "@/store/slices/authSlice";
import { getProfileApi } from "../api/get-profile.api";
import { principalToProfile } from "../types/auth.types";
import { tokenStore } from "@/shared/api/token-store";
import LoadingState from "@/shared/components/LoadingState";

interface AuthProviderProps {
  children: ReactNode;
}

export default function AuthProvider({ children }: AuthProviderProps) {
  const dispatch = useAppDispatch();
  const hasToken = typeof window !== "undefined" && tokenStore.hasSession();

  const { data, isLoading, isError } = useQuery({
    queryKey: ["auth-profile"],
    queryFn: getProfileApi,
    // Only hit /me when we actually hold a token; otherwise there's no session to verify.
    enabled: hasToken,
    retry: false,
    staleTime: 1000 * 60 * 15,
  });

  useEffect(() => {
    dispatch(setLoading(hasToken && isLoading));
  }, [hasToken, isLoading, dispatch]);

  useEffect(() => {
    if (data?.success && data.data) {
      dispatch(loginSuccess(principalToProfile(data.data)));
    } else if (isError) {
      tokenStore.clear();
      dispatch(logoutSuccess());
    }
  }, [data, isError, dispatch]);

  if (hasToken && isLoading) {
    return <LoadingState message="Verifying security credentials..." fullPage />;
  }

  return <>{children}</>;
}
