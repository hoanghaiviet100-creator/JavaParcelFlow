"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  getParcelApi,
  getParcelTransitionsApi,
  updateParcelStatusApi,
  ParcelStatus,
} from "@/features/parcels/api/parcels.api";
import Button from "@/shared/components/Button";
import Input from "@/shared/components/Input";
import LoadingState from "@/shared/components/LoadingState";
import ErrorState from "@/shared/components/ErrorState";
import { ApiError } from "@/shared/api/api-error";

export default function ParcelDetailPage() {
  const params = useParams();
  const id = String(params.id);
  const queryClient = useQueryClient();

  // `null` means "nothing picked yet". There is deliberately no default: the old
  // screen pre-selected the parcel's current status inside a list of all 16, which
  // made an accidental one-click jump to CANCELLED far too easy.
  const [status, setStatus] = useState<ParcelStatus | null>(null);
  const [hubId, setHubId] = useState("");
  const [note, setNote] = useState("");
  const [banner, setBanner] = useState<{ kind: "ok" | "err"; text: string } | null>(null);

  const parcelQuery = useQuery({
    queryKey: ["parcel", id],
    queryFn: () => getParcelApi(id),
    enabled: !!id,
    retry: false,
  });

  // The server owns the state machine; we only render what it says is reachable.
  const transitionsQuery = useQuery({
    queryKey: ["parcel-transitions", id],
    queryFn: () => getParcelTransitionsApi(id),
    enabled: !!id,
    retry: false,
  });

  const parcel = parcelQuery.data?.data;
  const allowed = transitionsQuery.data?.data.allowed ?? [];
  const corrections = transitionsQuery.data?.data.corrections ?? [];
  const isCorrection = status !== null && corrections.includes(status);
  const isFinal = !transitionsQuery.isLoading && allowed.length === 0 && corrections.length === 0;

  const mutation = useMutation({
    mutationFn: () => {
      if (!status) throw new Error("No status selected");
      return updateParcelStatusApi(id, {
        status,
        hubId: hubId ? Number(hubId) : undefined,
        note: note || undefined,
      });
    },
    onSuccess: (res) => {
      setBanner({ kind: "ok", text: `Parcel is now ${res.data.status}.` });
      setStatus(null);
      setNote("");
      queryClient.invalidateQueries({ queryKey: ["parcel", id] });
      // The next legal steps change with the status, so this must refetch too.
      queryClient.invalidateQueries({ queryKey: ["parcel-transitions", id] });
    },
    onError: (err) => {
      setBanner({ kind: "err", text: err instanceof ApiError ? err.message : "Update failed." });
    },
  });

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem", maxWidth: 640 }}>
      <div>
        <Link href="/parcels" style={{ color: "var(--color-primary)", fontSize: "0.875rem" }}>
          ← Back to Parcels
        </Link>
        <h1 style={{ fontFamily: "var(--font-be-vietnam-pro)", fontSize: "2rem", fontWeight: 800, letterSpacing: "-0.03em", marginTop: "0.5rem" }}>
          Parcel Details
        </h1>
      </div>

      {parcelQuery.isLoading ? (
        <LoadingState message="Loading parcel..." />
      ) : parcelQuery.isError || !parcel ? (
        <ErrorState title="Could not load this parcel" onRetry={() => parcelQuery.refetch()} />
      ) : (
        <>
          <div style={{ border: "1px solid var(--color-border)", borderRadius: "var(--radius-card)", padding: "1.5rem", backgroundColor: "var(--color-surface)", display: "flex", flexDirection: "column", gap: "0.5rem", fontSize: "0.875rem" }}>
            <Row label="Code" value={parcel.parcelCode} />
            <Row label="Weight" value={`${parcel.weight} kg`} />
            <Row label="Current Status" value={parcel.status} />
          </div>

          {banner && (
            <div style={{ padding: "0.75rem 1rem", borderRadius: "var(--radius-card)", background: banner.kind === "ok" ? "rgba(16,185,129,0.1)" : "rgba(239,68,68,0.1)", color: banner.kind === "ok" ? "#065f46" : "#991b1b", fontSize: "0.875rem" }}>
              {banner.text}
            </div>
          )}

          <div style={{ border: "1px solid var(--color-border)", borderRadius: "var(--radius-card)", padding: "1.5rem", backgroundColor: "var(--color-surface)", display: "flex", flexDirection: "column", gap: "1rem" }}>
            <h3 style={{ fontSize: "1rem", fontWeight: 700 }}>Update Status</h3>

            {isFinal ? (
              <p style={{ fontSize: "0.875rem", color: "var(--color-text-secondary)" }}>
                <strong>{parcel.status}</strong> is a final status and no further scan is
                possible. An ADMIN or HUB_MANAGER can still correct it if it was scanned
                by mistake.
              </p>
            ) : (
              <>
                <div style={{ display: "flex", flexDirection: "column", gap: "0.375rem" }}>
                  <label style={{ fontSize: "0.875rem", fontWeight: 600 }}>New Status</label>
                  <select
                    value={status ?? ""}
                    disabled={transitionsQuery.isLoading}
                    onChange={(e) => setStatus(e.target.value ? (e.target.value as ParcelStatus) : null)}
                    style={{ padding: "0.625rem 0.75rem", borderRadius: "var(--radius-input, 8px)", border: "1px solid var(--color-border)", background: "var(--color-background)", color: "var(--color-text-primary)" }}
                  >
                    <option value="">
                      {transitionsQuery.isLoading ? "Loading next steps..." : "Select a scan event..."}
                    </option>
                    {allowed.length > 0 && (
                      <optgroup label="Next step">
                        {allowed.map((s) => (
                          <option key={s} value={s}>{s}</option>
                        ))}
                      </optgroup>
                    )}
                    {corrections.length > 0 && (
                      <optgroup label="Correction (supervisor)">
                        {corrections.map((s) => (
                          <option key={s} value={s}>{s}</option>
                        ))}
                      </optgroup>
                    )}
                  </select>
                </div>

                {isCorrection && (
                  <div style={{ padding: "0.75rem 1rem", borderRadius: "var(--radius-card)", background: "rgba(245,158,11,0.12)", color: "#92400e", fontSize: "0.875rem" }}>
                    This reverses a final status. It is recorded in the custody log and on
                    the customer timeline as a correction — leave a note saying why.
                  </div>
                )}

                <Input type="number" label="Hub ID (optional)" value={hubId} onChange={(e) => setHubId(e.target.value)} />
                <Input type="text" label="Note (optional)" value={note} onChange={(e) => setNote(e.target.value)} />
                <Button type="button" variant="primary" disabled={!status} loading={mutation.isPending} onClick={() => { setBanner(null); mutation.mutate(); }}>
                  Submit Update
                </Button>
              </>
            )}
          </div>
        </>
      )}
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ display: "flex", justifyContent: "space-between" }}>
      <span style={{ color: "var(--color-text-secondary)" }}>{label}</span>
      <span style={{ fontWeight: 600 }}>{value}</span>
    </div>
  );
}
