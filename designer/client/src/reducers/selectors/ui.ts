import type { RootState } from "../index";
import type { UiState } from "../ui";

export const getUi = (state: RootState): UiState => state.ui;
