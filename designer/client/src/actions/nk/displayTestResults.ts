import type { ProcessName } from "src/components/Process/types";

import type { ExpressionObj } from "../../components/graph/node-modal/editors/expression/types";
import type { TestingDataRecords } from "../../components/modals/TestingDataRecords/Table";
import { mapDataRecordsToRunTestsFormat } from "../../components/modals/TestingDataRecords/utils";
import HttpService from "../../http/HttpService/instance";
import type { SourceWithParametersTest } from "../../http/HttpService/types";
import type { ResultsWithCountsDto, TestAssertionResults, TestResultsDto } from "../../http/resultsWithCountsDto";
import { getProcessName, getScenarioGraph } from "../../reducers/selectors/graph";
import type { ScenarioGraph } from "../../types/scenarioGraph";
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
            type: "WITH_LIVE_DATA",
            numberOfSamples: parseInt(testSampleSize),
        }).then(({ data }) => ({
            testResults: data,
        })),
    );
}

export function testScenarioWithDataRecords(
    testingEventsParameters: TestingDataRecords[],
    testAssertions: Record<string, { expression: ExpressionObj }[]>,
): ThunkAction {
    return wrapWithTestAction((scenarioName, scenarioGraph) =>
        HttpService.testScenarioWithEventsData(
            scenarioName,
            scenarioGraph,
            testingEventsParameters.map(mapDataRecordsToRunTestsFormat),
            testAssertions,
        ).then(({ data }) => ({
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
          testResults: TestResultsDto;
          testData?: SourceWithParametersTest;
          testingDataRecords?: TestingDataRecords[];
      }
    | {
          type: "DISPLAY_TEST_ASSERTIONS_RESULTS";
          assertionsResults: TestAssertionResults;
      }
    | {
          type: "CLEAR_TEST_ASSERTIONS_RESULTS";
      };

function wrapWithTestAction(
    fn: (
        processName: ProcessName,
        scenarioGraph: ScenarioGraph,
    ) => Promise<{
        assertionsResults?: TestAssertionResults;
        testResults: ResultsWithCountsDto;
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

export function displayTestResultsDetails(testResults: TestResultsDto, testData?: SourceWithParametersTest): Action {
    return {
        type: "DISPLAY_TEST_RESULTS_DETAILS",
        testResults,
        testData,
    };
}

export function displayTestAssertionsResults(assertionsResults: TestAssertionResults): Action {
    return {
        type: "DISPLAY_TEST_ASSERTIONS_RESULTS",
        assertionsResults,
    };
}

function displayTestResults(
    { counts, results, assertionsResults }: ResultsWithCountsDto,
    testData?: SourceWithParametersTest,
): ThunkAction {
    return (dispatch) => {
        dispatch(displayProcessCounts(counts));
        dispatch(displayTestResultsDetails(results, testData));
        dispatch(displayTestAssertionsResults(assertionsResults));
    };
}
