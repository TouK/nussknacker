import {createSelector} from "reselect"
import {RootState} from "../index"
import {UiState} from "../ui"

export const getUi = (state: RootState): UiState => state.ui

export const isEdgeDetailsModalVisible = createSelector(getUi, ui => !!ui.showEdgeDetailsModal)
export const isNodeDetailsModalVisible = createSelector(getUi, ui => !!ui.showNodeDetailsModal)

export const areAllModalsClosed = createSelector(
  isNodeDetailsModalVisible,
  isEdgeDetailsModalVisible,
  (node, edge) => !(node || edge),
)

