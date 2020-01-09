import _ from "lodash"
import {events} from "../../analytics/TrackingEvents"
import * as VisualizationUrl from "../../common/VisualizationUrl"
import NodeUtils from "../../components/graph/NodeUtils"
import history from "../../history"
import {reportEvent} from "./reportEvent"

export function displayModalNodeDetails(node, readonly, eventInfo) {
  return (dispatch) => {
    history.replace({
      pathname: window.location.pathname,
      search: VisualizationUrl.setAndPreserveLocationParams({
        nodeId: node.id,
        edgeId: null,
      }),
    })

    !_.isEmpty(eventInfo) && dispatch(reportEvent({
      category: eventInfo.eventCategory,
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

export function displayModalEdgeDetails(edge) {
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

export function closeModals() {
  history.replace({
    pathname: window.location.pathname,
    search: VisualizationUrl.setAndPreserveLocationParams({
      edgeId: null,
      nodeId: null,
    }),
  })

  return {
    type: "CLOSE_MODALS",
  }
}

export function toggleModalDialog(openDialog) {
  return (dispatch) => {
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

export function toggleInfoModal(openDialog, text) {
  return {
    type: "TOGGLE_INFO_MODAL",
    openDialog: openDialog,
    text: text,
  }
}
