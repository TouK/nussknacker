import {persistReducer} from "redux-persist"
import storage from "redux-persist/lib/storage"
import {Reducer} from "../actions/reduxTypes"
import {combineReducers} from "redux"

export enum ToolbarsSide {
  TopRight = "TOP-RIGHT",
  BottomRight = "BOTTOM-RIGHT",
  TopLeft = "TOP-LEFT",
  BottomLeft = "BOTTOM-LEFT",
  Hidden = "HIDDEN",
}

type NodeToolbox = {
  opened: Record<string, boolean>,
}

type InitData = Array<[string, ToolbarsSide]>

type Positions = {
  [side in ToolbarsSide]?: string[]
}

type Collapsed = Record<string, boolean>

export type ToolbarsState = {
  positions: Positions,
  initData: InitData,
  collapsed: Collapsed,
  nodeToolbox: NodeToolbox,
}

type Id = `#${string}`
export type ToolbarsStates = {currentConfigId?: string} & { [id in Id]: ToolbarsState }

const nodeToolbox: Reducer<NodeToolbox> = (state = {opened: {}}, action) => {
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

function setupPositions(positions: Positions, toolbars: Array<[string, ToolbarsSide]>): Positions {
  const groups = Object.values(positions)
  const newToolbars = toolbars.filter(([id]) => !groups.some(g => g.includes(id)))
  return newToolbars.reduce((nextState, [id, side]) => {
    const currentValues = nextState[side || ToolbarsSide.TopRight] || []
    return {...nextState, [side || ToolbarsSide.TopRight]: [...currentValues, id]}
  }, positions)
}

function insertItemOnTargetPlaceOnList(
  state: Positions,
  fromToolbar: ToolbarsSide | string,
  toToolbar: ToolbarsSide | string,
  fromIndex: number,
  toIndex: number,
): [string[], string[]] {
  const isSameToolbar = fromToolbar === toToolbar

  const src = [].concat(state[fromToolbar])
  const [item] = src.splice(fromIndex, 1)

  const dst = isSameToolbar ? src : [].concat(state[toToolbar])
  dst.splice(toIndex, 0, item)
  return [src, dst]
}

const positions: Reducer<Positions> = (state = {}, action) => {
  switch (action.type) {
    case "MOVE_TOOLBAR":
      const [fromToolbar, fromIndex] = action.from
      const [toToolbar, toIndex] = action.to
      const [src, dst] = insertItemOnTargetPlaceOnList(state, fromToolbar, toToolbar, fromIndex, toIndex)

      return {
        ...state,
        [fromToolbar]: src,
        [toToolbar]: dst,
      }

    case "REGISTER_TOOLBARS":
    case "RESET_TOOLBARS":
      return setupPositions(state, action.toolbars)

    default:
      return state
  }
}

const collapsed: Reducer<Collapsed> = (state = {}, action) => {
  switch (action.type) {
    case "TOGGLE_ALL_TOOLBARS":
      // eslint-disable-next-line i18next/no-literal-string
      throw "not implemented"
      return state

    case "TOGGLE_TOOLBAR":
      return {...state, [action.id]: action.isCollapsed}

    default:
      return state
  }
}

const initData: Reducer<InitData> = (state = [], action) => {
  return action.type === "REGISTER_TOOLBARS" ? action.toolbars : state
}

const resetReducer: Reducer<ToolbarsState> = (state, action) => {
  return action.type === "RESET_TOOLBARS" ? {...state, collapsed: {}, positions: {}} : state
}

const combinedReducers = combineReducers<ToolbarsState>({
  collapsed, positions, nodeToolbox, initData,
})

const configReducer: Reducer<ToolbarsState> = (state, action) => {
  const withReset = resetReducer(state, action)
  return combinedReducers(withReset, action)
}

const configIdReducer: Reducer<string> = (state = "", action) => {
  return action.type === "REGISTER_TOOLBARS" ? action.configId : state
}

const reducer: Reducer<ToolbarsStates> = (state = {}, action) => {
  const currentConfigId = configIdReducer(state.currentConfigId, action)
  return currentConfigId ?
    {...state, currentConfigId, [`#${currentConfigId}`]: configReducer(state[`#${currentConfigId}`], action)} :
    state
}

export const toolbars = persistReducer({key: `toolbars`, storage}, reducer)
