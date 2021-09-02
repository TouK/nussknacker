import {Reducer} from "../actions/reduxTypes"
import {mergeReducers} from "./mergeReducers"

export type UiState = {
  isToolTipsHighlighted: boolean,
}

const emptyUiState: UiState = {
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
    default:
      return state
  }
}

export const reducer = mergeReducers<UiState>(
  uiReducer,
)
