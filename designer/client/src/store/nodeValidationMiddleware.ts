import { debounce } from "lodash";
import type { Middleware } from "redux";
import { ActionTypes as UndoActionTypes } from "redux-undo";

import { validationResult } from "../actions/nk";
import type { Action, ThunkDispatch } from "../actions/reduxTypes";
import HttpService from "../http/HttpService";
import type { RootState } from "../reducers";
import { getProcessName, getScenarioGraph, getUnsavedOrCurrentName } from "../reducers/selectors/graph";

type ActionType = Action["type"];

const debouncedValidate = debounce((dispatch: ThunkDispatch, getState: () => RootState) => {
    const state = getState();
    const scenarioName = getProcessName(state);
    const scenarioGraph = getScenarioGraph(state);
    const unsavedOrCurrentName = getUnsavedOrCurrentName(state);
    return HttpService.validateProcess(scenarioName, unsavedOrCurrentName, scenarioGraph).then(({ data }) => {
        return dispatch(validationResult(data));
    });
}, 500);

export function nodeValidationMiddleware(
    validatedActions: ActionType[] = [],
    ignoredActions: ActionType[] = [],
): Middleware<void, RootState, ThunkDispatch> {
    const ignore = ["VALIDATION_RESULT", UndoActionTypes.CLEAR_HISTORY, ...ignoredActions];
    const validate = [...Object.values(UndoActionTypes), ...validatedActions];
    const shouldValidate = (action: ActionType) => !ignore.includes(action) && validate.includes(action);

    return ({ dispatch, getState }) =>
        (next) =>
        (action) => {
            const result = next(action);

            if (shouldValidate(action.type)) {
                debouncedValidate(dispatch, getState);
            }

            return result;
        };
}
