import {events} from "../../analytics/TrackingEvents"
import NodeUtils from "../../components/graph/NodeUtils"
import HttpService from "../../http/HttpService"
import {getSelectedGroups} from "../../reducers/graph/utils"
import {getGraph} from "../../reducers/selectors/graph"
import {getGroups} from "../../reducers/selectors/groups"
import {GroupId, GroupNodeType, GroupType, NodeId, Process} from "../../types"
import {Action, ThunkAction} from "../reduxTypes"
import {reportEvent} from "./reportEvent"
import {resetSelection} from "./selection"

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

export const groupSelected = () => withReportEvent(`group`, {type: "GROUP"})

const delay = (t = 0) => new Promise(resolve => {setTimeout(resolve, t)})

export function ungroupSelected(): ThunkAction {
  return (dispatch, getState) => {
    dispatch(reportEvent({
      category: events.categories.rightPanel,
      action: events.actions.buttonClick,
      name: "ungroup",
    }))

    return dispatch(ungroup(getSelectedGroups(getGraph(getState())).map(g => g.id)))
  }
}

export function ungroup(selectedGroups: Array<GroupType["id"]>): ThunkAction {
  return async (dispatch) => {
    await Promise.all(selectedGroups.map(async id => {dispatch({type: "EXPAND_GROUP", id: id})}))
    // delay action to avoid view flickering
    await delay()
    return Promise.all(selectedGroups.map(async id => {
      dispatch({type: "UNGROUP", groupToRemove: id})
    }))
  }
}

export type UnGroupAction = {type: "UNGROUP", groupToRemove: NodeId}

export type ToggleGroupAction = {
  type: "EXPAND_GROUP" | "COLLAPSE_GROUP",
  id: GroupId,
}

export function expandGroup(id: GroupId): ThunkAction {
  return (dispatch, getState) => {
    const group = getGroups(getState()).find(g => g.id === id)

    dispatch({type: "EXPAND_GROUP", id})
    dispatch(resetSelection(id, ...group.nodes))
  }
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
  newGroup: GroupNodeType,
}

export function editGroup(process: Process, oldGroupId: GroupId, newGroup: GroupNodeType): ThunkAction {
  return async (dispatch) => {
    const newProcess = NodeUtils.editGroup(process, oldGroupId, newGroup)
    await dispatch(validateProcess(newProcess))
    return dispatch({
      type: "EDIT_GROUP",
      oldGroupId: oldGroupId,
      newGroup: newGroup,
    })
  }
}

export function validateProcess(process: Process): ThunkAction {
  return async (dispatch) => {
    dispatch({
      type: "VALIDATION_STARTED",
      process,
    })
    return HttpService.validateProcess(process).then(({data}) => {
      dispatch({
        type: "VALIDATION_RESULT",
        validationResult: data,
      })
    })
  }
}
