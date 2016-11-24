import HttpService from "../http/HttpService";
import GraphUtils from "../components/graph/GraphUtils";

export function fetchProcessToDisplay(processId, versionId) {
  return (dispatch) => {
    dispatch({
      type: "FETCH_PROCESS_TO_DISPLAY"
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

export function displayProcess(processDetails) {
  return {
    type: "DISPLAY_PROCESS",
    fetchedProcessDetails: processDetails
  };
}

export function clearProcess() {
  return (dispatch) => {
    dispatch(clear())
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

//fixme to nie powinno tu byc, powinno byc wstrzykiwane jakos z espUndoable
export function undo() {
  return {type: "UNDO"};
}

export function redo() {
  return {type: "REDO"};
}

export function clear() {
  return {type: "CLEAR"};
}