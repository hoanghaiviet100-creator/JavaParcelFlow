"use client";

import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import Input from "@/shared/components/Input";
import Button from "@/shared/components/Button";
import { trackingSchema, TrackingSchemaType } from "@/features/tracking/schemas/tracking.schema";
import styles from "./tracking.module.scss";

export default function TrackingPage() {
  const router = useRouter();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<TrackingSchemaType>({
    resolver: zodResolver(trackingSchema),
    defaultValues: {
      orderCode: "",
      phoneNumber: "",
    },
  });

  const onSubmit = (data: TrackingSchemaType) => {
    const code = data.orderCode.trim().toUpperCase();
    const phone = data.phoneNumber?.trim();
    const query = phone ? `?code=${code}&phone=${phone}` : `?code=${code}`;
    router.push(`/tracking/result${query}`);
  };

  return (
    <div className={styles.container}>
      <div style={{ textAlign: "center" }}>
        <h1 className={styles.title}>Track Shipment</h1>
        <p className={styles.subtitle}>
          Verify the current status and location transit logs of your parcel.
        </p>
      </div>

      <div className={styles.alertInfo}>
        <div className={styles.alertTitle}>
          <span>💡</span> How tracking works
        </div>
        <p>
          Enter the order code from your shipping confirmation. Add the receiver&apos;s
          phone number to also reveal sender/receiver details; without it you&apos;ll still
          see the current status and transit timeline.
        </p>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className={styles.form}>
        <Input
          type="text"
          label="Order Code"
          placeholder="e.g. ORD-1002495"
          error={errors.orderCode?.message}
          {...register("orderCode")}
        />
        <Input
          type="tel"
          label="Phone Number"
          placeholder="Sender or Receiver's mobile number (Optional)"
          error={errors.phoneNumber?.message}
          {...register("phoneNumber")}
        />

        <Button
          type="submit"
          variant="primary"
          size="lg"
          style={{
            marginTop: "0.5rem",
            boxShadow: "0 4px 12px var(--color-glow)",
          }}
        >
          Track Now
        </Button>
      </form>
    </div>
  );
}
