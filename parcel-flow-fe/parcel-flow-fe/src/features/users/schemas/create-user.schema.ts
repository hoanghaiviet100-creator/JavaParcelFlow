import { z } from "zod";

export const createUserSchema = z
  .object({
    fullName: z.string().min(1, "Full name is required"),
    email: z.string().email("Please enter a valid email address"),
    phone: z
      .string()
      .optional()
      .refine((v) => !v || /^[0-9+\-\s()]{6,20}$/.test(v), "Please enter a valid phone number"),
    roleCode: z.enum(["ADMIN", "HUB_MANAGER", "HUB_STAFF", "DISPATCHER", "SHIPPER"]),
    hubId: z
      .string()
      .optional()
      .refine((v) => !v || /^\d+$/.test(v), "Hub ID must be a number"),
  })
  .refine((data) => data.roleCode !== "SHIPPER" || (!!data.hubId && data.hubId.length > 0), {
    message: "Hub ID is required for a SHIPPER",
    path: ["hubId"],
  });

export type CreateUserSchemaType = z.infer<typeof createUserSchema>;
