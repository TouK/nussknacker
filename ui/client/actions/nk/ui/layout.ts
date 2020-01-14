import {events} from "../../../analytics/TrackingEvents"
import * as VisualizationUrl from "../../../common/VisualizationUrl"
import history from "../../../history"
import {ThunkAction} from "../../reduxTypes"
import {reportEvent} from "../reportEvent"

export type Layout = any;
export type GraphLayoutFunction = any;
export type BusinessView = any;

export function layoutChanged(layout: Layout) {
  return {
    type: "LAYOUT_CHANGED",
    layout: layout,
  }
}

export type TogglePanelAction = {
  type: "TOGGLE_LEFT_PANEL" | "TOGGLE_RIGHT_PANEL";
}

export function toggleLeftPanel() {
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
