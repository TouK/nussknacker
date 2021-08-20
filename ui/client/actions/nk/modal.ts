import {isEdgeEditable} from "../../common/EdgeUtils"
import {CustomAction, Edge, NodeType} from "../../types"
import {ThunkAction} from "../reduxTypes"

export type DisplayModalNodeDetailsAction = {
  type: "DISPLAY_MODAL_NODE_DETAILS",
  nodeToDisplay: NodeType,
  nodeToDisplayReadonly?: boolean,
}
export type DisplayModalEdgeDetailsAction = {
  type: "DISPLAY_MODAL_EDGE_DETAILS",
  edgeToDisplay: Edge,
}

export type ToggleCustomActionAction = {
  type: "TOGGLE_CUSTOM_ACTION",
  customAction: CustomAction,
}

export function displayModalNodeDetails(node: NodeType): DisplayModalNodeDetailsAction {
  return {
    type: "DISPLAY_MODAL_NODE_DETAILS",
    nodeToDisplay: node,
  }
}

export function displayModalEdgeDetails(edge: Edge): ThunkAction {
  return (dispatch, getState) => {
    // const {edgeId = []} = getWindowsFromQuery(getState())
    if (isEdgeEditable(edge)) {
      // history.replace({
      //   pathname: history.location.pathname,
      //   search: VisualizationUrl.setAndPreserveLocationParams({
      //     // nodeId: null,
      //     edgeId: [...edgeId, NodeUtils.edgeId(edge)],
      //   }),
      // })
      return dispatch({
        type: "DISPLAY_MODAL_EDGE_DETAILS",
        edgeToDisplay: edge,
      })
    }
  }
}
