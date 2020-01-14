import {events} from "../../analytics/TrackingEvents"
import {reportEvent} from "./reportEvent"

export function zoomIn(graph) {
  return (dispatch) => {
    graph.zoomIn()

    dispatch(reportEvent({
      category: events.categories.rightPanel,
      action: events.actions.buttonClick,
      name: "zoom in",
    }))

    return dispatch({
      type: "ZOOM_IN",
    })
  }
}

export function zoomOut(graph) {
  return (dispatch) => {
    graph.zoomOut()

    dispatch(reportEvent({
      category: events.categories.rightPanel,
      action: events.actions.buttonClick,
      name: "zoom out",
    }))

    return dispatch({
      type: "ZOOM_OUT",
    })
  }
}
