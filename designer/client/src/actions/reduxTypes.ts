import { AnyAction, Reducer as ReduxReducer } from "redux";
import { ThunkAction as TA, ThunkDispatch as TD } from "redux-thunk";
import { RootState } from "../reducers";
import { CloudDataActions } from "../reducers/cloudData";

import { ActionTypes } from "./actionTypes";
import { CountsActions, NodeActions, NodeDetailsActions, PropertiesActions, ScenarioActions, SelectionActions } from "./nk";
import { DisplayTestResultsDetailsAction } from "./nk/displayTestResults";
import { NotificationActions } from "./nk/notifications";
import { GetScenarioActivitiesAction, UpdateScenarioActivitiesAction } from "./nk/scenarioActivities";
import { ToolbarActions } from "./nk/toolbars";
import { UiActions } from "./nk/ui/uiActions";
import { UserSettingsActions } from "./nk/userSettings";
import { SettingsActions } from "./settingsActions";

type TypedAction =
    | UiActions
    | SettingsActions
    | GetScenarioActivitiesAction
    | UpdateScenarioActivitiesAction
    | NodeActions
    | ToolbarActions
    | NodeDetailsActions
    | UserSettingsActions
    | SelectionActions
    | NotificationActions
    | DisplayTestResultsDetailsAction
    | CountsActions
    | ScenarioActions
    | PropertiesActions
    | CloudDataActions;

interface UntypedAction extends AnyAction {
    type: Exclude<ActionTypes, TypedAction["type"]>;
}

export type Action = UntypedAction | TypedAction;

type State = RootState;

export type ThunkAction<R = void, S = State> = TA<R, S, undefined, Action>;
export type ThunkDispatch<S = State> = TD<S, undefined, Action>;
export type Reducer<S> = ReduxReducer<S, Action>;
