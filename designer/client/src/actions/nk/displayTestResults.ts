import type { ProcessName } from "src/components/Process/types";

import type { TestResults } from "../../common/TestResultUtils";
import type { SourceWithParametersTest, TestProcessResponse } from "../../http/HttpService";
import HttpService from "../../http/HttpService";
import type { ScenarioGraph } from "../../types";
import type { ThunkAction } from "../reduxTypes";
import { displayProcessCounts } from "./displayProcessCounts";

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
        dispatch({ type: "TEST_RESULTS_LOADING" });

        HttpService.testProcessWithParameters(processName, testData, scenarioGraph)
            .then((response) => {
                dispatch(displayTestResults(response.data, testData));
            })
            .catch(() => dispatch({ type: "TEST_RESULTS_FAILED" }));
    };
}

export function testScenarioWithGeneratedData(testSampleSize: string, processName: ProcessName, scenarioGraph: ScenarioGraph): ThunkAction {
    return (dispatch) => {
        dispatch({
            type: "PROCESS_LOADING",
        });
        dispatch({ type: "TEST_RESULTS_LOADING" });

        HttpService.testScenarioWithGeneratedData(processName, testSampleSize, scenarioGraph)
            .then((response) => dispatch(displayTestResults(response.data)))
            .catch(() => {
                dispatch({ type: "LOADING_FAILED" });
                dispatch({ type: "TEST_RESULTS_FAILED" });
            });
    };
}

export interface DisplayTestResultsDetailsAction {
    type: "DISPLAY_TEST_RESULTS_DETAILS";
    testResults: TestResults;
    testData?: SourceWithParametersTest;
}

function displayTestResultsDetails(testResults: TestProcessResponse, testData?: SourceWithParametersTest): DisplayTestResultsDetailsAction {
    return {
        type: "DISPLAY_TEST_RESULTS_DETAILS",
        testResults: testResults.results,
        testData,
    };
}

function displayTestResults(testResults: TestProcessResponse, testData?: SourceWithParametersTest) {
    return (dispatch) => {
        dispatch(displayTestResultsDetails(testResults, testData));
        dispatch(displayProcessCounts(testResults.counts));
    };
}
