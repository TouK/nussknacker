import HttpService, { SourceWithParametersTest, TestProcessResponse } from "../../http/HttpService";
import { displayProcessCounts } from "./displayProcessCounts";
import { TestResults } from "../../common/TestResultUtils";
import { ThunkAction } from "../reduxTypes";
import { ProcessName } from "src/components/Process/types";
import { ScenarioGraph } from "../../types";

export function testProcessFromFile(processName: ProcessName, testDataFile: File, scenarioGraph: ScenarioGraph): ThunkAction {
    return (dispatch) => {
        dispatch({
            type: "PROCESS_LOADING",
        });

        HttpService.testProcess(processName, testDataFile, scenarioGraph)
            .then((response) => dispatch(displayTestResults(response.data)))
            .catch(() => dispatch({ type: "LOADING_FAILED" }));
    };
}

export function testProcessWithParameters(
    processName: ProcessName,
    testData: SourceWithParametersTest,
    scenarioGraph: ScenarioGraph,
): ThunkAction {
    return (dispatch) => {
        dispatch({
            type: "PROCESS_LOADING",
        });

        HttpService.testProcessWithParameters(processName, testData, scenarioGraph)
            .then((response) => dispatch(displayTestResults(response.data)))
            .catch(() => dispatch({ type: "LOADING_FAILED" }));
    };
}

export function testScenarioWithGeneratedData(testSampleSize: string, processName: ProcessName, scenarioGraph: ScenarioGraph): ThunkAction {
    return (dispatch) => {
        dispatch({
            type: "PROCESS_LOADING",
        });

        HttpService.testScenarioWithGeneratedData(processName, testSampleSize, scenarioGraph)
            .then((response) => dispatch(displayTestResults(response.data)))
            .catch(() => dispatch({ type: "LOADING_FAILED" }));
    };
}

export interface DisplayTestResultsDetailsAction {
    testResults: TestResults;
    type: "DISPLAY_TEST_RESULTS_DETAILS";
}

function displayTestResultsDetails(testResults: TestProcessResponse): DisplayTestResultsDetailsAction {
    return {
        type: "DISPLAY_TEST_RESULTS_DETAILS",
        testResults: testResults.results,
    };
}

function displayTestResults(testResults: TestProcessResponse) {
    return (dispatch) => {
        dispatch(displayTestResultsDetails(testResults));
        dispatch(displayProcessCounts(testResults.counts));
    };
}
