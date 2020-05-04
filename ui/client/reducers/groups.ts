import {Reducer} from "../actions/reduxTypes"
import {UiState} from "./ui"

export const reducer: Reducer<UiState> = (state, action) => {
  switch (action.type) {
    case "EXPAND_GROUP":
      return {
        ...state,
        expandedGroups: [...state.expandedGroups, action.id],
      }
    case "COLLAPSE_GROUP":
      return {
        ...state,
        expandedGroups: state.expandedGroups.filter(id => id !== action.id),
      }
    case "COLLAPSE_ALL_GROUPS":
      return {
        ...state,
        expandedGroups: [],
      }
    case "EDIT_GROUP":
      return {
        ...state,
        expandedGroups: state.expandedGroups.map(id => id === action.oldGroupId ? action.newGroup.id : id),
      }
    default:
      return state
  }
}
