import type { ThunkDispatch as TD } from "@reduxjs/toolkit";
import { produce } from "immer";
import { debounce } from "lodash";
import type { Middleware } from "redux";
import { ActionTypes as UndoActionTypes } from "redux-undo";

import type { Action } from "../actions/reduxTypes";
import { cleanNodeIdPlaceholder } from "../components/graph/node-modal/nodeIdFieldHelpers";
import HttpService from "../http/HttpService/instance";
import type { RootState } from "../reducers";
import { getProcessName, getScenarioGraph, getUnsavedOrCurrentName } from "../reducers/selectors/graph";

type ActionType = Action["type"];
type ThunkDispatch<S = RootState> = TD<S, undefined, Action>;

const debouncedValidate = debounce((dispatch: ThunkDispatch, getState: () => RootState) => {
    const state = getState();
    const scenarioName = getProcessName(state);
    const scenarioGraph = produce(getScenarioGraph(state), (draft) => {
        draft.nodes.forEach((node) => {
            node.id = cleanNodeIdPlaceholder(node.id);
        });
    });
    const unsavedOrCurrentName = getUnsavedOrCurrentName(state);
    return HttpService.validateProcess(scenarioName, unsavedOrCurrentName, scenarioGraph).then(({ data }) =>
        dispatch({ type: "VALIDATION_RESULT", validationResult: data }),
    );
}, 500);

export const scenarioValidationMiddleware = (validatedActions: ActionType[] = [], ignoredActions: ActionType[] = []): Middleware => {
    const ignore: (ActionType | NonNullable<string>)[] = ["VALIDATION_RESULT", UndoActionTypes.CLEAR_HISTORY, ...ignoredActions];
    const validate: (ActionType | NonNullable<string>)[] = [...Object.values(UndoActionTypes), ...validatedActions];
    const shouldValidate = (action: ActionType) => !ignore.includes(action) && validate.includes(action);

    return ({ dispatch, getState }) =>
        (next) =>
        (action: Action) => {
            const result = next(action);

            if (shouldValidate(action.type)) {
                debouncedValidate(dispatch, getState);
            }

            return result;
        };
};
