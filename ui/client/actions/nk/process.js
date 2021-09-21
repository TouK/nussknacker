import _ from "lodash"
import {events} from "../../analytics/TrackingEvents"
import {mapProcessWithNewNode} from "../../components/graph/GraphUtils"
import NodeUtils from "../../components/graph/NodeUtils"
import * as SubprocessSchemaAligner from "../../components/graph/SubprocessSchemaAligner"
import HttpService from "../../http/HttpService"
import * as UndoRedoActions from "../undoRedoActions"
import {displayProcessActivity} from "./displayProcessActivity"
import {displayProcessCounts} from "./displayProcessCounts"
import {fetchProcessDefinition} from "./processDefinitionData"
import {reportEvent} from "./reportEvent"

export function fetchProcessToDisplay(processId, versionId) {
  return (dispatch) => {
    dispatch({
      type: "PROCESS_FETCH",
    })

    return HttpService.fetchProcessDetails(processId, versionId).then((response) => {
      displayTestCapabilites(response.data.json, response.data.processingType)(dispatch)
      return dispatch(displayProcess(response.data))
    })
  }
}

export function loadProcessState(processId) {
  return (dispatch) => HttpService.fetchProcessState(processId).then((response) => dispatch({
    type: "PROCESS_STATE_LOADED",
    processState: response.data,
  }))
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
  return dispatch => {
    return dispatch({
      type: "DISPLAY_PROCESS",
      fetchedProcessDetails: processDetails,
    })
  }
}

export function displayCurrentProcessVersion(processId) {
  return fetchProcessToDisplay(processId)
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

export function calculateProcessAfterChange(process, before, after) {
  return dispatch => {
    if (NodeUtils.nodeIsProperties(after)) {
      return dispatch(
        fetchProcessDefinition(process.processingType, process.properties.isSubprocess),
      ).then((processDef) => {
        const processWithNewSubprocessSchema = alignSubprocessesWithSchema(process, processDef.processDefinitionData)
        const {id, ...properties} = after
        if (id && id !== before.id) {
          dispatch({type: "PROCESS_RENAME", name: id})
        }
        return {...processWithNewSubprocessSchema, properties}
      })
    } else {
      return Promise.resolve(mapProcessWithNewNode(process, before, after))
    }
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
    }))

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
    }))

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
    dispatch(displayProcessCounts(testResults.counts))
  }
}

export function addAttachment(processId, processVersionId, comment) {
  return (dispatch) => {
    return HttpService.addAttachment(processId, processVersionId, comment).then(() => {
      return dispatch(displayProcessActivity(processId))
    })
  }
}
