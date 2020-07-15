import {events} from "../../analytics/TrackingEvents"
import NodeUtils from "../../components/graph/NodeUtils"
import HttpService from "../../http/HttpService"
import {ThunkAction, Action} from "../reduxTypes"
import {GroupId, GroupType, NodeId, NodeType, Process, ValidationResult} from "../../types"
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

export type FinishGroupingAction = { type: "FINISH_GROUPING" }
export type AddNodeToGroupAction = { type: "ADD_NODE_TO_GROUP", nodeId: NodeId }
export type UnGroupAction = { type: "UNGROUP", groupToRemove: NodeId }

export function addToGroup(nodeId: NodeId): AddNodeToGroupAction {
  return {type: "ADD_NODE_TO_GROUP", nodeId: nodeId}
}

export function ungroup(node: NodeType): ThunkAction {
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
  id: GroupId,
}

export function expandGroup(id: GroupId): Action {
  return {type: "EXPAND_GROUP", id}
}

export function collapseGroup(id: GroupId): Action {
  return {type: "COLLAPSE_GROUP", id}
}

export function collapseAllGroups(): Action {
  return {type: "COLLAPSE_ALL_GROUPS"}
}

export type EditGroupAction = {
  type: "EDIT_GROUP",
  oldGroupId: GroupId,
  newGroup: GroupType,
  validationResult: ValidationResult,
}

export function editGroup(process: Process, oldGroupId: GroupId, newGroup: GroupType): ThunkAction {
  return (dispatch) => {
    const newProcess = NodeUtils.editGroup(process, oldGroupId, newGroup)
    return HttpService.validateProcess(newProcess).then((response) => {
      dispatch({
        type: "EDIT_GROUP",
        oldGroupId: oldGroupId,
        newGroup: newGroup,
        validationResult: response.data,
      })
    })
  }
}
