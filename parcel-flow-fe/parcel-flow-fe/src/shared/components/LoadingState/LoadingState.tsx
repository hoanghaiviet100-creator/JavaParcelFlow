import styles from "./LoadingState.module.scss";

interface LoadingStateProps {
  message?: string;
  fullPage?: boolean;
}

export default function LoadingState({
  message = "Loading information...",
  fullPage = false,
}: LoadingStateProps) {
  return (
    <div className={`${styles.wrapper} ${fullPage ? styles.fullPage : ""}`}>
      <div className={styles.spinner}></div>
      {message && <p className={styles.message}>{message}</p>}
    </div>
  );
}
