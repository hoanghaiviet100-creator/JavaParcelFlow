"use client";

import { useState } from "react";
import Link from "next/link";
import { useMutation, useQuery } from "@tanstack/react-query";
import Input from "@/shared/components/Input";
import Button from "@/shared/components/Button";
import {
  getParcelByCodeApi,
  getParcelTransitionsApi,
  updateParcelStatusApi,
  ParcelResponse,
  ParcelStatus,
} from "@/features/parcels/api/parcels.api";
import { ApiError } from "@/shared/api/api-error";

export default function ParcelScanPage() {
  const [code, setCode] = useState("");
  // No pre-selected status: the operator picks the event deliberately.
  const [status, setStatus] = useState<ParcelStatus | null>(null);
  const [hubId, setHubId] = useState("");
  const [found, setFound] = useState<ParcelResponse | null>(null);
  const [banner, setBanner] = useState<{ kind: "ok" | "err"; text: string } | null>(null);

  const lookup = useMutation({
    mutationFn: () => getParcelByCodeApi(code.trim()),
    onSuccess: (res) => {
      setFound(res.data);
      setStatus(null);
      setBanner(null);
    },
    onError: (err) => {
      setFound(null);
      setBanner({ kind: "err", text: err instanceof ApiError ? err.message : "Parcel not found." });
    },
  });

  // Which scan events this parcel will actually accept, per the server's state
  // machine. Re-keyed on the current status so it refreshes after each scan.
  const transitionsQuery = useQuery({
    queryKey: ["parcel-transitions", found?.id, found?.status],
    queryFn: () => getParcelTransitionsApi(found!.id),
    enabled: !!found,
    retry: false,
  });

  const allowed = transitionsQuery.data?.data.allowed ?? [];
  const corrections = transitionsQuery.data?.data.corrections ?? [];
  const isCorrection = status !== null && corrections.includes(status);

  const submit = useMutation({
    mutationFn: () => {
      if (!found) throw new Error("No parcel loaded");
      if (!status) throw new Error("No status selected");
      return updateParcelStatusApi(found.id, {
        status,
        hubId: hubId ? Number(hubId) : undefined,
      });
    },
    onSuccess: (res) => {
      setFound(res.data);
      setStatus(null);
      setBanner({ kind: "ok", text: `Scan recorded — ${res.data.parcelCode} is now ${res.data.status}.` });
    },
    onError: (err) => {
      setBanner({ kind: "err", text: err instanceof ApiError ? err.message : "Scan update failed." });
    },
  });

  return (
    <div style={{ maxWidth: "600px", display: "flex", flexDirection: "column", gap: "1.5rem" }}>
      <div>
        <h1 style={{ fontFamily: "var(--font-be-vietnam-pro)", fontSize: "2rem", fontWeight: 800, letterSpacing: "-0.03em" }}>
          Terminal Scan Entry
        </h1>
        <p style={{ color: "var(--color-text-secondary)", fontSize: "0.875rem" }}>
          Enter a parcel code to load it, then register the inbound/outbound custody event.
        </p>
      </div>

      {banner && (
        <div style={{ padding: "0.75rem 1rem", borderRadius: "var(--radius-card)", background: banner.kind === "ok" ? "rgba(16,185,129,0.1)" : "rgba(239,68,68,0.1)", color: banner.kind === "ok" ? "#065f46" : "#991b1b", fontSize: "0.875rem" }}>
          {banner.text}
        </div>
      )}

      <form
        onSubmit={(e) => { e.preventDefault(); if (code.trim()) lookup.mutate(); }}
        style={{ display: "flex", flexDirection: "column", gap: "1rem" }}
      >
        <Input
          type="text"
          label="Barcode Content / Parcel Code"
          placeholder="e.g. PAR-1002495-01"
          value={code}
          onChange={(e) => setCode(e.target.value)}
        />
        <Button type="submit" variant="secondary" loading={lookup.isPending}>
          Look up parcel
        </Button>
      </form>

      {found && (
        <div style={{ border: "1px solid var(--color-border)", borderRadius: "var(--radius-card)", padding: "1.5rem", backgroundColor: "var(--color-surface)", display: "flex", flexDirection: "column", gap: "1rem" }}>
          <div style={{ fontSize: "0.875rem" }}>
            <strong>{found.parcelCode}</strong> — currently{" "}
            <span style={{ color: "var(--color-primary)" }}>{found.status}</span>{" "}
            (<Link href={`/parcels/${found.id}`} style={{ color: "var(--color-primary)" }}>details</Link>)
          </div>
          {allowed.length === 0 && corrections.length === 0 && !transitionsQuery.isLoading ? (
            <p style={{ fontSize: "0.875rem", color: "var(--color-text-secondary)" }}>
              <strong>{found.status}</strong> is a final status — no further scan is possible
              here. A supervisor can correct it from the parcel details page.
            </p>
          ) : (
            <>
              <div style={{ display: "flex", flexDirection: "column", gap: "0.375rem" }}>
                <label style={{ fontSize: "0.875rem", fontWeight: 600 }}>Scan Event / New Status</label>
                <select
                  value={status ?? ""}
                  disabled={transitionsQuery.isLoading}
                  onChange={(e) => setStatus(e.target.value ? (e.target.value as ParcelStatus) : null)}
                  style={{ padding: "0.625rem 0.75rem", borderRadius: "var(--radius-input, 8px)", border: "1px solid var(--color-border)", background: "var(--color-background)", color: "var(--color-text-primary)" }}
                >
                  <option value="">
                    {transitionsQuery.isLoading ? "Loading scan events..." : "Select a scan event..."}
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
                  This reverses a final status and is logged as a correction.
                </div>
              )}

              <Input type="number" label="Hub ID (optional)" value={hubId} onChange={(e) => setHubId(e.target.value)} />
              <Button type="button" variant="primary" disabled={!status} loading={submit.isPending} onClick={() => submit.mutate()}>
                Submit Scan Event
              </Button>
            </>
          )}
        </div>
      )}
    </div>
  );
}
