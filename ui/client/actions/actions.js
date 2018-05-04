import {browserHistory} from "react-router";
import HttpService from "../http/HttpService";
import * as GraphUtils from "../components/graph/GraphUtils";
import NodeUtils from "../components/graph/NodeUtils";
import * as SubprocessSchemaAligner from "../components/graph/SubprocessSchemaAligner";
import _ from "lodash";
import * as UndoRedoActions from "../undoredo/UndoRedoActions";
import * as VisualizationUrl from '../common/VisualizationUrl'

export function fetchProcessToDisplay(processId, versionId, businessView) {
  return (dispatch) => {
    dispatch({
      type: "PROCESS_LOADING"
    })
    return HttpService.fetchProcessDetails(processId, versionId, businessView)
      .then((processDetails) => {
        displayTestCapabilites(processDetails.json, processDetails.processingType)(dispatch)
        return dispatch(displayProcess(processDetails))
      })
  }
}

export function fetchProcessDefinition(processingType, isSubprocess, subprocessVersions) {
  return (dispatch) => {
    return HttpService.fetchProcessDefinitionData(processingType, isSubprocess, subprocessVersions).then((data) =>
      dispatch({type: "PROCESS_DEFINITION_DATA", processDefinitionData: data})
    )
  }
}

export function fetchAvailableQueryStates() {
  return (dispatch) => {
    return HttpService.availableQueryableStates().then((data) =>
      dispatch({type: "AVAILABLE_QUERY_STATES", availableQueryableStates: data})
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

export function saveProcess(processId, processJson, comment) {
  return (dispatch) => {
    return HttpService.saveProcess(processId, processJson, comment)
      .then((res) => dispatch(displayCurrentProcessVersion(processId)))
      .then((res) => dispatch(displayProcessActivity(processId)))
      .then((res) => dispatch(UndoRedoActions.clear()))
  }
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

export function displayModalNodeDetails(node, readonly) {
  browserHistory.replace({ pathname: window.location.pathname, search: VisualizationUrl.nodeIdPart(node.id)})
  return {
    type: "DISPLAY_MODAL_NODE_DETAILS",
    nodeToDisplay: node,
    nodeToDisplayReadonly: readonly
  };
}

export function displayModalEdgeDetails(edge) {
  browserHistory.replace({ pathname: window.location.pathname, search: VisualizationUrl.edgeIdPart(NodeUtils.edgeId(edge))})
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
  browserHistory.replace({ pathname: window.location.pathname, search: ''})
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

//TODO: is it ok how we process validations here? first we *simulate* reducer on
//current process (which may be outdated...) and after validation we invoke reducer
//this is error prone... :|

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
    const processAfterChange = calculateProcessAfterChange(process, before, after, dispatch)
    return processAfterChange.then((process) => {
      return HttpService.validateProcess(process).then((validationResult) => {
        dispatch({
          type: "EDIT_NODE",
          before: before,
          after: after,
          validationResult: validationResult,
          processAfterChange: process
        })
      })
    })
  }
}

function calculateProcessAfterChange(process, before, after, dispatch) {
  if (NodeUtils.nodeIsProperties(after)) {
    const subprocessVersions = after.subprocessVersions || process.properties.subprocessVersions
    return dispatch(
      fetchProcessDefinition(process.processingType, process.properties.isSubprocess, subprocessVersions)
    ).then((processDef) => {
      const processWithNewSubprocessSchema = alignSubprocessesWithSchema(process, processDef.processDefinitionData)
      return GraphUtils.mapProcessWithNewNode(processWithNewSubprocessSchema, before, after)
    })
  } else {
    return Promise.resolve(GraphUtils.mapProcessWithNewNode(process, before, after))
  }
}

function alignSubprocessesWithSchema(process, processDefinitionData) {
  const nodesWithNewSubprocessSchema = _.map(process.nodes, (node) => {
    if (node.type === "SubprocessInput") {
      return SubprocessSchemaAligner.alignSubprocessWithSchema(processDefinitionData, node)
    } else {
      return node
    }
  })
  return {...process, nodes: nodesWithNewSubprocessSchema};
}

export function editGroup(process, oldGroupId, newGroup) {
  return (dispatch) => {
    const newProcess = NodeUtils.editGroup(process, oldGroupId, newGroup)
    return HttpService.validateProcess(newProcess).then((validationResult) => {
      dispatch({
        type: "EDIT_GROUP",
        oldGroupId: oldGroupId,
        newGroup: newGroup,
        validationResult: validationResult
      })
    })
  }

}

export function nodesConnected(fromNode, toNode, processDefinitionData) {
  return {type: "NODES_CONNECTED", fromNode: fromNode, toNode: toNode, processDefinitionData: processDefinitionData}
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

export function toggleLeftPanel() {
  return {
    type: "TOGGLE_LEFT_PANEL",
  }
}

export function toggleRightPanel() {
  return {
    type: "TOGGLE_RIGHT_PANEL",
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

export function toggleModalDialog(openDialog) {
  return {
    type: "TOGGLE_MODAL_DIALOG",
    openDialog: openDialog
  }
}

export function toggleInfoModal(openDialog, text) {
  return {
    type: "TOGGLE_INFO_MODAL",
    openDialog: openDialog,
    text: text
  }
}

export function testProcessFromFile(id, testDataFile, process) {
  return (dispatch) => {
    dispatch({
      type: "PROCESS_LOADING"
    })
    HttpService.testProcess(id, testDataFile, process, testResults => dispatch(displayTestResults(testResults)),
      error => dispatch({
        type: "LOADING_FAILED"
      }));

  }
}

function displayTestResults(testResults) {
  return (dispatch) => {
    dispatch({
      type: "DISPLAY_TEST_RESULTS_DETAILS",
      testResults: testResults.results
    })
    dispatch({
        type: "DISPLAY_PROCESS_COUNTS",
        processCounts: testResults.counts
      }
    )
  }
}

export function displayProcessCounts(processCounts) {
  return {
    type: "DISPLAY_PROCESS_COUNTS",
    processCounts: processCounts
  }
}

export function hideRunProcessDetails() {
  return {type: "HIDE_RUN_PROCESS_DETAILS"}
}

export function expandGroup(id) {
  return {type: "EXPAND_GROUP", id: id}
}

export function collapseGroup(id) {
  return {type: "COLLAPSE_GROUP", id: id}
}

export function businessViewChanged(value) {
  return {
    type: "BUSINESS_VIEW_CHANGED",
    businessView: value
  }
}
