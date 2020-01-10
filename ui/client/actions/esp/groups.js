// @flow
import {events} from "../../analytics/TrackingEvents"
import type {ThunkAction} from "../reduxTypes.flow"
import type {GroupId} from "./models.flow"
import {reportEvent} from "./reportEvent"

export function startGrouping(): ThunkAction {
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

export function cancelGrouping(): ThunkAction {
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

export function finishGrouping(): ThunkAction {
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

export type AddNodeToGroupAction = { type: "ADD_NODE_TO_GROUP", nodeId: GroupId }
export type UnGroupAction = { type: "UNGROUP", groupToRemove: GroupId }

export function addToGroup(nodeId: GroupId): AddNodeToGroupAction {
  return {type: "ADD_NODE_TO_GROUP", nodeId: nodeId}
}

export function ungroup(node: {
  id: GroupId;
}): ThunkAction {
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

export type ToggleGroupAction = {
  type: "EXPAND_GROUP" | "COLLAPSE_GROUP",
  id: GroupId
}

export function expandGroup(id: GroupId) {
  return {type: "EXPAND_GROUP", id}
}

export function collapseGroup(id: GroupId) {
  return {type: "COLLAPSE_GROUP", id}
}