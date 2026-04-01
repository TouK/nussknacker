import { EventSourceParserStream } from "eventsource-parser/stream";
import type { ProcessName } from "src/components/Process/types";

import type { TestingDataRecords } from "../../components/modals/TestingDataRecords/Table";
import HttpService from "../../http/HttpService/instance";
import type { SourceWithParametersTest } from "../../http/HttpService/types";
import type { MultipleResultsWithCountsDto, ResultsWithCountsDto, TestResultsDto } from "../../http/resultsWithCountsDto";
import type { TestCase } from "../../reducers/graph/testCase";
import { getProcessName, getScenarioGraph } from "../../reducers/selectors/graph";
import { getTestCases } from "../../reducers/selectors/testCases";
import { getActiveTestCaseId, getTestAssertionResults } from "../../reducers/selectors/testing";
import type { ScenarioGraph } from "../../types/scenarioGraph";
import { error } from "../notificationActions";
import type { Action, ThunkAction } from "../reduxTypes";
import { checkPendingChanges } from "./checkPendingChanges";
import { clearProcessCounts, displayProcessCounts } from "./displayProcessCounts";

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

export function testScenarioWithTestCase(testCase: TestCase, isMockEnabled: boolean): ThunkAction {
    const testData = isMockEnabled ? testCase : { ...testCase, mocks: {} };
    return wrapWithTestCaseAction(testCase, (scenarioName, scenarioGraph) =>
        HttpService.testScenarioWithTestCase(scenarioName, scenarioGraph, testData).then(({ data }) => ({
            testResults: data,
        })),
    );
}

export function testAllScenarioTestCases(isMockEnabled: boolean): ThunkAction {
    return async (dispatch, getState) => {
        await dispatch(checkPendingChanges());

        const state = getState();
        const scenarioGraph = getScenarioGraph(state);
        const processName = getProcessName(state);
        const testCases = getTestCases(state);

        const failTestCase = (id: string, name: string, details?: string) => {
            dispatch({ type: "TEST_CASE_ASSERTION_RESULTS_FAILED", testCaseId: id });
            dispatch(error(`Test case "${name}" failed`, details, !!details));
        };

        dispatch(clearProcessCounts());
        dispatch({ type: "TEST_RESULTS_LOADING" });
        testCases.forEach((testCase) => dispatch(setTestCaseAssertionResultsLoading(testCase.id)));

        const testData = testCases.map((testCase) => (isMockEnabled ? testCase : { ...testCase, mocks: {} }));

        try {
            const response = await HttpService.testScenarioWithTestMultipleCases(processName, scenarioGraph, testData);
            if (!response.ok || !response.body) {
                testCases.forEach((testCase) => failTestCase(testCase.id, testCase.name));
                return;
            }

            const reader = response.body.pipeThrough(new TextDecoderStream()).pipeThrough(new EventSourceParserStream()).getReader();

            try {
                for (;;) {
                    const { done, value } = await reader.read();
                    if (done) break;
                    if (value.data) {
                        const data: MultipleResultsWithCountsDto = JSON.parse(value.data);
                        if (data.type === "Completed") {
                            dispatch(displayTestAssertionsResults(data.testCaseId, data));
                            const activeTestCaseId = getActiveTestCaseId(getState());
                            if (data.testCaseId === activeTestCaseId) {
                                dispatch(testingActions(data.result));
                            }
                        } else {
                            failTestCase(data.testCaseId, data.testCaseName, data.error);
                        }
                    }
                }
            } finally {
                reader.releaseLock();
            }
        } catch {
            testCases.forEach((tc) => failTestCase(tc.id, tc.name));
        }
    };
}

export type TestsActions =
    | {
          type: "TEST_RESULTS_LOADING";
      }
    | {
          type: "TEST_RESULTS_FAILED";
      }
    | {
          type: "TEST_CASE_ASSERTION_RESULTS_FAILED";
          testCaseId: string;
      }
    | {
          type: "DISPLAY_TEST_RESULTS_DETAILS";
          testResults: TestResultsDto;
          testData?: SourceWithParametersTest;
          testingDataRecords?: TestingDataRecords[];
      }
    | {
          type: "SET_TEST_CASE_ASSERTION_RESULTS_LOADING";
          testCaseId: string;
      }
    | {
          type: "DISPLAY_TEST_ASSERTIONS_RESULTS";
          testCaseId: string;
          results: MultipleResultsWithCountsDto;
      }
    | {
          type: "CLEAR_TEST_ASSERTIONS_RESULTS";
      }
    | { type: "CHANGE_ACTIVE_TEST_CASE"; testCaseId: string };

type TestFn = (
    processName: ProcessName,
    scenarioGraph: ScenarioGraph,
) => Promise<{
    testResults: ResultsWithCountsDto;
    testData?: SourceWithParametersTest;
}>;

function wrapWithTestAction(fn: TestFn): ThunkAction {
    return async (dispatch, getState) => {
        dispatch({ type: "TEST_RESULTS_LOADING" });
        try {
            await dispatch(checkPendingChanges());

            const state = getState();
            const scenarioGraph = getScenarioGraph(state);
            const processName = getProcessName(state);
            const { testResults, testData } = await fn(processName, scenarioGraph);
            dispatch(testingActions(testResults, testData));
        } catch {
            dispatch({ type: "TEST_RESULTS_FAILED" });
        }
    };
}

function wrapWithTestCaseAction(testCase: TestCase, fn: TestFn): ThunkAction {
    return async (dispatch, getState) => {
        dispatch(clearProcessCounts());
        dispatch({ type: "TEST_RESULTS_LOADING" });
        try {
            await dispatch(checkPendingChanges());

            const state = getState();
            const scenarioGraph = getScenarioGraph(state);
            const processName = getProcessName(state);
            const { testResults, testData } = await fn(processName, scenarioGraph);
            dispatch(testingActions(testResults, testData));
            const completedResult: MultipleResultsWithCountsDto = {
                type: "Completed",
                testCaseId: testCase.id,
                testCaseName: testCase.name,
                result: testResults,
            };
            dispatch(displayTestAssertionsResults(testCase.id, completedResult));
        } catch {
            dispatch({ type: "TEST_RESULTS_FAILED" });
            dispatch({ type: "TEST_CASE_ASSERTION_RESULTS_FAILED", testCaseId: testCase.id });
        }
    };
}

export function displayTestResultsDetails(testResults: TestResultsDto, testData?: SourceWithParametersTest): Action {
    return {
        type: "DISPLAY_TEST_RESULTS_DETAILS",
        testResults,
        testData,
    };
}

export function setTestCaseAssertionResultsLoading(testCaseId: string): Action {
    return {
        type: "SET_TEST_CASE_ASSERTION_RESULTS_LOADING",
        testCaseId,
    };
}

export function displayTestAssertionsResults(testCaseId: string, results: MultipleResultsWithCountsDto): Action {
    return {
        type: "DISPLAY_TEST_ASSERTIONS_RESULTS",
        testCaseId,
        results,
    };
}

export function changeActiveTestCase(testCaseId: string): ThunkAction {
    return (dispatch, getState) => {
        dispatch(clearProcessCounts());
        dispatch({ type: "CHANGE_ACTIVE_TEST_CASE", testCaseId });

        const result = getTestAssertionResults(getState())[testCaseId];
        if (result?.status === "loaded" && result.results.type === "Completed") {
            dispatch(testingActions(result.results.result));
        }
    };
}

function testingActions({ counts, results }: ResultsWithCountsDto, testData?: SourceWithParametersTest): ThunkAction {
    return (dispatch) => {
        dispatch(displayProcessCounts(counts));
        dispatch(displayTestResultsDetails(results, testData));
    };
}

export function clearTestAssertionsResults(): Action {
    return { type: "CLEAR_TEST_ASSERTIONS_RESULTS" };
}
