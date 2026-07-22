import { createSlice, PayloadAction } from "@reduxjs/toolkit";
import { ThemeMode, THEME_MODES } from "@/types/enums/theme";

interface ThemeState {
  mode: ThemeMode;
}

const initialState: ThemeState = {
  mode: THEME_MODES.SYSTEM,
};

export const themeSlice = createSlice({
  name: "theme",
  initialState,
  reducers: {
    setThemeMode: (state, action: PayloadAction<ThemeMode>) => {
      state.mode = action.payload;
    },
  },
});

export const { setThemeMode } = themeSlice.actions;

export default themeSlice.reducer;
