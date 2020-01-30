import _ from "lodash"
import {events} from "../../analytics/TrackingEvents"
import * as GraphUtils from "../../components/graph/GraphUtils"
import NodeUtils from "../../components/graph/NodeUtils"
import * as SubprocessSchemaAligner from "../../components/graph/SubprocessSchemaAligner"
import HttpService from "../../http/HttpService"
import * as UndoRedoActions from "../undoRedoActions"
import {displayProcessActivity} from "./displayProcessActivity"
import {fetchProcessDefinition} from "./processDefinitionData"
import {reportEvent} from "./reportEvent"

export function fetchProcessToDisplay(processId, versionId, businessView) {
  return (dispatch) => {
    dispatch({
      type: "PROCESS_LOADING",
    })
    return HttpService.fetchProcessDetails(processId, versionId, businessView).then((response) => {
      displayTestCapabilites(response.data.json, response.data.processingType)(dispatch)
      return dispatch(displayProcess(response.data))
    })
  }
}

function displayTestCapabilites(processDetails) {
  return (dispatch) => {
    HttpService.getTestCapabilities(processDetails).then((response) => dispatch({
      type: "UPDATE_TEST_CAPABILITIES",
      capabilities: response.data,
    }))
  }
}

function displayProcess(processDetails) {
  return {
    type: "DISPLAY_PROCESS",
    fetchedProcessDetails: processDetails,
  }
}

export function displayCurrentProcessVersion(processId) {
  return fetchProcessToDisplay(processId)
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
      type: "PROCESS_LOADING",
    })

    return HttpService.importProcess(processId, file)
        .then((process) => dispatch(updateImportedProcess(process.data)))
        .catch((error) => dispatch({type: "LOADING_FAILED"}))
  }
}

export function updateImportedProcess(processJson) {
  return {
    type: "UPDATE_IMPORTED_PROCESS",
    processJson: processJson,
  }
}

export function clearProcess() {
  return (dispatch) => {
    dispatch(UndoRedoActions.clear())
    return dispatch({
      type: "CLEAR_PROCESS",
    })
  }
}

export function calculateProcessAfterChange(process, before, after, dispatch) {
  if (NodeUtils.nodeIsProperties(after)) {
    const subprocessVersions = after.subprocessVersions || process.properties.subprocessVersions
    return dispatch(
        fetchProcessDefinition(process.processingType, process.properties.isSubprocess, subprocessVersions),
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
  return {...process, nodes: nodesWithNewSubprocessSchema}
}

export function testProcessFromFile(id, testDataFile, process) {
  return (dispatch) => {
    dispatch({
      type: "PROCESS_LOADING",
    })

    dispatch(reportEvent({
          category: events.categories.rightPanel,
          action: events.actions.buttonClick,
          name: "from file",
        },
    ))

    HttpService.testProcess(id, testDataFile, process)
        .then(response => dispatch(displayTestResults(response.data)))
        .catch(error => dispatch({type: "LOADING_FAILED"}))
  }
}

export function hideRunProcessDetails() {
  return (dispatch) => {
    dispatch(reportEvent({
          category: events.categories.rightPanel,
          action: events.actions.buttonClick,
          name: "hide",
        },
    ))

    return dispatch({
      type: "HIDE_RUN_PROCESS_DETAILS",
    })
  }
}

function displayTestResults(testResults) {
  return (dispatch) => {
    dispatch({
      type: "DISPLAY_TEST_RESULTS_DETAILS",
      testResults: testResults.results,
    })
    dispatch({
          type: "DISPLAY_PROCESS_COUNTS",
          processCounts: testResults.counts,
        },
    )
  }
}

export function addAttachment(processId, processVersionId, comment) {
  return (dispatch) => {
    return HttpService.addAttachment(processId, processVersionId, comment).then(() => {
      return dispatch(displayProcessActivity(processId))
    })
  }
}
