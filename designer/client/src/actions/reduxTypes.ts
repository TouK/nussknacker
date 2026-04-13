import type { Reducer as ReduxReducer, ThunkAction as TA } from "@reduxjs/toolkit";

import type { NodeSelectorActions } from "../components/toolbars/creator/nodeSelectorActions";
import type { RootState } from "../reducers";
import type { CloudDataActions } from "../reducers/cloudData";
import type { SquashHistoryActions } from "../reducers/graph/historySquash";
import type { ProcessActivityActions } from "../reducers/processActivity";
import type { ScenarioDraftActions } from "../reducers/scenarioDraft";
import type { ScenariosActions } from "../reducers/scenarios";
import type { ActionTypes } from "./actionTypes";
import type { AssistantActions } from "./assistantActions";
import type { CountsActions } from "./nk/displayProcessCounts";
import type { PropertiesActions } from "./nk/editProperties";
import type { LiveDataActions } from "./nk/liveData";
import type { NodeActions } from "./nk/node";
import type { NodeDetailsActions } from "./nk/nodeDetails";
import type { NotificationActions } from "./nk/notifications";
import type { ScenarioActions, UpdateTestCapabilitiesAction } from "./nk/process";
import type { GetScenarioActivitiesAction, UpdateScenarioActivitiesAction } from "./nk/scenarioActivities";
import type { SelectionActions } from "./nk/selection";
import type { TestCasesActions } from "./nk/testCasesActions";
import type { TestsActions } from "./nk/testingActions";
import type { ToolbarActions } from "./nk/toolbars";
import type { ToolWindowActions } from "./nk/toolWindow";
import type { UiActions } from "./nk/ui/uiActions";
import type { UserSettingsActions } from "./nk/userSettings";
import type { ValidationsActions } from "./nk/validationsActions";
import type { SettingsActions } from "./settingsActions";

export type TypedAction =
    | AssistantActions
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
    | ScenarioDraftActions
    | ScenariosActions
    | SelectionActions
    | SettingsActions
    | SquashHistoryActions
    | TestsActions
    | ToolWindowActions
    | ToolbarActions
    | UiActions
    | UpdateScenarioActivitiesAction
    | UpdateTestCapabilitiesAction
    | UserSettingsActions
    | ValidationsActions
    | TestCasesActions;

export interface UntypedAction {
    type: Exclude<ActionTypes, TypedAction["type"]>;
}

export type Action = UntypedAction | TypedAction;
export type ActionOfType<T extends Action["type"]> = Action & { type: T };

type State = RootState;

export type ThunkAction<R = unknown, S = State> = TA<R, S, undefined, Action>;
export type Reducer<S> = ReduxReducer<S, Action>;
