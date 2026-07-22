"use client";

import Link from "next/link";
import styles from "../login/login.module.scss";

export default function LockedAccountPage() {
  return (
    <div style={{ width: "100%", textAlign: "center" }}>
      <h1 className={styles.title}>Account Locked</h1>
      <p className={styles.subtitle}>
        This account has been permanently locked after repeated failed sign-in attempts.
        For security reasons it must be unlocked by an administrator.
      </p>
      <div className={styles.errorAlert} style={{ marginTop: "1rem", justifyContent: "center" }}>
        <span>🔒</span>
        <span>Please contact your system administrator to regain access.</span>
      </div>
      <p className={styles.subtitle} style={{ marginTop: "1.5rem" }}>
        <Link href="/login">Back to sign in</Link>
      </p>
    </div>
  );
}
