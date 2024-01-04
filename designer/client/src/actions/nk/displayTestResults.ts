import HttpService, { SourceWithParametersTest, TestProcessResponse } from "../../http/HttpService";
import { displayProcessCounts } from "./displayProcessCounts";
import { TestResults } from "../../common/TestResultUtils";
import { Expression, Process, ProcessName } from "../../types";
import { ThunkAction } from "../reduxTypes";
import { withoutHackOfEmptyEdges } from "../../components/graph/GraphPartialsInTS/EdgeUtils";

export function testProcessFromFile(processName: ProcessName, testDataFile: File, process: Process): ThunkAction {
    return (dispatch) => {
        dispatch({
            type: "PROCESS_LOADING",
        });

        const processWithCleanEdges = withoutHackOfEmptyEdges(process);
        HttpService.testProcess(processName, testDataFile, processWithCleanEdges)
            .then((response) => dispatch(displayTestResults(response.data)))
            .catch(() => dispatch({ type: "LOADING_FAILED" }));
    };
}

export function testProcessWithParameters(processName: ProcessName, testData: SourceWithParametersTest, process: Process): ThunkAction {
    return (dispatch) => {
        dispatch({
            type: "PROCESS_LOADING",
        });

        const processWithCleanEdges = withoutHackOfEmptyEdges(process);
        HttpService.testProcessWithParameters(processName, testData, processWithCleanEdges)
            .then((response) => dispatch(displayTestResults(response.data)))
            .catch(() => dispatch({ type: "LOADING_FAILED" }));
    };
}

export function testScenarioWithGeneratedData(processName: ProcessName, testSampleSize: string, process: Process): ThunkAction {
    return (dispatch) => {
        dispatch({
            type: "PROCESS_LOADING",
        });

        const processWithCleanEdges = withoutHackOfEmptyEdges(process);
        HttpService.testScenarioWithGeneratedData(processName, testSampleSize, processWithCleanEdges)
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
