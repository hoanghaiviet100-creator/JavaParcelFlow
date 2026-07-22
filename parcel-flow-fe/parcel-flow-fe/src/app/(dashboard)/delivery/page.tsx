export default function DeliveryAssignmentListPage() {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
      <div>
        <h1 style={{ fontFamily: "var(--font-be-vietnam-pro)", fontSize: "2rem", fontWeight: 800, letterSpacing: "-0.03em" }}>
          Delivery Assignments
        </h1>
        <p style={{ color: "var(--color-text-secondary)", fontSize: "0.875rem" }}>
          Assign last-mile packages to shippers and courier fleets.
        </p>
      </div>

      <div style={{ 
        border: "1px solid var(--color-border)", 
        borderRadius: "var(--radius-card)", 
        padding: "2rem", 
        backgroundColor: "var(--color-surface)",
        textAlign: "center"
      }}>
        <p style={{ color: "var(--color-text-secondary)" }}>
          Pending parcel allocations and shipper queue logs will display here.
        </p>
      </div>
    </div>
  );
}
