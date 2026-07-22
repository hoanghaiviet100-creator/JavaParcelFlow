import { createSlice, PayloadAction } from "@reduxjs/toolkit";
import { UserProfile, UserSessionState } from "@/types/domain/user";
import { ROLE_PERMISSIONS } from "@/config/permissions";

const initialState: UserSessionState = {
  user: null,
  role: null,
  permissions: [],
  isAuthenticated: false,
  isLoading: false,
};

export const authSlice = createSlice({
  name: "auth",
  initialState,
  reducers: {
    setLoading: (state, action: PayloadAction<boolean>) => {
      state.isLoading = action.payload;
    },
    // Vẫn giữ nguyên kiểu UserProfile để khớp với cấu trúc cũ của bạn
    loginSuccess: (state, action: PayloadAction<UserProfile>) => {
      const user = action.payload;
      state.user = user;
      // Thêm toán tử || 'GUEST' để tránh lỗi undefined
      const role = (user && user.role) ? user.role : null;
      state.role = role as "ADMIN" | "HUB_MANAGER" | "HUB_STAFF" | "DISPATCHER" | "SHIPPER" | null;
      state.permissions = ROLE_PERMISSIONS[role as keyof typeof ROLE_PERMISSIONS] || [];
      state.isAuthenticated = true;
      state.isLoading = false;
    },
    logoutSuccess: (state) => {
      state.user = null;
      state.role = null;
      state.permissions = [];
      state.isAuthenticated = false;
      state.isLoading = false;
    },
    updateProfile: (state, action: PayloadAction<Partial<UserProfile>>) => {
      if (state.user) {
        state.user = { ...state.user, ...action.payload };
        if (action.payload.role) {
          state.role = action.payload.role;
          state.permissions = ROLE_PERMISSIONS[action.payload.role as keyof typeof ROLE_PERMISSIONS] || [];
        }
      }
    },
  },
});

export const { setLoading, loginSuccess, logoutSuccess, updateProfile } = authSlice.actions;

export default authSlice.reducer;