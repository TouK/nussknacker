import {Graph} from "../../components/graph/Graph"

export function zoomIn(graph: Graph) {
  return (dispatch) => {
    graph.zoomIn()
    return dispatch({
      type: "ZOOM_IN",
    })
  }
}

export function zoomOut(graph: Graph) {
  return (dispatch) => {
    graph.zoomOut()
    return dispatch({
      type: "ZOOM_OUT",
    })
  }
}
