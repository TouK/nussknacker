import HttpService from '../http/HttpService'
import GraphUtils from '../components/graph/GraphUtils'
import * as ProcessToDisplayMode from '../constants/ProcessToDisplayMode'

export function displayProcess(processDetails) {
  return {
    type: "DISPLAY_PROCESS",
    fetchedProcessDetails: processDetails,
    processToDisplayMode: ProcessToDisplayMode.CURRENT
  };
}

export function displayDeployedProcess(processDetails) {
  return {
    type: "DISPLAY_PROCESS",
    fetchedProcessDetails: processDetails,
    processToDisplayMode: ProcessToDisplayMode.DEPLOYED
  };
}


export function displayNodeDetails(node) {
  return {
    type: "DISPLAY_NODE_DETAILS",
    nodeToDisplay: node
  };
}

export function closeNodeDetails() {
  return {
    type: "CLOSE_NODE_DETAILS"
  };
}

export function editNode(process, before, after) {
  return (dispatch) => {
    const changedProcess = GraphUtils.mapProcessWithNewNode(process, before, after)
    return HttpService.validateProcess(changedProcess).then((validationResult) => {
      dispatch({
        type: "EDIT_NODE",
        before: before,
        after: after,
        validationResult: validationResult
      })
    })
  }
}

//fixme to nie powinno tu byc, powinno byc wstrzykiwane jakos z espUndoable
export function undo() {
  return { type: "UNDO"};
}

export function redo() {
  return { type: "REDO"};
}
