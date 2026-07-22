interface PageProps {
  params: Promise<{ id: string }>;
}

export default async function RoutePlanDetailPage({ params }: PageProps) {
  const { id } = await params;

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
      <div>
        <h1 style={{ fontFamily: "var(--font-be-vietnam-pro)", fontSize: "2rem", fontWeight: 800, letterSpacing: "-0.03em" }}>
          Route Planning Track Detail
        </h1>
        <p style={{ color: "var(--color-text-secondary)", fontSize: "0.875rem" }}>
          Viewing track details for Route Plan ID: {id}
        </p>
      </div>

      <div style={{ 
        border: "1px solid var(--color-border)", 
        borderRadius: "var(--radius-card)", 
        padding: "2rem", 
        backgroundColor: "var(--color-surface)"
      }}>
        <p style={{ color: "var(--color-text-secondary)" }}>
          Calculated node steps sequence map, transit schedules, and assigned drivers list will load here.
        </p>
      </div>
    </div>
  );
}
