import {Action} from "../actions/reduxTypes"

export enum ToolbarsSide {
  TopRight = "TOP-RIGHT",
  BottomRight = "BOTTOM-RIGHT",
  Hidden = "HIDDEN",
}

export type ToolbarsState = {
  [side in ToolbarsSide]?: string[]
}

export function reducer(state: ToolbarsState = {}, action: Action): ToolbarsState {
  switch (action.type) {
    case "MOVE_TOOLBAR":
      const [fromToolbar, fromIndex] = action.from
      const [toToolbar, toIndex] = action.to

      const src = [].concat(state[fromToolbar])
      const [item] = src.splice(fromIndex, 1)

      const dst = fromToolbar === toToolbar ? src : [].concat(state[toToolbar])
      dst.splice(toIndex, 0, item)

      return {...state, [fromToolbar]: src, [toToolbar]: dst}

    case "REGISTER_TOOLBARS":
      const groups = Object.values(state)
      const newToolbars = action.toolbars.filter(([id]) => !groups.some(g => g.includes(id)))
      return newToolbars.reduce((nextState, [id, side = ToolbarsSide.TopRight]) => {
        const currentValues = nextState[side] || []
        return {...nextState, [side]: [...currentValues, id]}
      }, state)

    default:
      return state
  }
}
