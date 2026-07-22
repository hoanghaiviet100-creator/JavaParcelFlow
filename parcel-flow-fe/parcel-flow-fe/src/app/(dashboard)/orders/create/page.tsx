"use client";

import { forwardRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useForm, useFieldArray } from "react-hook-form";
import { useMutation, useQuery } from "@tanstack/react-query";
import Input from "@/shared/components/Input";
import Button from "@/shared/components/Button";
import { createOrderApi } from "@/features/orders/api/orders.api";
import { CreateOrderPayload } from "@/features/orders/types/order.types";
import { listHubsApi } from "@/features/hubs/api/hubs.api";
import { ApiError } from "@/shared/api/api-error";

interface PartyForm {
  fullName: string;
  phone: string;
  addressLine: string;
  districtId: number;
  provinceId: number;
}

interface FormValues {
  createdHubId: number;
  finalHubId?: number;
  serviceType: "STANDARD" | "EXPRESS" | "ECONOMY";
  paymentType: "SENDER_PAY" | "RECEIVER_PAY" | "COD";
  codAmount?: number;
  note?: string;
  sender: PartyForm;
  receiver: PartyForm;
  parcels: { weight: number; note?: string }[];
}

export default function CreateOrderPage() {
  const router = useRouter();
  const [banner, setBanner] = useState<string | null>(null);

  const hubsQuery = useQuery({ queryKey: ["hubs"], queryFn: listHubsApi, retry: false });
  const hubs = hubsQuery.data?.data ?? [];

  const {
    register,
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    defaultValues: {
      serviceType: "STANDARD",
      paymentType: "SENDER_PAY",
      parcels: [{ weight: 1 }],
    },
  });

  const { fields, append, remove } = useFieldArray({ control, name: "parcels" });

  const mutation = useMutation({
    mutationFn: (values: FormValues) => {
      const payload: CreateOrderPayload = {
        createdHubId: Number(values.createdHubId),
        finalHubId: values.finalHubId ? Number(values.finalHubId) : undefined,
        serviceType: values.serviceType,
        paymentType: values.paymentType,
        codAmount: values.codAmount ? Number(values.codAmount) : undefined,
        note: values.note || undefined,
        sender: toParty(values.sender),
        receiver: toParty(values.receiver),
        parcels: values.parcels.map((p) => ({
          weight: Number(p.weight),
          note: p.note || undefined,
        })),
      };
      return createOrderApi(payload);
    },
    onSuccess: (res) => {
      router.push(`/orders/${res.data.id}`);
    },
    onError: (err) => {
      setBanner(err instanceof ApiError ? err.message : "Could not create the order.");
    },
  });

  return (
    <div style={{ maxWidth: "760px", display: "flex", flexDirection: "column", gap: "1.5rem" }}>
      <div>
        <h1 style={{ fontFamily: "var(--font-be-vietnam-pro)", fontSize: "2rem", fontWeight: 800, letterSpacing: "-0.03em" }}>
          Create New Order
        </h1>
        <p style={{ color: "var(--color-text-secondary)", fontSize: "0.875rem" }}>
          Input sender, receiver, destination, and package details to initiate a route.
        </p>
      </div>

      {banner && (
        <div style={{ padding: "0.75rem 1rem", borderRadius: "var(--radius-card)", background: "rgba(239,68,68,0.1)", color: "#991b1b", fontSize: "0.875rem" }}>
          {banner}
        </div>
      )}

      <form
        onSubmit={handleSubmit((v) => {
          setBanner(null);
          mutation.mutate(v);
        })}
        style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}
      >
        <Section title="Routing">
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "1rem" }}>
            <Select label="Origin Hub" error={errors.createdHubId?.message} {...register("createdHubId", { valueAsNumber: true, validate: (v) => (typeof v === "number" && !Number.isNaN(v)) || "Origin hub is required" })}>
              <option value="">Select origin hub…</option>
              {hubs.map((h) => (
                <option key={h.id} value={h.id}>{h.name} ({h.code})</option>
              ))}
            </Select>
            <Select label="Destination Hub (optional)" {...register("finalHubId", { valueAsNumber: true })}>
              <option value="">Select destination hub…</option>
              {hubs.map((h) => (
                <option key={h.id} value={h.id}>{h.name} ({h.code})</option>
              ))}
            </Select>
            <Select label="Service Type" {...register("serviceType")}>
              <option value="STANDARD">Standard</option>
              <option value="EXPRESS">Express</option>
              <option value="ECONOMY">Economy</option>
            </Select>
            <Select label="Payment Type" {...register("paymentType")}>
              <option value="SENDER_PAY">Sender pays</option>
              <option value="RECEIVER_PAY">Receiver pays</option>
              <option value="COD">Cash on delivery</option>
            </Select>
            <Input type="number" step="0.01" label="COD Amount (optional)" {...register("codAmount", { valueAsNumber: true })} />
            <Input type="text" label="Note (optional)" {...register("note")} />
          </div>
        </Section>

        <PartySection title="Sender" prefix="sender" register={register} errors={errors} />
        <PartySection title="Receiver" prefix="receiver" register={register} errors={errors} />

        <Section title="Parcels">
          <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
            {fields.map((field, idx) => (
              <div key={field.id} style={{ display: "grid", gridTemplateColumns: "1fr 2fr auto", gap: "1rem", alignItems: "end" }}>
                <Input
                  type="number"
                  step="0.01"
                  label={`Weight #${idx + 1} (kg)`}
                  error={errors.parcels?.[idx]?.weight?.message}
                  {...register(`parcels.${idx}.weight`, { required: "Weight required", valueAsNumber: true, min: { value: 0.01, message: "Must be > 0" } })}
                />
                <Input type="text" label="Parcel note (optional)" {...register(`parcels.${idx}.note`)} />
                <Button type="button" variant="secondary" onClick={() => fields.length > 1 && remove(idx)}>
                  Remove
                </Button>
              </div>
            ))}
            <Button type="button" variant="secondary" onClick={() => append({ weight: 1 })}>
              + Add parcel
            </Button>
          </div>
        </Section>

        <Button type="submit" variant="primary" size="lg" loading={mutation.isPending}>
          Register Order
        </Button>
      </form>
    </div>
  );
}

function toParty(p: PartyForm) {
  return {
    fullName: p.fullName,
    phone: p.phone,
    addressLine: p.addressLine,
    districtId: Number(p.districtId),
    provinceId: Number(p.provinceId),
  };
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{ border: "1px solid var(--color-border)", borderRadius: "var(--radius-card)", padding: "1.5rem", backgroundColor: "var(--color-surface)", display: "flex", flexDirection: "column", gap: "1rem" }}>
      <h3 style={{ fontSize: "1rem", fontWeight: 700 }}>{title}</h3>
      {children}
    </div>
  );
}

/* eslint-disable @typescript-eslint/no-explicit-any */
function PartySection({ title, prefix, register, errors }: { title: string; prefix: "sender" | "receiver"; register: any; errors: any }) {
  return (
    <Section title={title}>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "1rem" }}>
        <Input type="text" label={`${title} Full Name`} error={errors?.[prefix]?.fullName?.message} {...register(`${prefix}.fullName`, { required: "Required" })} />
        <Input type="tel" label={`${title} Phone`} error={errors?.[prefix]?.phone?.message} {...register(`${prefix}.phone`, { required: "Required" })} />
        <Input type="text" label={`${title} Address Line`} error={errors?.[prefix]?.addressLine?.message} {...register(`${prefix}.addressLine`, { required: "Required" })} />
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "1rem" }}>
          <Input type="number" label={`${title} District ID`} error={errors?.[prefix]?.districtId?.message} {...register(`${prefix}.districtId`, { required: "Required", valueAsNumber: true })} />
          <Input type="number" label={`${title} Province ID`} error={errors?.[prefix]?.provinceId?.message} {...register(`${prefix}.provinceId`, { required: "Required", valueAsNumber: true })} />
        </div>
      </div>
    </Section>
  );
}

const Select = forwardRef<
  HTMLSelectElement,
  React.SelectHTMLAttributes<HTMLSelectElement> & { label: string; error?: string }
>(({ label, error, children, ...rest }, ref) => {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "0.375rem" }}>
      <label style={{ fontSize: "0.875rem", fontWeight: 600 }}>{label}</label>
      <select
        ref={ref}
        {...rest}
        style={{ padding: "0.625rem 0.75rem", borderRadius: "var(--radius-input, 8px)", border: "1px solid var(--color-border)", background: "var(--color-background)", color: "var(--color-text-primary)" }}
      >
        {children}
      </select>
      {error && <span style={{ color: "#ef4444", fontSize: "0.8125rem" }}>{error}</span>}
    </div>
  );
});
Select.displayName = "Select";
