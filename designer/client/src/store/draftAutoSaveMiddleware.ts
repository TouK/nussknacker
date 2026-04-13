/* eslint-disable i18next/no-literal-string */
import type { ThunkDispatch as TD } from "@reduxjs/toolkit";
import { debounce, isEqual } from "lodash";
import type { Middleware } from "redux";

import type { Action } from "../actions/reduxTypes";
import { draftStorage } from "../draftStorage/instance";
import HttpService from "../http/HttpService/instance";
import type { RootState } from "../reducers";
import {
    getProcessName,
    getProcessVersionId,
    getScenarioGraph,
    getUnsavedOrCurrentName,
} from "../reducers/selectors/graph";

type ActionType = Action["type"];
type ThunkDispatch = TD<RootState, undefined, Action>;

const MUTATION_ACTIONS: ActionType[] = [
    "NODE_ADDED",
    "DELETE_NODES",
    "NODES_CONNECTED",
    "NODES_DISCONNECTED",
    "NODES_WITH_EDGES_ADDED",
    "STICKY_NOTE_UPDATED",
    "NODE_DETAILS_CLOSED",
    "TOOL_CLOSED",
    "LAYOUT_CHANGED",
    "EDIT_NODE",
    "EDIT_PROPERTIES",
];

const debouncedSave = debounce((getState: () => RootState) => {
    const state = getState();
    const processName = getProcessName(state);
    if (!processName) return;
    const scenarioGraph = getScenarioGraph(state);
    const baseVersionId = getProcessVersionId(state);
    draftStorage
        .save(processName, {
            scenarioGraph,
            baseVersionId,
            updatedAt: new Date().toISOString(),
        })
        .catch(() => {
            // best-effort; localStorage may be full or disabled
        });
}, 500);

const restoreIfPresent = async (dispatch: ThunkDispatch, getState: () => RootState) => {
    const state = getState();
    const processName = getProcessName(state);
    if (!processName) return;
    const draft = await draftStorage.get(processName);
    if (!draft) return;

    const serverVersion = getProcessVersionId(state);
    if (draft.baseVersionId !== serverVersion) {
        await draftStorage.delete(processName);
        return;
    }

    const serverGraph = getScenarioGraph(state);
    if (isEqual(draft.scenarioGraph, serverGraph)) {
        await draftStorage.delete(processName);
        return;
    }

    dispatch({
        type: "UPDATE_IMPORTED_PROCESS",
        importedScenarioData: {
            scenarioGraph: draft.scenarioGraph,
            validationResult: state.graphReducer.present.scenario.validationResult,
        },
    });

    const unsavedOrCurrentName = getUnsavedOrCurrentName(getState());
    HttpService.validateProcess(processName, unsavedOrCurrentName, draft.scenarioGraph)
        .then(({ data }) => dispatch({ type: "VALIDATION_RESULT", validationResult: data }))
        .catch(() => {
            // ignore — validation will re-run on next edit
        });
};

export const draftAutoSaveMiddleware: Middleware<unknown, RootState, ThunkDispatch> = ({ dispatch, getState }) => {
    return (next) => (action: Action) => {
        const result = next(action);

        if (action.type === "DISPLAY_PROCESS") {
            restoreIfPresent(dispatch, getState).catch(() => undefined);
        } else if (MUTATION_ACTIONS.includes(action.type)) {
            debouncedSave(getState);
        }

        return result;
    };
};
