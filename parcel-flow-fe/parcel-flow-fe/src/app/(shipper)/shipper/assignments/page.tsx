"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import {
  getMyAssignmentsApi,
  updateAssignmentStatusApi,
  DeliveryAssignmentStatus,
} from "@/features/shipper/api/shipper.api";
import LoadingState from "@/shared/components/LoadingState";
import ErrorState from "@/shared/components/ErrorState";
import EmptyState from "@/shared/components/EmptyState";
import Button from "@/shared/components/Button";

/** Which status a shipper can move an assignment to from its current status. */
const NEXT_ACTIONS: Partial<Record<DeliveryAssignmentStatus, DeliveryAssignmentStatus[]>> = {
  ASSIGNED: ["ACCEPTED"],
  ACCEPTED: ["PICKED_UP"],
  PICKED_UP: ["OUT_FOR_DELIVERY"],
  OUT_FOR_DELIVERY: ["DELIVERED", "FAILED"],
};

export default function ShipperAssignmentsPage() {
  const queryClient = useQueryClient();

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ["shipper-assignments"],
    queryFn: getMyAssignmentsApi,
    retry: false,
  });

  const mutation = useMutation({
    mutationFn: ({ id, status }: { id: number; status: DeliveryAssignmentStatus }) =>
      updateAssignmentStatusApi(id, status),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["shipper-assignments"] }),
  });

  const assignments = data?.data ?? [];

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
      <div>
        <h1 style={{ fontFamily: "var(--font-be-vietnam-pro)", fontSize: "1.75rem", fontWeight: 800, letterSpacing: "-0.03em" }}>
          My Courier Tasks
        </h1>
        <p style={{ color: "var(--color-text-secondary)", fontSize: "0.875rem" }}>
          List of parcel packages assigned to your route for final delivery.
        </p>
      </div>

      {isLoading ? (
        <LoadingState message="Loading your tasks..." />
      ) : isError ? (
        <ErrorState title="Could not load your assignments" onRetry={() => refetch()} />
      ) : assignments.length === 0 ? (
        <EmptyState icon="🛵" title="No active tasks" description="Assignments routed to you will appear here." />
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
          {assignments.map((a) => (
            <div key={a.id} style={{ border: "1px solid var(--color-border)", borderRadius: "var(--radius-card)", padding: "1.25rem", backgroundColor: "var(--color-surface)", display: "flex", justifyContent: "space-between", alignItems: "center", gap: "1rem", flexWrap: "wrap" }}>
              <div style={{ display: "flex", flexDirection: "column", gap: "0.25rem" }}>
                <span style={{ fontWeight: 700 }}>
                  <Link href={`/dashboard/parcels/${a.parcelId}`} style={{ color: "var(--color-primary)" }}>
                    {a.parcelCode ?? `Parcel #${a.parcelId}`}
                  </Link>
                </span>
                <span style={{ fontSize: "0.8125rem", color: "var(--color-text-secondary)" }}>
                  Assignment status: {a.status}
                  {a.parcelStatus ? ` · Parcel: ${a.parcelStatus}` : ""}
                </span>
              </div>
              <div style={{ display: "flex", gap: "0.5rem" }}>
                {(NEXT_ACTIONS[a.status] ?? []).map((next) => (
                  <Button
                    key={next}
                    type="button"
                    variant={next === "FAILED" ? "danger" : "primary"}
                    size="sm"
                    loading={mutation.isPending && mutation.variables?.id === a.id && mutation.variables?.status === next}
                    onClick={() => mutation.mutate({ id: a.id, status: next })}
                  >
                    {labelFor(next)}
                  </Button>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function labelFor(status: DeliveryAssignmentStatus): string {
  switch (status) {
    case "ACCEPTED": return "Accept";
    case "PICKED_UP": return "Mark Picked Up";
    case "OUT_FOR_DELIVERY": return "Out for Delivery";
    case "DELIVERED": return "Mark Delivered";
    case "FAILED": return "Mark Failed";
    default: return status;
  }
}
