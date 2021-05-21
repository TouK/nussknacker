import {RootState} from "../index"
import {createSelector} from "reselect"
import {UiState} from "../ui"

export const getUi = (state: RootState): UiState => state.ui

export const isEdgeDetailsModalVisible = createSelector(getUi, ui => !!ui.showEdgeDetailsModal)
export const isNodeDetailsModalVisible = createSelector(getUi, ui => !!ui.showNodeDetailsModal)
export const getModalDialog = createSelector(getUi, ui => ui.modalDialog || {})
export const getOpenDialog = createSelector(getModalDialog, m => m.openDialog)

export const areAllModalsClosed = createSelector(getUi, isNodeDetailsModalVisible, isEdgeDetailsModalVisible, (ui, node, edge) => {
  return (
    !ui.modalDialog.openDialog &&
    !node &&
    !edge &&
    !ui.confirmDialog.isOpen
  )
})

