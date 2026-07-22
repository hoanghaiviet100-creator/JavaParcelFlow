import { z } from "zod";

export const trackingSchema = z.object({
  orderCode: z.string().min(1, "Order code is required").regex(/^ORD-\d+$/i, "Code must be in format: ORD-XXXXXX"),
  phoneNumber: z.string().optional().or(z.literal("")),
});

export type TrackingSchemaType = z.infer<typeof trackingSchema>;
