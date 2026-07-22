"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useAppDispatch, useAppSelector } from "@/store/store";
import { loginSuccess, logoutSuccess, setLoading } from "@/store/slices/authSlice";
import { loginApi } from "../api/login.api";
import { logoutApi } from "../api/logout.api";
import { getProfileApi } from "../api/get-profile.api";
import { LoginRequest, principalToProfile } from "../types/auth.types";
import { tokenStore } from "@/shared/api/token-store";
import { ApiError } from "@/shared/api/api-error";

export default function useAuth() {
  const router = useRouter();
  const dispatch = useAppDispatch();
  const queryClient = useQueryClient();

  const { user, role, permissions, isAuthenticated, isLoading } = useAppSelector(
    (state) => state.auth
  );

  const loginMutation = useMutation({
    mutationFn: async (payload: LoginRequest) => {
      dispatch(setLoading(true));
      // 1. Exchange credentials for JWTs.
      const res = await loginApi(payload);
      const { accessToken, refreshToken } = res.data;
      tokenStore.set(accessToken, refreshToken);
      // 2. Fetch the full principal from /me now that we're authenticated.
      const profileRes = await getProfileApi();
      return principalToProfile(profileRes.data);
    },
    onSuccess: (profile) => {
      dispatch(loginSuccess(profile));
      queryClient.invalidateQueries({ queryKey: ["auth-profile"] });

      if (profile.role === "SHIPPER") {
        router.push("/shipper/assignments");
      } else {
        router.push("/dashboard");
      }
    },
    onError: (error) => {
      dispatch(setLoading(false));
      tokenStore.clear();
      // If the backend says the temp password must be changed, route to that page.
      if (error instanceof ApiError && error.code === "AUTH_PASSWORD_CHANGE_REQUIRED") {
        router.push("/change-password");
      }
    },
  });

  const logoutMutation = useMutation({
    mutationFn: async () => {
      dispatch(setLoading(true));
      try {
        await logoutApi();
      } catch {
        // Best-effort server revoke; local cleanup happens regardless.
      }
    },
    onSuccess: () => finishLogout(),
    onError: () => finishLogout(),
  });

  const finishLogout = () => {
    tokenStore.clear();
    dispatch(logoutSuccess());
    queryClient.clear();
    router.push("/login");
  };

  return {
    user,
    role,
    permissions,
    isAuthenticated,
    isLoading,
    login: loginMutation.mutateAsync,
    logout: logoutMutation.mutateAsync,
    isLoggingIn: loginMutation.isPending,
    isLoggingOut: logoutMutation.isPending,
    loginError:
      loginMutation.error instanceof ApiError
        ? loginMutation.error.message
        : loginMutation.error
        ? "Login failed. Please try again."
        : null,
    loginErrorCode:
      loginMutation.error instanceof ApiError ? loginMutation.error.code : undefined,
  };
}
