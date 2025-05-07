import type { ProcessName } from "src/components/Process/types";

import type { TestResults } from "../../common/TestResultUtils";
import type { SourceWithParametersTest, TestProcessResponse } from "../../http/HttpService";
import HttpService from "../../http/HttpService";
import { getProcessName, getScenarioGraph } from "../../reducers/selectors/graph";
import type { ScenarioGraph } from "../../types";
import type { Action, ThunkAction } from "../reduxTypes";
import { displayProcessCounts } from "./displayProcessCounts";

export function testProcessFromFile(testDataFile: File): ThunkAction {
    return wrapWithTestAction((processName, scenarioGraph) =>
        HttpService.testScenarioWithFile(processName, scenarioGraph, testDataFile).then(({ data }) => ({
            testResults: data,
        })),
    );
}

export function testProcessWithParameters(sourceParameters: SourceWithParametersTest): ThunkAction {
    return wrapWithTestAction((processName, scenarioGraph) =>
        HttpService.testScenario(processName, scenarioGraph, {
            type: "WITH_PARAMETERS",
            sourceParameters,
        }).then(({ data }) => ({
            testResults: data,
            testData: sourceParameters,
        })),
    );
}

export function testScenarioWithGeneratedData(testSampleSize: string): ThunkAction {
    return wrapWithTestAction((processName, scenarioGraph) =>
        HttpService.testScenario(processName, scenarioGraph, {
            type: "WITH_GENERATED_DATA",
            numberOfSamples: parseInt(testSampleSize),
        }).then(({ data }) => ({
            testResults: data,
        })),
    );
}

export type TestsActions =
    | {
          type: "TEST_RESULTS_LOADING";
      }
    | {
          type: "TEST_RESULTS_FAILED";
      }
    | {
          type: "DISPLAY_TEST_RESULTS_DETAILS";
          testResults: TestResults;
          testData?: SourceWithParametersTest;
      }
    | {
          type: "UPDATE_TEST_TYPE";
          testType: string;
      };

function wrapWithTestAction(
    fn: (
        processName: ProcessName,
        scenarioGraph: ScenarioGraph,
    ) => Promise<{
        testResults: TestProcessResponse;
        testData?: SourceWithParametersTest;
    }>,
): ThunkAction {
    return (dispatch, getState) => {
        dispatch({ type: "TEST_RESULTS_LOADING" });
        const state = getState();
        const scenarioGraph = getScenarioGraph(state);
        const processName = getProcessName(state);
        fn(processName, scenarioGraph)
            .then(({ testResults, testData }) => dispatch(displayTestResults(testResults, testData)))
            .catch(() => dispatch({ type: "TEST_RESULTS_FAILED" }));
    };
}

function displayTestResultsDetails(testResults: TestResults, testData?: SourceWithParametersTest): Action {
    return {
        type: "DISPLAY_TEST_RESULTS_DETAILS",
        testResults,
        testData,
    };
}

export function updateTestType(testType: string): Action {
    return {
        type: "UPDATE_TEST_TYPE",
        testType,
    };
}

function displayTestResults({ counts, results }: TestProcessResponse, testData?: SourceWithParametersTest): ThunkAction {
    return (dispatch) => {
        dispatch(displayTestResultsDetails(results, testData));
        dispatch(displayProcessCounts(counts));
    };
}

export interface SetTestData {
    type: "SET_TEST_DATA";
    testData: SourceWithParametersTest;
}

export function setTestData(testData: SourceWithParametersTest): SetTestData {
    return {
        type: "SET_TEST_DATA",
        testData,
    };
}
