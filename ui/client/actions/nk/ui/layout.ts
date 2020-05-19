import {events} from "../../../analytics/TrackingEvents"
import * as VisualizationUrl from "../../../common/VisualizationUrl"
import history from "../../../history"
import {NodeId} from "../../../types"
import {reportEvent} from "../reportEvent"
import {ThunkAction} from "../../reduxTypes"
import {getLayout} from "../../../reducers/selectors/layout"

export type Position = { x: number, y: number }
export type NodePosition = { id: NodeId, position: Position }
export type Layout = NodePosition[]
export type GraphLayoutFunction = () => void
export type LayoutChangedAction = { layout: Layout, type: "LAYOUT_CHANGED" }
export type TogglePanelAction = { type: "TOGGLE_LEFT_PANEL" | "TOGGLE_RIGHT_PANEL" }

export function layoutChanged(layout?: Layout): ThunkAction {
  return (dispatch, getState) => dispatch({
    type: "LAYOUT_CHANGED",
    layout: layout || getLayout(getState()),
  })
}

export function toggleLeftPanel(): TogglePanelAction {
  return {
    type: "TOGGLE_LEFT_PANEL",
  }
}

export function toggleRightPanel(): ThunkAction {
  return (dispatch) => {
    dispatch({
      type: "TOGGLE_RIGHT_PANEL",
    })

    dispatch(reportEvent({
      category: events.categories.rightPanel,
      action: events.actions.buttonClick,
      name: "toggle_right_panel",
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

export function businessViewChanged(value: boolean) {
  history.replace({
    pathname: window.location.pathname,
    search: VisualizationUrl.setAndPreserveLocationParams({
      businessView: value,
    }),
  })

  return {
    type: "BUSINESS_VIEW_CHANGED",
    businessView: value,
  }
}
