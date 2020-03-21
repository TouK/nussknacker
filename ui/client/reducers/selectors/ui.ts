import {createSelector} from "reselect"
import {RootState} from "../index"
import {UiState} from "../ui"

const getUi = (state: RootState): UiState => state.ui

export const areAllModalsClosed = createSelector(getUi, ui => ui.allModalsClosed)
export const isLeftPanelOpened = createSelector(getUi, ui => ui.leftPanelIsOpened)
export const isRightPanelOpened = createSelector(getUi, ui => ui.rightPanelIsOpened)

export const getExpandedGroups = createSelector(getUi, ui => ui.expandedGroups)
export const getShowNodeDetailsModal = createSelector(getUi, ui => ui.showNodeDetailsModal)
