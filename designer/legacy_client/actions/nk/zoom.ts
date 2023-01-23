import {events} from "../../analytics/TrackingEvents"
import {reportEvent} from "./reportEvent"
import {Graph} from "../../components/graph/Graph"

export function zoomIn(graph: Graph) {
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

export function zoomOut(graph: Graph) {
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
