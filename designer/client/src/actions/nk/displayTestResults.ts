import { cloneDeep } from "lodash";
import type { ProcessName } from "src/components/Process/types";

import type { TestingEventParameters } from "../../components/modals/Testing/TestingEventsTable";
import type { SourceWithParametersTest } from "../../http/HttpService";
import HttpService from "../../http/HttpService";
import type { ResultsWithCountsDto, TestResultsDto } from "../../http/resultsWithCountsDto";
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
            type: "WITH_LIVE_DATA",
            numberOfSamples: parseInt(testSampleSize),
        }).then(({ data }) => ({
            testResults: data,
        })),
    );
}

export function testScenarioWithEventsData(testingEventsParameters: TestingEventParameters[]): ThunkAction {
    return wrapWithTestAction((scenarioName, scenarioGraph) =>
        HttpService.testScenarioWithEventsData(
            scenarioName,
            scenarioGraph,
            cloneDeep(testingEventsParameters).map((event) => {
                event.variables = JSON.parse(event.variables);

                return event;
            }),
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
          testingEventParameters?: TestingEventParameters[];
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
          testingEventsParameters: TestingEventParameters[];
      };

function wrapWithTestAction(
    fn: (
        processName: ProcessName,
        scenarioGraph: ScenarioGraph,
    ) => Promise<{
        testResults: ResultsWithCountsDto;
        testData?: SourceWithParametersTest;
        testingEventsParameters?: TestingEventParameters[];
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

export function setTestingEventsParameters(testingEventsParameters: TestingEventParameters[]): Action {
    return {
        type: "SET_TESTING_EVENTS_PARAMETERS",
        testingEventsParameters,
    };
}

function displayTestResults(
    { counts, results }: ResultsWithCountsDto,
    testData?: SourceWithParametersTest,
    testingEventsParameters?: TestingEventParameters[],
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
