import _ from "lodash"
import {events} from "../../analytics/TrackingEvents"
import {isEdgeEditable} from "../../common/EdgeUtils"
import * as VisualizationUrl from "../../common/VisualizationUrl"
import history from "../../history"
import {CustomAction, Edge, GroupNodeType, NodeType} from "../../types"
import {WindowKind} from "../../windowManager/WindowKind"
import {ThunkAction} from "../reduxTypes"
import {EventInfo, reportEvent} from "./reportEvent"

export type DisplayModalNodeDetailsAction = {
  type: "DISPLAY_MODAL_NODE_DETAILS",
  nodeToDisplay: NodeType,
  nodeToDisplayReadonly: boolean,
}
export type DisplayModalEdgeDetailsAction = {
  type: "DISPLAY_MODAL_EDGE_DETAILS",
  edgeToDisplay: Edge,
}

export type ToggleCustomActionAction = {
  type: "TOGGLE_CUSTOM_ACTION",
  customAction: CustomAction,
}

export function displayModalNodeDetails(node: NodeType, readonly?: boolean, eventInfo?: EventInfo): ThunkAction {
  return (dispatch) => {
    history.replace({
      pathname: history.location.pathname,
      search: VisualizationUrl.setAndPreserveLocationParams({
        nodeId: node.id,
        edgeId: null,
      }),
    })

    !_.isEmpty(eventInfo) && dispatch(reportEvent({
      category: eventInfo.category,
      action: events.actions.buttonClick,
      name: eventInfo.name,
    }))

    return dispatch({
      type: "DISPLAY_MODAL_NODE_DETAILS",
      nodeToDisplay: node,
      nodeToDisplayReadonly: readonly,
    })
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
