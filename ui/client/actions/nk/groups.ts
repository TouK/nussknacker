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

export const groupSelected = () => withReportEvent("group", {type: "GROUP"})

export function ungroupSelected(): ThunkAction {
  return (dispatch, getState) => {
    dispatch(reportEvent({
      category: events.categories.rightPanel,
      action: events.actions.buttonClick,
      name: "ungroup",
    }))

    getSelectedGroups(getGraph(getState())).forEach(({id}) => {
      // delay action to avoid view flickering
      setTimeout(() => {
        dispatch({type: "EXPAND_GROUP", id: id})
        dispatch({type: "UNGROUP", groupToRemove: id})
      })
    })
  }
}

export type UnGroupAction = {type: "UNGROUP", groupToRemove: NodeId}

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
