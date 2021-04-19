import {events} from "../../analytics/TrackingEvents"
import NodeUtils from "../../components/graph/NodeUtils"
import HttpService from "../../http/HttpService"
import {getSelectedGroups} from "../../reducers/graph/utils"
import {getGraph} from "../../reducers/selectors/graph"
import {ThunkAction, Action} from "../reduxTypes"
import {GroupId, GroupType, NodeId, NodeType, Process, ValidationResult} from "../../types"
import {reportEvent} from "./reportEvent"

function withReportEvent(name: string, action: Action): ThunkAction {
  return (dispatch) => {
    dispatch(reportEvent({
      category: events.categories.rightPanel,
      action: events.actions.buttonClick,
      name: name,
    }))

    return dispatch(action)
  }
}

export const startGrouping = () => withReportEvent("start", {type: "START_GROUPING"})
export const cancelGrouping = () => withReportEvent("cancel", {type: "CANCEL_GROUPING"})
export const finishGrouping = () => withReportEvent("finish", {type: "FINISH_GROUPING"})
export const ungroup = (node: NodeType) => withReportEvent("ungroup", {type: "UNGROUP", groupToRemove: node.id})

export const groupSelected = () => withReportEvent("group selected", {type: "GROUP_SELECTED"})

export function ungroupSelected(): ThunkAction {
  return (dispatch, getState) => {
    dispatch(reportEvent({
      category: events.categories.rightPanel,
      action: events.actions.buttonClick,
      name: "ungroup selected",
    }))

    getSelectedGroups(getGraph(getState())).forEach(g => {
      dispatch({type: "UNGROUP", groupToRemove: g.id})
    })
  }
}

export type FinishGroupingAction = {type: "FINISH_GROUPING"}
export type AddNodeToGroupAction = {type: "ADD_NODE_TO_GROUP", nodeId: NodeId}
export type UnGroupAction = {type: "UNGROUP", groupToRemove: NodeId}

export function addToGroup(nodeId: NodeId): AddNodeToGroupAction {
  return {type: "ADD_NODE_TO_GROUP", nodeId: nodeId}
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
