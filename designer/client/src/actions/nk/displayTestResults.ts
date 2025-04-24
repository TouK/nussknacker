import type { ProcessName } from "src/components/Process/types";

import type { TestResults } from "../../common/TestResultUtils";
import type { SourceWithParametersTest, TestProcessResponse } from "../../http/HttpService";
import HttpService from "../../http/HttpService";
import type { ScenarioGraph } from "../../types";
import type { Action, ThunkAction } from "../reduxTypes";
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

        HttpService.testScenarioWithGeneratedData(processName, parseInt(testSampleSize), scenarioGraph)
            .then((response) => dispatch(displayTestResults(response.data)))
            .catch(() => {
                dispatch({ type: "LOADING_FAILED" });
                dispatch({ type: "TEST_RESULTS_FAILED" });
            });
    };
}

type DisplayTestResultsDetailsAction = {
    type: "DISPLAY_TEST_RESULTS_DETAILS";
    testResults: TestResults;
    testData?: SourceWithParametersTest;
};

type UpdateTestTypeAction = {
    type: "UPDATE_TEST_TYPE";
    testType: string;
};

export type TestsActions = DisplayTestResultsDetailsAction | UpdateTestTypeAction;

function displayTestResultsDetails(testResults: TestProcessResponse, testData?: SourceWithParametersTest): Action {
    return {
        type: "DISPLAY_TEST_RESULTS_DETAILS",
        testResults: testResults.results,
        testData,
    };
}

export function updateTestType(testType: string): Action {
    return {
        type: "UPDATE_TEST_TYPE",
        testType,
    };
}

function displayTestResults(testResults: TestProcessResponse, testData?: SourceWithParametersTest): ThunkAction {
    return (dispatch) => {
        dispatch(displayTestResultsDetails(testResults, testData));
        dispatch(displayProcessCounts(testResults.counts));
    };
}
