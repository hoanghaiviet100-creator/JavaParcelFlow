import { UserProfile } from "../domain/user";

export interface LoginRequest {
  email: string;
  phoneNumber?: string;
  code?: string; // OTP
  password?: string; // if password auth
}

export interface LoginResponse {
  user: UserProfile;
}
