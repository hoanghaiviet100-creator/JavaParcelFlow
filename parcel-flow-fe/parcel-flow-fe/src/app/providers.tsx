"use client";

import { ReactNode, useEffect, useState } from "react";
import { Provider } from "react-redux";
import { store, useAppSelector } from "@/store/store";
import { QueryClientProvider } from "@tanstack/react-query";
import { queryClient } from "@/shared/lib/query-client";
import { env } from "@/config/env";

function ThemeInitializer({ children }: { children: ReactNode }) {
  const themeMode = useAppSelector((state) => state.theme.mode);

  useEffect(() => {
    const root = document.documentElement;
    const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
    
    const applyTheme = () => {
      if (themeMode === "system") {
        const systemTheme = mediaQuery.matches ? "dark" : "light";
        root.setAttribute("data-theme", systemTheme);
      } else {
        root.setAttribute("data-theme", themeMode);
      }
    };

    applyTheme();

    if (themeMode === "system") {
      mediaQuery.addEventListener("change", applyTheme);
      return () => mediaQuery.removeEventListener("change", applyTheme);
    }
  }, [themeMode]);

  return <>{children}</>;
}

function MSWInitializer({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(!env.enableMSW);

  useEffect(() => {
    if (!env.enableMSW) {
      return;
    }

    const startMocks = async () => {
      try {
        const { initMocks } = await import("../mocks/browser");
        await initMocks();
        setReady(true);
      } catch (err) {
        console.error("Failed to initialize MSW mock worker:", err);
        setReady(true);
      }
    };

    startMocks();
  }, []);

  if (!ready) {
    return (
      <div 
        style={{ 
          display: "flex", 
          height: "100vh", 
          width: "100vw",
          alignItems: "center", 
          justifyContent: "center",
          fontFamily: "sans-serif",
          backgroundColor: "var(--color-background, #F8FAFC)",
          color: "var(--color-text-primary, #0F172A)"
        }}
      >
        <div style={{ textAlign: "center" }}>
          <div style={{ marginBottom: "1rem", fontWeight: 600 }}>Parcel Flow</div>
          <div style={{ fontSize: "0.875rem", color: "var(--color-text-secondary, #475569)" }}>
            Initializing environment mocks...
          </div>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}

import AuthProvider from "@/features/auth/components/AuthProvider";

export function Providers({ children }: { children: ReactNode }) {
  return (
    <Provider store={store}>
      <ThemeInitializer>
        <QueryClientProvider client={queryClient}>
          <MSWInitializer>
            <AuthProvider>{children}</AuthProvider>
          </MSWInitializer>
        </QueryClientProvider>
      </ThemeInitializer>
    </Provider>
  );
}
