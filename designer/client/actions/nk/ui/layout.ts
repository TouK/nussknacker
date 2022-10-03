import {events} from "../../../analytics/TrackingEvents"
import * as VisualizationUrl from "../../../common/VisualizationUrl"
import history from "../../../history"
import {getLayout} from "../../../reducers/selectors/layout"
import {NodeId} from "../../../types"
import {ThunkAction} from "../../reduxTypes"
import {reportEvent} from "../reportEvent"

export type Position = {x: number, y: number}
export type NodePosition = {id: NodeId, position: Position}
export type Layout = NodePosition[]
export type GraphLayoutFunction = () => void
export type LayoutChangedAction = {layout: Layout, type: "LAYOUT_CHANGED"}
export type TogglePanelAction = {type: "TOGGLE_LEFT_PANEL" | "TOGGLE_RIGHT_PANEL", configId: string}

export function layoutChanged(layout?: Layout): ThunkAction {
  return (dispatch, getState) => dispatch({
    type: "LAYOUT_CHANGED",
    layout: layout || getLayout(getState()),
  })
}

export function togglePanel(side: "LEFT" | "RIGHT"): ThunkAction {
  return (dispatch, getState) => {
    const configId = getState().toolbars?.currentConfigId

    dispatch({
      type: side === "LEFT" ? "TOGGLE_LEFT_PANEL" : "TOGGLE_RIGHT_PANEL",
      configId,
    })

    dispatch(reportEvent({
      category: side === "LEFT" ? events.categories.leftPanel : events.categories.rightPanel,
      action: events.actions.buttonClick,
      name: side === "LEFT" ? "toggle_left_panel" : "toggle_right_panel",
    }))
  }
}

export function layout(graphLayoutFunction: GraphLayoutFunction): ThunkAction {
  return (dispatch) => {
    graphLayoutFunction()

    dispatch(reportEvent({
      category: "right_panel",
      action: "button_click",
      name: "layout",
    }))

    return dispatch({
      type: "LAYOUT",
    })
  }
}

