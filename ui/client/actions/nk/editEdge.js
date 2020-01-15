import * as GraphUtils from "../../components/graph/GraphUtils"
import HttpService from "../../http/HttpService"

export function editEdge(process, before, after) {
  return (dispatch) => {
    const changedProcess = GraphUtils.mapProcessWithNewEdge(process, before, after)
    return HttpService.validateProcess(changedProcess).then((response) => {
      dispatch({
        type: "EDIT_EDGE",
        before: before,
        after: after,
        validationResult: response.data,
      })
    })
  }
}
