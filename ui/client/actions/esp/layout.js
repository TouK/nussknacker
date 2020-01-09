import {events} from "../../analytics/TrackingEvents"
import * as VisualizationUrl from "../../common/VisualizationUrl"
import history from "../../history"
import {reportEvent} from "./reportEvent"

export function layoutChanged(layout) {
  return {
    type: "LAYOUT_CHANGED",
    layout: layout,
  }
}

export function toggleLeftPanel() {
  return {
    type: "TOGGLE_LEFT_PANEL",
  }
}

export function toggleRightPanel() {
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

export function layout(graphLayoutFunction) {
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

export function businessViewChanged(value) {
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
