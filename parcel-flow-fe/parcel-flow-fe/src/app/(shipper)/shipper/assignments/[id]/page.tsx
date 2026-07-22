interface PageProps {
  params: Promise<{ id: string }>;
}

export default async function ShipperAssignmentDetailPage({ params }: PageProps) {
  const { id } = await params;

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
      <div>
        <h1 style={{ fontFamily: "var(--font-be-vietnam-pro)", fontSize: "1.75rem", fontWeight: 800, letterSpacing: "-0.03em" }}>
          Courier Task Detail
        </h1>
        <p style={{ color: "var(--color-text-secondary)", fontSize: "0.875rem" }}>
          Viewing delivery details for Task ID: {id}
        </p>
      </div>

      <div style={{ 
        border: "1px solid var(--color-border)", 
        borderRadius: "var(--radius-card)", 
        padding: "1.5rem", 
        backgroundColor: "var(--color-surface)",
        display: "flex",
        flexDirection: "column",
        gap: "1rem"
      }}>
        <h3 style={{ fontSize: "1rem", fontWeight: 700 }}>Recipient Information</h3>
        <p style={{ color: "var(--color-text-secondary)", fontSize: "0.875rem" }}>
          Receiver info details, delivery location logs, address fields, and handoff action options will load here.
        </p>
      </div>
    </div>
  );
}
