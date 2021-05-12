import {Reducer} from "../actions/reduxTypes"
import {mergeReducers} from "./mergeReducers"

export type UiState = {
  showNodeDetailsModal: boolean,
  showEdgeDetailsModal: boolean,
  isToolTipsHighlighted: boolean,
}

const emptyUiState: UiState = {
  showNodeDetailsModal: false,
  showEdgeDetailsModal: false,
  isToolTipsHighlighted: false,
}

const uiReducer: Reducer<UiState> = (state = emptyUiState, action) => {
  switch (action.type) {
    case "SWITCH_TOOL_TIPS_HIGHLIGHT": {
      return {
        ...state,
        isToolTipsHighlighted: action.isHighlighted,
      }
    }
    case "CLEAR":
    case "CLOSE_MODALS": {
      return {
        ...state,
        showNodeDetailsModal: false,
        showEdgeDetailsModal: false,
      }
    }
    case "DISPLAY_MODAL_NODE_DETAILS": {
      return {
        ...state,
        showNodeDetailsModal: true,
      }
    }
    case "DISPLAY_MODAL_EDGE_DETAILS": {
      return {
        ...state,
        showEdgeDetailsModal: true,
      }
    }
    default:
      return state
  }
}

export const reducer = mergeReducers<UiState>(
  uiReducer,
)
