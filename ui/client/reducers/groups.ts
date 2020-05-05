import {Reducer} from "../actions/reduxTypes"
import {GroupId} from "../types"

export type GroupsState = GroupId[]

export const reducer: Reducer<GroupsState> = (state = [], action) => {
  switch (action.type) {
    case "EXPAND_GROUP":
      return [...state, action.id]
    case "COLLAPSE_GROUP":
      return state.filter(id => id !== action.id)
    case "COLLAPSE_ALL_GROUPS":
      return []
    case "EDIT_GROUP":
      return state.map(id => id === action.oldGroupId ? action.newGroup.id : id)
    default:
      return state
  }
}
