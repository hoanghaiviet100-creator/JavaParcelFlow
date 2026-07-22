"use client";

import { useState } from "react";
import Link from "next/link";
import { useMutation } from "@tanstack/react-query";
import Input from "@/shared/components/Input";
import Button from "@/shared/components/Button";
import {
  getParcelByCodeApi,
  updateParcelStatusApi,
  ParcelResponse,
  ParcelStatus,
} from "@/features/parcels/api/parcels.api";
import { ApiError } from "@/shared/api/api-error";

const SCAN_STATUSES: ParcelStatus[] = [
  "RECEIVED_AT_ORIGIN_HUB", "ARRIVED_AT_HUB", "WAITING_FOR_OUTBOUND", "IN_TRANSIT",
  "READY_FOR_DELIVERY", "OUT_FOR_DELIVERY", "DELIVERED", "DELIVERY_FAILED",
];

export default function ParcelScanPage() {
  const [code, setCode] = useState("");
  const [status, setStatus] = useState<ParcelStatus>("ARRIVED_AT_HUB");
  const [hubId, setHubId] = useState("");
  const [found, setFound] = useState<ParcelResponse | null>(null);
  const [banner, setBanner] = useState<{ kind: "ok" | "err"; text: string } | null>(null);

  const lookup = useMutation({
    mutationFn: () => getParcelByCodeApi(code.trim()),
    onSuccess: (res) => {
      setFound(res.data);
      setStatus(res.data.status);
      setBanner(null);
    },
    onError: (err) => {
      setFound(null);
      setBanner({ kind: "err", text: err instanceof ApiError ? err.message : "Parcel not found." });
    },
  });

  const submit = useMutation({
    mutationFn: () => {
      if (!found) throw new Error("No parcel loaded");
      return updateParcelStatusApi(found.id, {
        status,
        hubId: hubId ? Number(hubId) : undefined,
      });
    },
    onSuccess: (res) => {
      setFound(res.data);
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
          <div style={{ display: "flex", flexDirection: "column", gap: "0.375rem" }}>
            <label style={{ fontSize: "0.875rem", fontWeight: 600 }}>Scan Event / New Status</label>
            <select
              value={status}
              onChange={(e) => setStatus(e.target.value as ParcelStatus)}
              style={{ padding: "0.625rem 0.75rem", borderRadius: "var(--radius-input, 8px)", border: "1px solid var(--color-border)", background: "var(--color-background)", color: "var(--color-text-primary)" }}
            >
              {SCAN_STATUSES.map((s) => (
                <option key={s} value={s}>{s}</option>
              ))}
            </select>
          </div>
          <Input type="number" label="Hub ID (optional)" value={hubId} onChange={(e) => setHubId(e.target.value)} />
          <Button type="button" variant="primary" loading={submit.isPending} onClick={() => submit.mutate()}>
            Submit Scan Event
          </Button>
        </div>
      )}
    </div>
  );
}
