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
        return dispatch(displayProcess(processDetails))
      })
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

export function displayProcessActivity(processId) {
  return (dispatch) => {
    return HttpService.fetchProcessActivity(processId).then((activity) => {
      return dispatch({
        type: "DISPLAY_PROCESS_ACTIVITY",
        comments: activity.comments
      })
    })
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

export function deleteNode(id) {
  return {
    type: "DELETE_NODE",
    id: id
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
    })
  }
}

export function nodesConnected(from, to) {
  return {type: "NODES_CONNECTED", from: from, to: to}
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

export function testProcessFromFile(id, testDataFile) {
  return (dispatch) => {
    dispatch({
      type: "PROCESS_LOADING"
    })
    HttpService.testProcess(id, testDataFile, testResults => dispatch(displayTestResults(testResults)),
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
  return (dispatch) => {
    dispatch({type: "HIDE_TEST_RESULTS"})
  }
}