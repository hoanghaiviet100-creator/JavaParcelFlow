import Link from "next/link";

export default function NotFound() {
  return (
    <div 
      style={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        height: "100vh",
        textAlign: "center",
        backgroundColor: "var(--color-background, #F8FAFC)",
        color: "var(--color-text-primary, #0F172A)",
        padding: "2rem",
      }}
    >
      <h1 style={{ fontSize: "5rem", fontWeight: 800, margin: 0, color: "var(--color-primary, #2563EB)" }}>
        404
      </h1>
      <h2 style={{ fontSize: "1.5rem", fontWeight: 600, marginTop: "1rem", marginBottom: "0.5rem" }}>
        Page Not Found
      </h2>
      <p style={{ color: "var(--color-text-secondary, #475569)", marginBottom: "2rem", maxWidth: "400px" }}>
        The page you are looking for does not exist or has been moved.
      </p>
      <Link 
        href="/"
        style={{
          display: "inline-block",
          backgroundColor: "var(--color-primary, #2563EB)",
          color: "#FFFFFF",
          padding: "0.75rem 1.5rem",
          borderRadius: "var(--radius-button, 12px)",
          fontWeight: 500,
          transition: "opacity 0.2s ease",
        }}
      >
        Go Back Home
      </Link>
    </div>
  );
}
