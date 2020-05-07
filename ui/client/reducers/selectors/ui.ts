import {RootState} from "../index"
import {createSelector} from "reselect"
import {UiState} from "../ui"

export const getUi = (state: RootState): UiState => state.ui

export const areAllModalsClosed = createSelector(getUi, ui => ui.allModalsClosed)
export const isLeftPanelOpened = createSelector(getUi, ui => ui.leftPanelIsOpened)
export const isRightPanelOpened = createSelector(getUi, ui => ui.rightPanelIsOpened)
export const getModalDialog = createSelector(getUi, ui => ui.modalDialog || {})
export const getOpenDialog = createSelector(getModalDialog, m => m.openDialog)

export const getShowNodeDetailsModal = createSelector(getUi, ui => ui.showNodeDetailsModal)
