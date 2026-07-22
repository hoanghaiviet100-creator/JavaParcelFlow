"use client";

import { ReactNode, useState } from "react";
import Link from "next/link";
import { useAppDispatch, useAppSelector } from "@/store/store";
import { setThemeMode } from "@/store/slices/themeSlice";
import styles from "./PublicLayout.module.scss";

interface PublicLayoutProps {
  children: ReactNode;
}

export default function PublicLayout({ children }: PublicLayoutProps) {
  const dispatch = useAppDispatch();
  const themeMode = useAppSelector((state) => state.theme.mode);
  const [isMenuOpen, setIsMenuOpen] = useState(false);

  const toggleTheme = () => {
    dispatch(setThemeMode(themeMode === "light" ? "dark" : "light"));
  };

  const toggleMenu = () => {
    setIsMenuOpen(!isMenuOpen);
  };

  return (
    <div className={styles.wrapper}>
      {/* Mesh Orbs in Background for Dark Theme */}
      <div className={styles.meshGlow} />

      <header className={styles.header}>
        <div className={styles.container}>
          <Link href="/" className={styles.logo}>
            <span>Parcel</span>Flow
          </Link>
          
          <nav className={`${styles.nav} ${isMenuOpen ? styles.navOpen : ""}`}>
            <Link href="/" className={styles.navLink} onClick={() => setIsMenuOpen(false)}>
              Home
            </Link>
            <Link href="/tracking" className={styles.navLink} onClick={() => setIsMenuOpen(false)}>
              Track Order
            </Link>
            <Link href="/login" className={styles.navLink} onClick={() => setIsMenuOpen(false)}>
              Staff Portal
            </Link>
            
            <button onClick={toggleTheme} className={styles.themeToggle} aria-label="Toggle theme">
              {themeMode === "light" ? (
                <span className={styles.icon}>🌙</span>
              ) : (
                <span className={styles.icon}>☀️</span>
              )}
            </button>
          </nav>

          <button 
            className={`${styles.hamburger} ${isMenuOpen ? styles.hamburgerActive : ""}`} 
            onClick={toggleMenu}
            aria-label="Toggle Navigation Menu"
          >
            <span className={styles.line}></span>
            <span className={styles.line}></span>
            <span className={styles.line}></span>
          </button>
        </div>
      </header>
      
      <main className={styles.main}>
        {children}
      </main>

      <footer className={styles.footer}>
        <div className={styles.footerContainer}>
          <p>© {new Date().getFullYear()} Parcel Flow. Enterprise Logistics Systems.</p>
        </div>
      </footer>
    </div>
  );
}
