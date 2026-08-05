import { z } from "zod";

export const trackingSchema = z.object({
  // Codes come from CodeGenerator.orderCode(): "OD" + yyyyMMdd + 6 random chars.
  // Checked loosely as [A-Z0-9] rather than the generator's exact alphabet — this
  // regex previously demanded "ORD-\d+", which no real code has ever matched, so it
  // rejected every genuine lookup. Catching typos is worth a client-side check;
  // mirroring the server's alphabet here is not, since it only drifts again.
  orderCode: z
    .string()
    .trim()
    .min(1, "Order code is required")
    .regex(/^OD\d{8}[A-Z0-9]{6}$/i, "Code must look like OD20260805LJ2GJ9"),
  phoneNumber: z.string().optional().or(z.literal("")),
});

export type TrackingSchemaType = z.infer<typeof trackingSchema>;
