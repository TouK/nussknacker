import {RootState} from "../index"
import {UiState} from "../ui"

export const getUi = (state: RootState): UiState => state.ui
