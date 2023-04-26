import HttpService, {TestProcessResponse} from "../../http/HttpService"
import {displayProcessCounts} from "./displayProcessCounts"
import {TestResults} from "../../common/TestResultUtils"
import {Process, ProcessId} from "../../types"
import {ThunkAction} from "../reduxTypes"
import {withoutHackOfEmptyEdges} from "../../components/graph/GraphPartialsInTS/EdgeUtils"
import {UIValueParameter} from "./genericAction";

export function testProcessFromFile(id: ProcessId, testDataFile: File, process: Process): ThunkAction {
  return (dispatch) => {
    dispatch({
      type: "PROCESS_LOADING",
    })

    const processWithCleanEdges = withoutHackOfEmptyEdges(process)
    HttpService.testProcess(id, testDataFile, processWithCleanEdges)
      .then(response => dispatch(displayTestResults(response.data)))
      .catch(() => dispatch({type: "LOADING_FAILED"}))
  }
}

export function testProcessFromJson(id: ProcessId, testData: string, process: Process): ThunkAction {
  return (dispatch) => {
    dispatch({
      type: "PROCESS_LOADING",
    })

    const processWithCleanEdges = withoutHackOfEmptyEdges(process)
    HttpService.testProcessFromJson(id, testData, processWithCleanEdges)
      .then(response => dispatch(displayTestResults(response.data)))
      .catch(() => dispatch({type: "LOADING_FAILED"}))
  }
}

export function testScenarioWithGeneratedData(id: ProcessId, testSampleSize: string, process: Process): ThunkAction {
  return (dispatch) => {
    dispatch({
      type: "PROCESS_LOADING",
    })

    const processWithCleanEdges = withoutHackOfEmptyEdges(process)
    HttpService.testScenarioWithGeneratedData(id, testSampleSize, processWithCleanEdges)
      .then(response => dispatch(displayTestResults(response.data)))
      .catch(() => dispatch({type: "LOADING_FAILED"}))
  }
}

export interface DisplayTestResultsDetailsAction {
  testResults: TestResults,
  type: "DISPLAY_TEST_RESULTS_DETAILS",
}

function displayTestResultsDetails(testResults: TestProcessResponse): DisplayTestResultsDetailsAction {
  return {
    type: "DISPLAY_TEST_RESULTS_DETAILS",
    testResults: testResults.results,
  }
}

function displayTestResults(testResults: TestProcessResponse) {
  return (dispatch) => {
    dispatch(displayTestResultsDetails(testResults))
    dispatch(displayProcessCounts(testResults.counts))
  }
}
