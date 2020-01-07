import HttpService from "../http/HttpService";
import * as GraphUtils from "../components/graph/GraphUtils";
import NodeUtils from "../components/graph/NodeUtils";
import * as SubprocessSchemaAligner from "../components/graph/SubprocessSchemaAligner";
import _ from "lodash";
import * as UndoRedoActions from "./undoRedoActions";
import * as VisualizationUrl from "../common/VisualizationUrl";
import {dateFormat} from "../config";
import history from "../history"
import User from "../common/models/User";
import Metrics from "../containers/Metrics"
import {events} from "../analytics/TrackingEvents"

export function fetchProcessToDisplay(processId, versionId, businessView) {
  return (dispatch) => {
    dispatch({
      type: "PROCESS_LOADING"
    })
    return HttpService.fetchProcessDetails(processId, versionId, businessView).then((response) => {
        displayTestCapabilites(response.data.json, response.data.processingType)(dispatch)
        return dispatch(displayProcess(response.data))
      })
  }
}

export function fetchProcessDefinition(processingType, isSubprocess, subprocessVersions) {
  return (dispatch) => {
    return HttpService.fetchProcessDefinitionData(processingType, isSubprocess, subprocessVersions).then((response) => (
        dispatch({type: "PROCESS_DEFINITION_DATA", processDefinitionData: response.data})
      )
    )
  }
}

export function fetchAvailableQueryStates() {
  return (dispatch) => {
    return HttpService.availableQueryableStates().then((response) =>
      dispatch({type: "AVAILABLE_QUERY_STATES", availableQueryableStates: response.data})
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
    return HttpService.fetchProcessActivity(processId).then((response) => {
      return dispatch({
        type: "DISPLAY_PROCESS_ACTIVITY",
        comments: response.data.comments,
        attachments: response.data.attachments
      })
    })
  }
}

function displayTestCapabilites(processDetails) {
  return (dispatch) => {
    HttpService.getTestCapabilities(processDetails).then((response) => dispatch({
      type: "UPDATE_TEST_CAPABILITIES",
      capabilities: response.data
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
      .then((response) => dispatch(displayCurrentProcessVersion(processId)))
      .then((response) => dispatch(displayProcessActivity(processId)))
      .then((response) => dispatch(UndoRedoActions.clear()))
  }
}

export function importProcess(processId, file) {
  return (dispatch) => {
    dispatch({
      type: "PROCESS_LOADING"
    })

    return HttpService.importProcess(processId, file)
      .then((process) => dispatch(updateImportedProcess(process.data)))
      .catch((error) => dispatch({type: "LOADING_FAILED"}))
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

export function displayModalNodeDetails(node, readonly, eventInfo) {
  return (dispatch) => {
    history.replace({
      pathname: window.location.pathname,
      search: VisualizationUrl.setAndPreserveLocationParams({
        nodeId: node.id,
        edgeId: null
      })
    })

    !_.isEmpty(eventInfo) && dispatch(reportEvent({
      category: eventInfo.eventCategory,
      action: events.actions.buttonClick,
      name: eventInfo.name
    }))

    return dispatch({
      type: "DISPLAY_MODAL_NODE_DETAILS",
      nodeToDisplay: node,
      nodeToDisplayReadonly: readonly
    })
  }
}

export function displayModalEdgeDetails(edge) {
  history.replace({
    pathname: window.location.pathname,
    search: VisualizationUrl.setAndPreserveLocationParams({
      nodeId: null,
      edgeId: NodeUtils.edgeId(edge)
    })
  })

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
  history.replace({
    pathname: window.location.pathname,
    search: VisualizationUrl.setAndPreserveLocationParams({
      edgeId: null,
      nodeId: null
    })
  })

  return {
    type: "CLOSE_MODALS"
  };
}

export function deleteNodes(ids) {
  return runSyncActionsThenValidate(state => [{
    type: "DELETE_NODES",
    ids: ids
  }])
}

export function startGrouping() {
  return (dispatch) => {
    dispatch(reportEvent({
      category: events.categories.rightPanel,
      action: events.actions.buttonClick,
      name: "start"
    }))

    return dispatch({
      type: "START_GROUPING"
    })
  }
}

export function cancelGrouping() {
  return (dispatch) => {
    dispatch(reportEvent({
      category: events.categories.rightPanel,
      action: events.actions.buttonClick,
      name: "cancel"
    }))

    return dispatch({
      type: "CANCEL_GROUPING"
    })
  }
}

export function finishGrouping() {
  return (dispatch) => {
    dispatch(reportEvent({
      category: events.categories.rightPanel,
      action: events.actions.buttonClick,
      name: "finish"
    }))

    return dispatch({
      type: "FINISH_GROUPING"
    })
  }
}

export function addToGroup(nodeId) {
  return {type: "ADD_NODE_TO_GROUP", nodeId: nodeId}
}

export function ungroup(node) {
  return (dispatch) => {
    dispatch(reportEvent({
      category: events.categories.rightPanel,
      action: events.actions.buttonClick,
      name: "ungroup"
    }))

    return dispatch({
      type: "UNGROUP",
      groupToRemove: node.id
    })
  }
}

export function expandSelection(nodeId) {
  return {type: "EXPAND_SELECTION", nodeId}
}

export function resetSelection(nodeId) {
  return {type: "RESET_SELECTION", nodeId}
}

//TODO: is it ok how we process validations here? first we *simulate* reducer on
//current process (which may be outdated...) and after validation we invoke reducer
//this is error prone... :|

export function editEdge(process, before, after) {
  return (dispatch) => {
    const changedProcess = GraphUtils.mapProcessWithNewEdge(process, before, after)
    return HttpService.validateProcess(changedProcess).then((response) => {
      dispatch({
        type: "EDIT_EDGE",
        before: before,
        after: after,
        validationResult: response.data
      })
    })
  }
}


export function editNode(process, before, after) {
  return (dispatch) => {
    const processAfterChange = calculateProcessAfterChange(process, before, after, dispatch)
    return processAfterChange.then((process) => {
      return HttpService.validateProcess(process).then((response) => {
        dispatch({
          type: "EDIT_NODE",
          before: before,
          after: after,
          validationResult: response.data,
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
    return HttpService.validateProcess(newProcess).then((response) => {
      dispatch({
        type: "EDIT_GROUP",
        oldGroupId: oldGroupId,
        newGroup: newGroup,
        validationResult: response.data
      })
    })
  }

}

export function nodesConnected(fromNode, toNode) {
  return runSyncActionsThenValidate(state => [
    {
        type: "NODES_CONNECTED",
        fromNode: fromNode,
        toNode: toNode,
        processDefinitionData: state.settings.processDefinitionData
    }
  ]);
}

export function nodesDisconnected(from, to) {
  return runSyncActionsThenValidate(state => [{
      type: "NODES_DISCONNECTED",
      from: from,
      to: to
    }]);
}

export function injectNode(from, middle, to, edgeType) {
  return runSyncActionsThenValidate(state => [
    {
        type: "NODES_DISCONNECTED",
        from: from.id,
        to: to.id
    },
    {
        type: "NODES_CONNECTED",
        fromNode: from,
        toNode: middle,
        processDefinitionData: state.settings.processDefinitionData,
        edgeType: edgeType
    },
    {
        type: "NODES_CONNECTED",
        fromNode: middle,
        toNode: to,
        processDefinitionData: state.settings.processDefinitionData
    }
  ])
}

//this WON'T work for async actions - have to handle promises separately
function runSyncActionsThenValidate(syncActions) {
  return (dispatch, getState) => {
    syncActions(getState()).forEach(action => dispatch(action))
    return HttpService.validateProcess(getState().graphReducer.processToDisplay).then(
      (response) => dispatch({type: "VALIDATION_RESULT", validationResult: response.data})
    )
  }
}

export function nodeAdded(node, position) {
  return {
    type: "NODE_ADDED",
    node: node,
    position: position
  }
}

export function nodesWithEdgesAdded(nodesWithPositions, edges) {
  return (dispatch, getState) => dispatch({
    type: "NODES_WITH_EDGES_ADDED",
    nodesWithPositions,
    edges,
    processDefinitionData: getState().settings.processDefinitionData
  })
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
  return (dispatch) => {
    dispatch({
      type: "TOGGLE_RIGHT_PANEL",
    })

    dispatch(reportEvent({
      category: events.categories.rightPanel,
      action: events.actions.buttonClick,
      name: "toggle_right_panel"
    }))
  }
}

export function enableToolTipsHighlight() {
  return {
    type: "SWITCH_TOOL_TIPS_HIGHLIGHT",
    isHighlighted: true
  }
}

export function disableToolTipsHighlight() {
  return {
    type: "SWITCH_TOOL_TIPS_HIGHLIGHT",
    isHighlighted: false
  }
}

export function toggleConfirmDialog(isOpen, text, action, confirmText = "Yes", denyText = "No", event) {
  return (dispatch) => {
    !_.isEmpty(event) && dispatch(reportEvent(
      {
        category: event.category,
        action: event.action,
        name: event.name
      }
    ))

    return dispatch({
      type: "TOGGLE_CONFIRM_DIALOG",
      isOpen: isOpen,
      text: text,
      confirmText: confirmText,
      denyText: denyText,
      onConfirmCallback: action
    })
  }
}

export function toggleProcessActionDialog(message, action, displayWarnings) {
  return (dispatch) => {
    dispatch(reportEvent({
      category: "right_panel",
      action: "button_click",
      name: message
    }))

    return dispatch({
      type: "TOGGLE_PROCESS_ACTION_MODAL",
      message: message,
      action: action,
      displayWarnings: displayWarnings
    })
  }
}


export function toggleModalDialog(openDialog) {
  return (dispatch) => {
    openDialog != null && dispatch(reportEvent({
        category: "right_panel",
        action: "button_click",
        name: openDialog.toLowerCase()
      }
    ))

    return dispatch({
      type: "TOGGLE_MODAL_DIALOG",
      openDialog: openDialog
    })
  }
}

export function toggleInfoModal(openDialog, text) {
  return {
    type: "TOGGLE_INFO_MODAL",
    openDialog: openDialog,
    text: text
  }
}

export function assignUser(data) {
  return {
    type: "LOGGED_USER",
    user: new User(data)
  }
}

export function assignSettings(settings) {
  return {
    type: "UI_SETTINGS",
    settings: settings
  }
}

export function testProcessFromFile(id, testDataFile, process) {
  return (dispatch) => {
    dispatch({
      type: "PROCESS_LOADING"
    })

    dispatch(reportEvent({
        category: events.categories.rightPanel,
        action: events.actions.buttonClick,
        name: "from file"
      }
    ))

    HttpService.testProcess(id, testDataFile, process)
      .then(response => dispatch(displayTestResults(response.data)))
      .catch(error => dispatch({type: "LOADING_FAILED"}))
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

export function urlChange(location) {

  return (dispatch) => {
    dispatch(handleHTTPError(null))

    dispatch({
      type: "URL_CHANGED",
      location: location
    })
  }
}

export function fetchAndDisplayProcessCounts(processName, from, to) {
  return (dispatch) =>
    HttpService.fetchProcessCounts(
      processName,
      from ? from.format(dateFormat): null,
      to ? to.format(dateFormat) : null
    ).then((response) => dispatch(displayProcessCounts(response.data)))
}

export function hideRunProcessDetails() {
  return (dispatch) => {
    dispatch(reportEvent({
        category: events.categories.rightPanel,
        action: events.actions.buttonClick,
        name: "hide"
      }
    ))

    return dispatch({
      type: "HIDE_RUN_PROCESS_DETAILS"
    })
  }
}

export function expandGroup(id) {
  return {type: "EXPAND_GROUP", id: id}
}

export function collapseGroup(id) {
  return {type: "COLLAPSE_GROUP", id: id}
}

export function businessViewChanged(value) {
  history.replace({
    pathname: window.location.pathname,
    search: VisualizationUrl.setAndPreserveLocationParams({
      businessView: value
    })
  })

  return {
    type: "BUSINESS_VIEW_CHANGED",
    businessView: value
  }
}

//Developers can handle error on two ways:
//1. Catching it at HttpService and show error information modal - application still works normally
//2. Catching it at Containers / etc.. and show ErrorPage by run action handleHTTPError - application stop works
export function handleHTTPError(error) {
  return {
    type: "HANDLE_HTTP_ERROR",
    error: error
  }
}

export function showMetrics(processId) {
  return (dispatch) => {
    history.push(Metrics.pathForProcess(processId))

    dispatch(reportEvent({
      category: "right_panel",
      action: "button_click",
      name: "metrics"
    }))

    return dispatch({
      type: "SHOW_METRICS",
      processId: processId
    })
  }
}

export function reportEvent(eventInfo) {
  return (dispatch) => {
    return dispatch({
      type: "USER_TRACKING",
      tracking: {
        event: {
          e_c: eventInfo.category,
          e_a: eventInfo.action,
          e_n: eventInfo.name
        }
      }
    })
  }
}


export function importFiles(files, processId) {
  return (dispatch) => {
    files.forEach(
      file => dispatch(importProcess(processId, file))
    )

    return ({
      type: "IMPORT_FILES"
    })
  }
}

export function exportProcessToJSON(process, versionId) {
  return (dispatch) => {
    HttpService.exportProcess(process, versionId)

    dispatch(reportEvent({
      category: "right_panel",
      action: "button_click",
      name: "export_to_json"
    }))

    return dispatch({
      type: "EXPORT_PROCESS_TO_JSON"
    })
  }
}

export function exportProcessToPdf(processId, versionId, data, businessView) {
  return (dispatch) => {
    HttpService.exportProcessToPdf(processId, versionId, data, businessView)

    dispatch(reportEvent({
      category: "right_panel",
      action: "button_click",
      name: "export_to_pdf"
    }))

    return dispatch({
      type: "EXPORT_PROCESS_TO_PDF"
    })
  }
}

export function layout(graphLayoutFunction) {
  return (dispatch) => {
    graphLayoutFunction()

    dispatch(reportEvent({
      category: "right_panel",
      action: "button_click",
      name: "layout"
    }))

    return dispatch({
      type: "LAYOUT"
    })
  }
}

export function copySelection(copyFunction, event) {
  return (dispatch) => {
    copyFunction()

    dispatch(reportEvent({
      category: event.category,
      action: event.action,
      name: "copy"
    }))

    return dispatch({
      type: "COPY_SELECTION",
    })
  }
}

export function cutSelection(cutFunction, event) {
  return (dispatch) => {
    cutFunction()

    dispatch(reportEvent({
      category: event.category,
      action: event.action,
      name: "cut"
    }))

    return dispatch({
      type: "CUT_SELECTION"
    })
  }
}

export function pasteSelection(pasteFunction, event) {
  return (dispatch) => {
    pasteFunction()

    dispatch(reportEvent({
      category: event.category,
      action: event.action,
      name: "paste"
    }))

    return dispatch({
      type: "PASTE_SELECTION",
    })
  }
}

export function deleteSelection(selectionState, event) {
  return (dispatch) => {
    dispatch(deleteNodes(selectionState))

    dispatch(reportEvent({
      category: event.category,
      action: event.action,
      name: "delete"
    }))

    return dispatch({
      type: "DELETE_SELECTION"
    })
  }
}

export function zoomIn(graph) {
  return (dispatch) => {
    graph.zoomIn();

    dispatch(reportEvent({
      category: events.categories.rightPanel,
      action: events.actions.buttonClick,
      name: "zoom in"
    }))

    return dispatch({
      type: "ZOOM_IN"
    })
  }
}

export function zoomOut(graph) {
  return (dispatch) => {
    graph.zoomOut();

    dispatch(reportEvent({
      category: events.categories.rightPanel,
      action: events.actions.buttonClick,
      name: "zoom out"
    }))

    return dispatch({
      type: "ZOOM_OUT"
    })
  }
}
