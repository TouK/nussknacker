import type { ProcessName } from "src/components/Process/types";

import type { TestingDataRecords } from "../../components/modals/TestingDataRecords/Table";
import { mapDataRecordsToRunTestsFormat } from "../../components/modals/TestingDataRecords/utils";
import HttpService from "../../http/instance";
import type { ResultsWithCountsDto, TestResultsDto } from "../../http/resultsWithCountsDto";
import type { SourceWithParametersTest } from "../../http/types";
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

export function testScenarioWithDataRecords(testingEventsParameters: TestingDataRecords[]): ThunkAction {
    return wrapWithTestAction((scenarioName, scenarioGraph) =>
        HttpService.testScenarioWithEventsData(
            scenarioName,
            scenarioGraph,
            testingEventsParameters.map(mapDataRecordsToRunTestsFormat),
        ).then(({ data }) => ({
            testResults: data,
            testingEventsParameters: testingEventsParameters,
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
          type: "UPDATE_TEST_TYPE";
          testType: string;
      }
    | {
          type: "SET_TEST_DATA";
          testData: SourceWithParametersTest;
      }
    | {
          type: "SET_TESTING_EVENTS_PARAMETERS";
          testingEventsParameters: TestingDataRecords[];
      };

function wrapWithTestAction(
    fn: (
        processName: ProcessName,
        scenarioGraph: ScenarioGraph,
    ) => Promise<{
        testResults: ResultsWithCountsDto;
        testData?: SourceWithParametersTest;
        testingEventsParameters?: TestingDataRecords[];
    }>,
): ThunkAction {
    return (dispatch, getState) => {
        dispatch({ type: "TEST_RESULTS_LOADING" });
        const state = getState();
        const scenarioGraph = getScenarioGraph(state);
        const processName = getProcessName(state);
        fn(processName, scenarioGraph)
            .then(({ testResults, testData, testingEventsParameters }) =>
                dispatch(displayTestResults(testResults, testData, testingEventsParameters)),
            )
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

export function setTestingEventsParameters(testingEventsParameters: TestingDataRecords[]): Action {
    return {
        type: "SET_TESTING_EVENTS_PARAMETERS",
        testingEventsParameters,
    };
}

function displayTestResults(
    { counts, results }: ResultsWithCountsDto,
    testData?: SourceWithParametersTest,
    testingEventsParameters?: TestingDataRecords[],
): ThunkAction {
    return (dispatch) => {
        dispatch(displayProcessCounts(counts));
        dispatch(displayTestResultsDetails(results, testData));
        if (testingEventsParameters) {
            dispatch(setTestingEventsParameters(testingEventsParameters));
        }
    };
}

export function setTestData(testData: SourceWithParametersTest): Action {
    return {
        type: "SET_TEST_DATA",
        testData,
    };
}
