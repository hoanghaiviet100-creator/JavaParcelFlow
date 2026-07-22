import { ReactNode } from "react";
import styles from "./EmptyState.module.scss";

interface EmptyStateProps {
  title?: string;
  description?: string;
  icon?: string;
  action?: ReactNode;
}

export default function EmptyState({
  title = "No data found",
  description = "There is currently no information to show in this view.",
  icon = "📭",
  action,
}: EmptyStateProps) {
  return (
    <div className={styles.wrapper}>
      <div className={styles.icon}>{icon}</div>
      <h3 className={styles.title}>{title}</h3>
      <p className={styles.description}>{description}</p>
      {action && <div className={styles.action}>{action}</div>}
    </div>
  );
}
