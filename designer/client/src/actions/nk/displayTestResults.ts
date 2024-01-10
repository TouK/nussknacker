import HttpService, { SourceWithParametersTest, TestProcessResponse } from "../../http/HttpService";
import { displayProcessCounts } from "./displayProcessCounts";
import { TestResults } from "../../common/TestResultUtils";
import { ThunkAction } from "../reduxTypes";
import { ProcessName, Scenario } from "src/components/Process/types";

export function testProcessFromFile(processName: ProcessName, testDataFile: File, scenario: Scenario): ThunkAction {
    return (dispatch) => {
        dispatch({
            type: "PROCESS_LOADING",
        });

        HttpService.testProcess(processName, testDataFile, scenario)
            .then((response) => dispatch(displayTestResults(response.data)))
            .catch(() => dispatch({ type: "LOADING_FAILED" }));
    };
}

export function testProcessWithParameters(processName: ProcessName, testData: SourceWithParametersTest, scenario: Scenario): ThunkAction {
    return (dispatch) => {
        dispatch({
            type: "PROCESS_LOADING",
        });

        HttpService.testProcessWithParameters(processName, testData, scenario)
            .then((response) => dispatch(displayTestResults(response.data)))
            .catch(() => dispatch({ type: "LOADING_FAILED" }));
    };
}

export function testScenarioWithGeneratedData(testSampleSize: string, scenario: Scenario): ThunkAction {
    return (dispatch) => {
        dispatch({
            type: "PROCESS_LOADING",
        });

        HttpService.testScenarioWithGeneratedData(testSampleSize, scenario)
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
