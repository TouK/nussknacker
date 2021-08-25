import {createSelector} from "reselect"
import {RootState} from "../index"
import {UiState} from "../ui"

export const getUi = (state: RootState): UiState => state.ui

//TODO: check if needed then connect to right place
export const isNodeDetailsModalVisible = createSelector(getUi, ui => false)

export const areAllModalsClosed = createSelector(
  isNodeDetailsModalVisible,
  (node) => !node,
)

