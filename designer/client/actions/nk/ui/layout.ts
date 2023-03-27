import {getLayout} from "../../../reducers/selectors/layout"
import {NodeId} from "../../../types"
import {ThunkAction} from "../../reduxTypes"
import {batchGroupBy} from "../../../reducers/graph/batchGroupBy"

export type Position = { x: number, y: number }
export type NodePosition = { id: NodeId, position: Position }
export type Layout = NodePosition[]
export type GraphLayoutFunction = () => void
export type LayoutChangedAction = { layout: Layout, type: "LAYOUT_CHANGED" }
export type TogglePanelAction = { type: "TOGGLE_LEFT_PANEL" | "TOGGLE_RIGHT_PANEL", configId: string }

export function layoutChanged(layout?: Layout, batchEnd?: boolean): ThunkAction {
  return (dispatch, getState) => {
    const group = batchGroupBy.startOrContinue()
    dispatch({
      type: "LAYOUT_CHANGED",
      layout: layout || getLayout(getState()),
    })
    if (batchEnd) {
      batchGroupBy.end(group) // only if batch started here
    }
  }
}

export function togglePanel(side: "LEFT" | "RIGHT"): ThunkAction {
  return (dispatch, getState) => {
    const configId = getState().toolbars?.currentConfigId

    dispatch({
      type: side === "LEFT" ? "TOGGLE_LEFT_PANEL" : "TOGGLE_RIGHT_PANEL",
      configId,
    })
  }
}

export function layout(graphLayoutFunction: GraphLayoutFunction): ThunkAction {
  return (dispatch) => {
    graphLayoutFunction()

    return dispatch({
      type: "LAYOUT",
    })
  }
}

