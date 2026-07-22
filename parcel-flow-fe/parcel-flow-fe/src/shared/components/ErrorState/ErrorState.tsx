import styles from "./ErrorState.module.scss";

interface ErrorStateProps {
  title?: string;
  message?: string;
  onRetry?: () => void;
}

export default function ErrorState({
  title = "An error occurred",
  message = "We encountered a problem loading this content. Please try again.",
  onRetry,
}: ErrorStateProps) {
  return (
    <div className={styles.wrapper}>
      <div className={styles.icon}>⚠️</div>
      <h3 className={styles.title}>{title}</h3>
      <p className={styles.message}>{message}</p>
      {onRetry && (
        <button onClick={onRetry} className={styles.retryBtn}>
          Try Again
        </button>
      )}
    </div>
  );
}
