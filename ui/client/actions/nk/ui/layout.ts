import {events} from "../../../analytics/TrackingEvents"
import * as VisualizationUrl from "../../../common/VisualizationUrl"
import history from "../../../history"
import {$TodoType} from "../../migrationTypes"
import {ThunkAction} from "../../reduxTypes"
import {reportEvent} from "../reportEvent"

export type Layout = $TodoType;
export type GraphLayoutFunction = $TodoType;
export type BusinessView = $TodoType;

export type LayoutChangedAction = { layout: Layout; type: "LAYOUT_CHANGED" }
export type TogglePanelAction = {type: "TOGGLE_LEFT_PANEL" | "TOGGLE_RIGHT_PANEL"}

export function layoutChanged(layout: Layout): LayoutChangedAction {
  return {
    type: "LAYOUT_CHANGED",
    layout: layout,
  }
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

export function businessViewChanged(value: BusinessView) {
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
