// @flow
import _ from "lodash"
import {ThunkDispatch} from "redux-thunk"
import {events} from "../../analytics/TrackingEvents"
import * as VisualizationUrl from "../../common/VisualizationUrl"
import NodeUtils from "../../components/graph/NodeUtils"
import type {DialogType} from "../../components/modals/Dialogs"
import history from "../../history"
import type {Action} from "../reduxTypes.flow"
import type {Edge, GroupId} from "./models.flow"
import type {EventInfo} from "./reportEvent"
import {reportEvent} from "./reportEvent"

export type DisplayModalNodeDetailsAction = {
  type: "DISPLAY_MODAL_NODE_DETAILS",
  nodeToDisplay: {
    id: GroupId;
  },
  nodeToDisplayReadonly: boolean,
}
export type DisplayModalEdgeDetailsAction = {
  type: "DISPLAY_MODAL_EDGE_DETAILS",
  edgeToDisplay: Edge,
}
export type ToggleModalDialogAction = {
  type: "TOGGLE_MODAL_DIALOG",
  openDialog: DialogType,
}
export type ToggleInfoModalAction = {
  type: "TOGGLE_INFO_MODAL",
  openDialog: DialogType,
  text: string,
}

export function displayModalNodeDetails(node: {
  id: GroupId;
}, readonly: boolean, eventInfo: EventInfo) {
  return (dispatch: ThunkDispatch<Action>) => {
    history.replace({
      pathname: window.location.pathname,
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

export function displayModalEdgeDetails(edge: Edge) {
  history.replace({
    pathname: window.location.pathname,
    search: VisualizationUrl.setAndPreserveLocationParams({
      nodeId: null,
      edgeId: NodeUtils.edgeId(edge),
    }),
  })

  return {
    type: "DISPLAY_MODAL_EDGE_DETAILS",
    edgeToDisplay: edge,
  }
}

export function toggleModalDialog(openDialog: string) {
  return (dispatch: ThunkDispatch<Action>) => {
    openDialog != null && dispatch(reportEvent({
          category: "right_panel",
          action: "button_click",
          name: openDialog.toLowerCase(),
        },
    ))

    return dispatch({
      type: "TOGGLE_MODAL_DIALOG",
      openDialog: openDialog,
    })
  }
}

export function toggleInfoModal(openDialog: string, text: string) {
  return {
    type: "TOGGLE_INFO_MODAL",
    openDialog: openDialog,
    text: text,
  }
}