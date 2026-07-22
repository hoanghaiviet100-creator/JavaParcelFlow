import { UserRole } from "@/config/permissions";

export type { UserRole };

export interface UserProfile {
  id: string;
  email: string;
  fullName: string;
  role: UserRole;
  phoneNumber?: string;
  hubId?: string; // null if not hub staff
  createdAt: string;
  updatedAt: string;
}

export interface UserSessionState {
  user: UserProfile | null;
  role: UserRole | null;
  permissions: string[];
  isAuthenticated: boolean;
  isLoading: boolean;
}
