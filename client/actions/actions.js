import HttpService from "../http/HttpService";
import GraphUtils from "../components/graph/GraphUtils";
import * as UndoRedoActions from "../undoredo/UndoRedoActions";

export function fetchProcessToDisplay(processId, versionId) {
  return (dispatch) => {
    dispatch({
      type: "PROCESS_LOADING"
    })
    return HttpService.fetchProcessDetails(processId, versionId)
      .then((processDetails) => {
        displayTestCapabilites(processDetails.json, processDetails.processingType)(dispatch)
        return dispatch(displayProcess(processDetails))
      })
  }
}

export function fetchProcessDefinition(processingType) {
  return (dispatch) => {
    HttpService.fetchProcessDefinitionData(processingType).then((data) =>
      dispatch({type: "PROCESS_DEFINITION_DATA", processDefinitionData: data})
    )
  }
}

export function displayCurrentProcessVersion(processId) {
  return fetchProcessToDisplay(processId)
}

export function addComment(processId, processVersionId, comment) {
  return (dispatch) => {
    return HttpService.addComment(processId, processVersionId, comment).then(() => {
      return dispatch(displayProcessActivity(processId))
    })
  }
}

export function deleteComment(processId, commentId) {
  return (dispatch) => {
    return HttpService.deleteComment(processId, commentId).then(() => {
      return dispatch(displayProcessActivity(processId))
    })
  }
}

export function addAttachment(processId, processVersionId, comment) {
  return (dispatch) => {
    return HttpService.addAttachment(processId, processVersionId, comment).then(() => {
      return dispatch(displayProcessActivity(processId))
    })
  }
}

export function displayProcessActivity(processId) {
  return (dispatch) => {
    return HttpService.fetchProcessActivity(processId).then((activity) => {
      return dispatch({
        type: "DISPLAY_PROCESS_ACTIVITY",
        comments: activity.comments,
        attachments: activity.attachments
      })
    })
  }
}

function displayTestCapabilites(processDetails) {
  return (dispatch) => {
    HttpService.getTestCapabilities(processDetails).then((capabilites) => dispatch({
      type: "UPDATE_TEST_CAPABILITIES",
      capabilities: capabilites
    }))

  }

}

function displayProcess(processDetails) {
  return {
    type: "DISPLAY_PROCESS",
    fetchedProcessDetails: processDetails
  };
}

export function importProcess(processId, file) {
  return (dispatch) => {
    dispatch({
      type: "PROCESS_LOADING"
    })
    return HttpService.importProcess(processId, file, process => dispatch(updateImportedProcess(process)),
      error => dispatch({
        type: "LOADING_FAILED"
      }))
  }
}

export function updateImportedProcess(processJson) {
  return {
    type: "UPDATE_IMPORTED_PROCESS",
    processJson: processJson
  };
}

export function clearProcess() {
  return (dispatch) => {
    dispatch(UndoRedoActions.clear())
    return dispatch({
      type: "CLEAR_PROCESS"
    })
  }
}

export function displayModalNodeDetails(node) {
  return {
    type: "DISPLAY_MODAL_NODE_DETAILS",
    nodeToDisplay: node
  };
}

export function displayModalEdgeDetails(edge) {
  return {
    type: "DISPLAY_MODAL_EDGE_DETAILS",
    edgeToDisplay: edge
  };
}

export function displayNodeDetails(node) {
  return {
    type: "DISPLAY_NODE_DETAILS",
    nodeToDisplay: node
  };
}

export function closeModals() {
  return {
    type: "CLOSE_MODALS"
  };
}

export function deleteNode(id) {
  return {
    type: "DELETE_NODE",
    id: id
  }
}

export function startGrouping() {
  return { type: "START_GROUPING"}
}

export function cancelGrouping() {
  return { type: "CANCEL_GROUPING"}
}

export function finishGrouping() {
  return { type: "FINISH_GROUPING"}
}

export function addToGroup(nodeId) {
  return { type: "ADD_NODE_TO_GROUP", nodeId: nodeId}
}

export function ungroup(node) {
  return { type: "UNGROUP", groupToRemove: node.id}
}

export function editEdge(process, before, after) {
  return (dispatch) => {
    const changedProcess = GraphUtils.mapProcessWithNewEdge(process, before, after)
    return HttpService.validateProcess(changedProcess).then((validationResult) => {
      dispatch({
        type: "EDIT_EDGE",
        before: before,
        after: after,
        validationResult: validationResult
      })
    })
  }
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
    }, Promise.reject)
  }
}

export function editGroup(oldGroupId, newGroup) {
  return {
    type: "EDIT_GROUP",
    oldGroupId: oldGroupId,
    newGroup: newGroup
  }

}

export function nodesConnected(fromNode, toNode, edgeTypes) {
  return {type: "NODES_CONNECTED", fromNode: fromNode, toNode: toNode, edgeTypes: edgeTypes}
}
export function nodesDisconnected(from, to) {
  return {type: "NODES_DISCONNECTED", from: from, to: to}
}
export function nodeAdded(node, position) {

  return {
    type: "NODE_ADDED",
    node: node,
    position: position
  }
}

export function layoutChanged(layout) {
  return {
    type: "LAYOUT_CHANGED",
    layout: layout
  }
}

export function toggleLeftPanel(isOpened) {
  return {
    type: "TOGGLE_LEFT_PANEL",
    leftPanelIsOpened: isOpened
  }
}

export function toggleConfirmDialog(isOpen, text, action) {
  return {
    type: "TOGGLE_CONFIRM_DIALOG",
    isOpen: isOpen,
    text: text,
    onConfirmCallback: action
  }
}

export function testProcessFromFile(id, testDataFile, processJson) {
  return (dispatch) => {
    dispatch({
      type: "PROCESS_LOADING"
    })
    HttpService.testProcess(id, testDataFile, processJson, testResults => dispatch(displayTestResults(testResults)),
      error => dispatch({
        type: "LOADING_FAILED"
      }));

  }
}

export function displayTestResults(testResults) {
  return {
    type: "DISPLAY_TEST_RESULTS",
    testResults: testResults
  }
}

export function hideTestResults() {
  return {type: "HIDE_TEST_RESULTS"}
}

export function expandGroup(id) {
  return {type: "EXPAND_GROUP", id: id}
}

export function collapseGroup(id) {
  return {type: "COLLAPSE_GROUP", id: id}
}
