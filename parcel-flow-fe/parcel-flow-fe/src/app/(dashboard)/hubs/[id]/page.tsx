interface PageProps {
  params: Promise<{ id: string }>;
}

export default async function HubDetailPage({ params }: PageProps) {
  const { id } = await params;

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
      <div>
        <h1 style={{ fontFamily: "var(--font-be-vietnam-pro)", fontSize: "2rem", fontWeight: 800, letterSpacing: "-0.03em" }}>
          Hub Operational Details
        </h1>
        <p style={{ color: "var(--color-text-secondary)", fontSize: "0.875rem" }}>
          Viewing details for Hub ID: {id}
        </p>
      </div>

      <div style={{ 
        border: "1px solid var(--color-border)", 
        borderRadius: "var(--radius-card)", 
        padding: "2rem", 
        backgroundColor: "var(--color-surface)"
      }}>
        <p style={{ color: "var(--color-text-secondary)" }}>
          Hub staff directories, parcel processing queues, and operational metrics will load here.
        </p>
      </div>
    </div>
  );
}
