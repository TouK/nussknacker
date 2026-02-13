import { produce } from "immer";
import { set, toPath, get } from "lodash";
import { omit } from "lodash/fp";
import { ActionCreators as UndoActionCreators } from "redux-undo";

import type { TestCapabilities } from "../../common/TestResultUtils";
import { useFrontendAiTool } from "../../components/aiAssistant/useFrontendAiTool";
import type { PredefinedActionName, ProcessName, ProcessStateType, ProcessVersionId, Scenario } from "../../components/Process/types";
import { replaceSearchQuery } from "../../containers/hooks/useSearchQuery";
import { memoizeByArgsWithTTL } from "../../helpers/memoizeByArgsWithTTL";
import HttpService from "../../http/HttpService/instance";
import { getProcessDefinitionData } from "../../reducers/selectors/getProcessDefinitionData";
import { getSavedScenario, getScenario, getScenarioGraph } from "../../reducers/selectors/graph";
import { useAppDispatch } from "../../store/storeHelpers";
import type { ProcessDefinitionData, ScenarioGraph } from "../../types/scenarioGraph";
import type { ValidationErrors, ValidationResult } from "../../types/validation";
import type { Action, ThunkAction } from "../reduxTypes";
import { Initiator, stopLiveData } from "./liveData";
import { preApplyValidation } from "./preApplyValidation";

export type ScenarioActions =
    | { type: "PENDING_SCENARIO_ACTION"; action: PredefinedActionName }
    | {
          type: "PROCESS_STATE_LOADED";
          processState: ProcessStateType;
      }
    | {
          type: "CORRECT_INVALID_SCENARIO";
          processDefinitionData: ProcessDefinitionData;
      }
    | {
          type: "DISPLAY_PROCESS";
          scenario: Scenario;
      }
    | {
          type: "UPDATE_IMPORTED_PROCESS";
          scenario: Scenario;
      }
    | { type: "CLEAR_PROCESS" }
    | { type: "HIDE_RUN_PROCESS_DETAILS" }
    | {
          type: "APPLY_GRAPH_CHANGES";
          validationResult?: ValidationResult;
          scenarioGraphAfterChange: ScenarioGraph;
      };

export function fetchProcessToDisplay(processName: ProcessName, versionId?: ProcessVersionId): ThunkAction<Promise<Scenario>> {
    return (dispatch) => {
        dispatch({ type: "PROCESS_FETCH" });

        return HttpService.fetchScenario(processName, { versionId }).then((response) => {
            dispatch({
                type: "DISPLAY_PROCESS",
                scenario: response.data,
            });
            return response.data;
        });
    };
}

export function loadProcessState(processName: ProcessName, processVersionId: number): ThunkAction {
    return (dispatch) =>
        HttpService.fetchProcessState(processName, processVersionId).then(({ data }) =>
            dispatch({
                type: "PROCESS_STATE_LOADED",
                processState: data,
            }),
        );
}

const getTestCapabilities = memoizeByArgsWithTTL((processName: ProcessName, scenarioGraph: ScenarioGraph) => {
    return HttpService.getTestCapabilities(processName, scenarioGraph);
}, 1000);

export type UpdateTestCapabilitiesAction = { type: "UPDATE_TEST_CAPABILITIES"; capabilities: TestCapabilities };

export function displayTestCapabilities(processName: ProcessName, scenarioGraph: ScenarioGraph): ThunkAction {
    return (dispatch) => {
        if (getTestCapabilities.isCached(processName, scenarioGraph)) return;
        getTestCapabilities(processName, scenarioGraph).then(({ data }) => {
            return dispatch({
                type: "UPDATE_TEST_CAPABILITIES",
                capabilities: data,
            });
        });
    };
}

export function displayCurrentProcessVersion(processName: ProcessName): ThunkAction {
    return fetchProcessToDisplay(processName);
}

export function displayScenarioVersion(processName: ProcessName, versionId: ProcessVersionId): ThunkAction {
    return async (dispatch, getState) => {
        await dispatch(fetchProcessToDisplay(processName, versionId));
        const processDefinitionData = getProcessDefinitionData(getState());
        dispatch({ type: "CORRECT_INVALID_SCENARIO", processDefinitionData });
    };
}

export function clearProcess(): ThunkAction {
    return (dispatch) => {
        dispatch(UndoActionCreators.clearHistory() as Action);
        dispatch({ type: "CLEAR_PROCESS" });
    };
}

export function hideTestRunDetails(): ThunkAction {
    return (dispatch, getState) => {
        replaceSearchQuery(omit(["from", "to", "refresh"]));
        dispatch({ type: "HIDE_RUN_PROCESS_DETAILS" });
    };
}
export function hideRunProcessDetails(): ThunkAction {
    return (dispatch, getState) => {
        dispatch(stopLiveData(Initiator.button));
        dispatch(hideTestRunDetails());
    };
}

export function unsafe_applyScenarioChanges(
    changes: { nodeId?: string; path: string; value: string }[],
): ThunkAction<Promise<{ scenario: ScenarioGraph } | { errors: ValidationErrors } | string>> {
    return async (dispatch, getState) => {
        const state = getState();
        const scenarioBefore = getScenario(state);
        const scenarioGraph = getScenarioGraph(state);

        try {
            const scenarioGraphAfterChange = produce(scenarioGraph, (draft) => {
                changes.forEach(({ nodeId, path, value }) => {
                    let processedPath = path;
                    if (nodeId) {
                        const nodeIndex = scenarioGraph.nodes.findIndex((n) => n.id === nodeId);
                        if (nodeIndex === -1) {
                            throw `invalid nodeId: ${nodeId}`;
                        }
                        processedPath = `nodes[${nodeIndex}].${path}`;
                    }
                    try {
                        set(draft, processedPath, value);
                    } catch (e) {
                        throw `invalid path: ${path}`;
                    }
                });
            });

            const response = await dispatch(preApplyValidation(scenarioBefore, scenarioGraphAfterChange));
            const validationResult = response?.data;

            const hasErrors =
                ["invalidNodes", "processPropertiesErrors", "globalErrors"].some((key) => validationResult?.errors[key]?.length > 0) ||
                typeof validationResult === "string";

            if (hasErrors) return validationResult;

            dispatch({
                type: "APPLY_GRAPH_CHANGES",
                validationResult,
                scenarioGraphAfterChange,
            });

            return { scenario: scenarioGraphAfterChange };
        } catch (error) {
            return error?.message || error;
        }
    };
}
