import { QueryClient } from "@tanstack/react-query";

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: (failureCount, error: unknown) => {
        // Only retry for network issues or 5xx server errors, not for 4xx authorization/validation failures
        const err = error as { status?: number } | null | undefined;
        if (err?.status && err.status >= 400 && err.status < 500) {
          return false;
        }
        return failureCount < 2;
      },
      staleTime: 1000 * 60 * 5, // 5 minutes default cache stale time
    },
  },
});
