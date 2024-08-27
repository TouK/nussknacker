import { AnyAction, Reducer as ReduxReducer } from "redux";
import { ThunkAction as TA, ThunkDispatch as TD } from "redux-thunk";

import { ActionTypes } from "./actionTypes";
import { CountsActions, DisplayProcessActivityAction, HandleHTTPErrorAction, NodeActions, ScenarioActions, SelectionActions } from "./nk";
import { UserSettingsActions } from "./nk/userSettings";
import { UiActions } from "./nk/ui/uiActions";
import { SettingsActions } from "./settingsActions";
import { ToolbarActions } from "./nk/toolbars";
import { RootState } from "../reducers";
import { NodeDetailsActions } from "./nk/nodeDetails";
import { NotificationActions } from "./nk/notifications";
import { DisplayTestResultsDetailsAction } from "./nk/displayTestResults";
import { LoadProcessVersionsAction } from "./nk/loadProcessVersions";

type TypedAction =
    | HandleHTTPErrorAction
    | UiActions
    | SettingsActions
    | DisplayProcessActivityAction
    | NodeActions
    | ToolbarActions
    | NodeDetailsActions
    | UserSettingsActions
    | SelectionActions
    | NotificationActions
    | DisplayTestResultsDetailsAction
    | CountsActions
    | LoadProcessVersionsAction
    | ScenarioActions;

interface UntypedAction extends AnyAction {
    type: Exclude<ActionTypes, TypedAction["type"]>;
}

export type Action = UntypedAction | TypedAction;

type State = RootState;

export type ThunkAction<R = void, S = State> = TA<R, S, undefined, Action>;
export type ThunkDispatch<S = State> = TD<S, undefined, Action>;
export type Reducer<S> = ReduxReducer<S, Action>;
