import type { Reducer as ReduxReducer, ThunkAction as TA } from "@reduxjs/toolkit";

import type { NodeSelectorActions } from "../components/toolbars/creator/nodeSelectorActions";
import type { RootState } from "../reducers";
import type { CloudDataActions } from "../reducers/cloudData";
import type { SquashHistoryActions } from "../reducers/graph/historySquash";
import type { ProcessActivityActions } from "../reducers/processActivity";
import type { ScenariosActions } from "../reducers/scenarios";
import type { ActionTypes } from "./actionTypes";
import type {
    CountsActions,
    NodeActions,
    NodeDetailsActions,
    PropertiesActions,
    ScenarioActions,
    SelectionActions,
    UpdateTestCapabilitiesAction,
} from "./nk";
import type { TestsActions } from "./nk/displayTestResults";
import type { LiveDataActions } from "./nk/liveData";
import type { NotificationActions } from "./nk/notifications";
import type { GetScenarioActivitiesAction, UpdateScenarioActivitiesAction } from "./nk/scenarioActivities";
import type { ToolbarActions } from "./nk/toolbars";
import type { UiActions } from "./nk/ui/uiActions";
import type { UserSettingsActions } from "./nk/userSettings";
import type { SettingsActions } from "./settingsActions";

type TypedAction =
    | CloudDataActions
    | CountsActions
    | GetScenarioActivitiesAction
    | LiveDataActions
    | NodeActions
    | NodeDetailsActions
    | NodeSelectorActions
    | NotificationActions
    | ProcessActivityActions
    | PropertiesActions
    | ScenarioActions
    | ScenariosActions
    | SelectionActions
    | SettingsActions
    | SquashHistoryActions
    | TestsActions
    | ToolbarActions
    | UiActions
    | UpdateScenarioActivitiesAction
    | UpdateTestCapabilitiesAction
    | UserSettingsActions;

interface UntypedAction {
    type: Exclude<ActionTypes, TypedAction["type"]>;
}

export type Action = UntypedAction | TypedAction;

type State = RootState;

export type ThunkAction<R = void, S = State> = TA<R, S, undefined, Action>;
export type Reducer<S> = ReduxReducer<S, Action>;
