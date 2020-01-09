import {events} from "../../analytics/TrackingEvents"
import {reportEvent} from "./reportEvent"

export function startGrouping() {
  return (dispatch) => {
    dispatch(reportEvent({
      category: events.categories.rightPanel,
      action: events.actions.buttonClick,
      name: "start",
    }))

    return dispatch({
      type: "START_GROUPING",
    })
  }
}

export function cancelGrouping() {
  return (dispatch) => {
    dispatch(reportEvent({
      category: events.categories.rightPanel,
      action: events.actions.buttonClick,
      name: "cancel",
    }))

    return dispatch({
      type: "CANCEL_GROUPING",
    })
  }
}

export function finishGrouping() {
  return (dispatch) => {
    dispatch(reportEvent({
      category: events.categories.rightPanel,
      action: events.actions.buttonClick,
      name: "finish",
    }))

    return dispatch({
      type: "FINISH_GROUPING",
    })
  }
}

export function addToGroup(nodeId) {
  return {type: "ADD_NODE_TO_GROUP", nodeId: nodeId}
}

export function ungroup(node) {
  return (dispatch) => {
    dispatch(reportEvent({
      category: events.categories.rightPanel,
      action: events.actions.buttonClick,
      name: "ungroup",
    }))

    return dispatch({
      type: "UNGROUP",
      groupToRemove: node.id,
    })
  }
}

export function expandGroup(id) {
  return {type: "EXPAND_GROUP", id: id}
}

export function collapseGroup(id) {
  return {type: "COLLAPSE_GROUP", id: id}
}
