import { z } from "zod";

/**
 * Strong-password policy mirrored from the backend PasswordPolicy (Phase 2):
 * min 8, 1 upper, 1 lower, 1 digit, 1 special.
 */
export const strongPassword = z
  .string()
  .min(8, "Password must be at least 8 characters")
  .regex(/[A-Z]/, "Password must contain an uppercase letter")
  .regex(/[a-z]/, "Password must contain a lowercase letter")
  .regex(/\d/, "Password must contain a digit")
  .regex(/[^A-Za-z0-9]/, "Password must contain a special character");

export const changePasswordSchema = z
  .object({
    email: z.string().email("Please enter a valid email address"),
    currentPassword: z.string().min(1, "Current password is required"),
    newPassword: strongPassword,
    confirmPassword: z.string().min(1, "Please confirm your new password"),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: "Passwords do not match",
    path: ["confirmPassword"],
  });

export type ChangePasswordSchemaType = z.infer<typeof changePasswordSchema>;
