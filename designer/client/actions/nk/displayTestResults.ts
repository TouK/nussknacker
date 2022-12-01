import {reportEvent} from "./reportEvent"
import {events} from "../../analytics/TrackingEvents"
import HttpService, {TestProcessResponse} from "../../http/HttpService"
import {displayProcessCounts} from "./displayProcessCounts"
import {TestResults} from "../../common/TestResultUtils"
import {Process, ProcessId} from "../../types"
import {ThunkAction} from "../reduxTypes"
import {withoutEmptyEdges} from "../../components/graph/GraphPartialsInTS/EdgeUtils";

export function testProcessFromFile(id: ProcessId, testDataFile: File, process: Process): ThunkAction {
  return (dispatch) => {
    dispatch({
      type: "PROCESS_LOADING",
    })

    dispatch(reportEvent({
      category: events.categories.rightPanel,
      action: events.actions.buttonClick,
      name: "from file",
    }))

    let processWithCleanEdges = withoutEmptyEdges(process)
    HttpService.testProcess(id, testDataFile, processWithCleanEdges)
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
