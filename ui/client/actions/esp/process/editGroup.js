import NodeUtils from "../../../components/graph/NodeUtils"
import HttpService from "../../../http/HttpService"

export function editGroup(process, oldGroupId, newGroup) {
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
