import {persistReducer} from "redux-persist"
import storage from "redux-persist/lib/storage"
import {Reducer} from "../actions/reduxTypes"

export interface ToolboxState {
  opened: Record<string, boolean>,
}

const nodeToolbox: Reducer<ToolboxState> = (state = {opened: {}}, action) => {
  switch (action.type) {
    case "TOGGLE_NODE_TOOLBOX_GROUP":
      return {
        opened: {
          ...state.opened,
          [action.nodeGroup]: !state.opened[action.nodeGroup],
        },
      }

    case "RESET_TOOLBARS":
      return {
        opened: {},
      }

    default:
      return state
  }
}

export const toolbox = persistReducer({key: `toolbox`, storage}, nodeToolbox)
