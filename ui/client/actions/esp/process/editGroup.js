// @flow
import NodeUtils from "../../../components/graph/NodeUtils"
import HttpService from "../../../http/HttpService"
import type {ThunkAction} from "../../reduxTypes.flow"
import type {GroupId, GroupType, Process} from "../models.flow"

export type EditGroupAction = {
  type: "EDIT_GROUP",
  oldGroupId: GroupId,
  newGroup: GroupType,
  validationResult: $FlowTODO,
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