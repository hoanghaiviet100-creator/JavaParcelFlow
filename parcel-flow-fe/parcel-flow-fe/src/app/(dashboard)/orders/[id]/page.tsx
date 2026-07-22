"use client";

import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { getOrderApi, getOrderTrackingEventsApi } from "@/features/orders/api/orders.api";
import LoadingState from "@/shared/components/LoadingState";
import ErrorState from "@/shared/components/ErrorState";

export default function OrderDetailPage() {
  const params = useParams();
  const id = String(params.id);

  const orderQuery = useQuery({
    queryKey: ["order", id],
    queryFn: () => getOrderApi(id),
    enabled: !!id,
    retry: false,
  });

  const eventsQuery = useQuery({
    queryKey: ["order-events", id],
    queryFn: () => getOrderTrackingEventsApi(id),
    enabled: !!id,
    retry: false,
  });

  const order = orderQuery.data?.data;
  const events = eventsQuery.data?.data ?? [];

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
      <div>
        <Link href="/dashboard/orders" style={{ color: "var(--color-primary)", fontSize: "0.875rem" }}>
          ← Back to Orders
        </Link>
        <h1 style={{ fontFamily: "var(--font-be-vietnam-pro)", fontSize: "2rem", fontWeight: 800, letterSpacing: "-0.03em", marginTop: "0.5rem" }}>
          Order Manifest Details
        </h1>
        <p style={{ color: "var(--color-text-secondary)", fontSize: "0.875rem" }}>
          {order ? `${order.orderCode} — ${order.status}` : `Viewing order ID: ${id}`}
        </p>
      </div>

      {orderQuery.isLoading ? (
        <LoadingState message="Loading order..." />
      ) : orderQuery.isError || !order ? (
        <ErrorState title="Could not load this order" onRetry={() => orderQuery.refetch()} />
      ) : (
        <>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "1.5rem" }}>
            <Card title="Sender">
              <PartyBlock
                name={order.sender.fullName}
                phone={order.sender.phone}
                address={order.sender.addressLine}
              />
            </Card>
            <Card title="Receiver">
              <PartyBlock
                name={order.receiver.fullName}
                phone={order.receiver.phone}
                address={order.receiver.addressLine}
              />
            </Card>
          </div>

          <Card title={`Parcels (${order.parcels.length})`}>
            {order.parcels.length === 0 ? (
              <p style={{ color: "var(--color-text-secondary)" }}>No parcels registered.</p>
            ) : (
              <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.875rem" }}>
                <thead>
                  <tr style={{ textAlign: "left", color: "var(--color-text-secondary)" }}>
                    <th style={{ padding: "0.5rem 0.75rem" }}>Code</th>
                    <th style={{ padding: "0.5rem 0.75rem" }}>Weight (kg)</th>
                    <th style={{ padding: "0.5rem 0.75rem" }}>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {order.parcels.map((p) => (
                    <tr key={p.id} style={{ borderTop: "1px solid var(--color-border)" }}>
                      <td style={{ padding: "0.5rem 0.75rem", fontWeight: 600 }}>
                        <Link href={`/dashboard/parcels/${p.id}`} style={{ color: "var(--color-primary)" }}>
                          {p.parcelCode}
                        </Link>
                      </td>
                      <td style={{ padding: "0.5rem 0.75rem" }}>{p.weight}</td>
                      <td style={{ padding: "0.5rem 0.75rem" }}>{p.status}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </Card>

          <Card title="Tracking Timeline">
            {eventsQuery.isLoading ? (
              <LoadingState message="Loading timeline..." />
            ) : events.length === 0 ? (
              <p style={{ color: "var(--color-text-secondary)" }}>No tracking events yet.</p>
            ) : (
              <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
                {events.map((e) => (
                  <div key={e.id} style={{ borderLeft: "2px solid var(--color-primary)", paddingLeft: "0.75rem" }}>
                    <div style={{ fontWeight: 600 }}>{e.title || e.status}</div>
                    <div style={{ fontSize: "0.8125rem", color: "var(--color-text-secondary)" }}>{e.message}</div>
                    <div style={{ fontSize: "0.75rem", color: "var(--color-text-secondary)" }}>
                      {new Date(e.createdAt).toLocaleString()}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </Card>
        </>
      )}
    </div>
  );
}

function Card({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div
      style={{
        border: "1px solid var(--color-border)",
        borderRadius: "var(--radius-card)",
        padding: "1.5rem",
        backgroundColor: "var(--color-surface)",
      }}
    >
      <h3 style={{ fontSize: "1rem", fontWeight: 700, marginBottom: "0.75rem" }}>{title}</h3>
      {children}
    </div>
  );
}

function PartyBlock({ name, phone, address }: { name: string; phone: string; address: string }) {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "0.25rem", fontSize: "0.875rem" }}>
      <span style={{ fontWeight: 600 }}>{name}</span>
      <span style={{ color: "var(--color-text-secondary)" }}>{phone}</span>
      <span style={{ color: "var(--color-text-secondary)" }}>{address}</span>
    </div>
  );
}
