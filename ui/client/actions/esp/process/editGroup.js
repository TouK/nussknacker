// @flow
import {ThunkAction} from "redux-thunk"
import NodeUtils from "../../../components/graph/NodeUtils"
import HttpService from "../../../http/HttpService"
import type {Action} from "../../types"
import type {GroupId, GroupType, Process} from "../types"

export type EditGroupAction = {
  type: "EDIT_GROUP",
  oldGroupId: GroupId,
  newGroup: GroupType,
  validationResult: any,
}

export function editGroup(process: Process, oldGroupId: GroupId, newGroup: GroupType) {
  return (dispatch: ThunkAction<Action>) => {
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
