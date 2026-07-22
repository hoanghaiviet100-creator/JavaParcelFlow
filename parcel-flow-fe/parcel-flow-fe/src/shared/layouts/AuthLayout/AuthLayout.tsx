import { ReactNode } from "react";
import Link from "next/link";
import styles from "./AuthLayout.module.scss";

interface AuthLayoutProps {
  children: ReactNode;
}

export default function AuthLayout({ children }: AuthLayoutProps) {
  return (
    <div className={styles.wrapper}>
      {/* Cinematic mesh orbs background */}
      <div className={styles.meshGlow} />

      {/* Double Bezel Outer Shell */}
      <div className={styles.outerShell}>
        {/* Double Bezel Inner Core */}
        <div className={styles.innerCore}>
          <div className={styles.logoWrapper}>
            <Link href="/" className={styles.logo}>
              <span>Parcel</span>Flow
            </Link>
          </div>
          {children}
        </div>
      </div>
    </div>
  );
}
