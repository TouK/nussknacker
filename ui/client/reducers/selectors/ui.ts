import {RootState} from "../index"
import {createSelector} from "reselect"
import {UiState} from "../ui"

const getUi = (state: RootState): UiState => state.ui

export const areAllModalsClosed = createSelector(getUi, ui => ui.allModalsClosed)
export const isLeftPanelOpened = createSelector(getUi, ui => ui.leftPanelIsOpened)
export const isRightPanelOpened = createSelector(getUi, ui => ui.rightPanelIsOpened)
