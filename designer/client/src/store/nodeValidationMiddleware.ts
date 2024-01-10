import { Action, ThunkDispatch } from "../actions/reduxTypes";
import { Middleware } from "redux";
import { RootState } from "../reducers";
import { ActionTypes as UndoActionTypes } from "redux-undo";
import { debounce } from "lodash";
import HttpService from "../http/HttpService";
import { getScenarioWithUnsavedName } from "../reducers/selectors/graph";

type ActionType = Action["type"];

const debouncedValidate = debounce(
    (dispatch: ThunkDispatch, getState: () => RootState) =>
        HttpService.validateProcess(getScenarioWithUnsavedName(getState()).json).then(({ data }) =>
            dispatch({ type: "VALIDATION_RESULT", validationResult: data }),
        ),
    500,
);

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
