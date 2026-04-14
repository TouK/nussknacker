import type { NotificationsReducer, NotificationsState } from "react-notification-system-redux";
import { reducer as notifications } from "react-notification-system-redux";
import { combineReducers } from "redux";

import type { Action } from "../actions/reduxTypes";
import type { ProcessStateType } from "../components/Process/types";
import { reducer as cloudData } from "./cloudData";
import type { LiveData } from "./graph/liveData";
import { liveData } from "./graph/liveData";
import type { GraphStateWithHistory } from "./graph/reducer";
import { reducerWithUndo as graphReducer } from "./graph/reducer";
import { reducer as httpErrorHandler } from "./httpErrorHandler";
import type { NodeDetailsState } from "./nodeDetailsState";
import { reducer as nodeDetails } from "./nodeDetailsState";
import { nodeWindowIdMap } from "./nodeWindowIdMap";
import type { BackendNotificationState } from "./notifications";
import { backendNotifications } from "./notifications";
import type { ProcessActivityState } from "./processActivity";
import { reducer as processActivity } from "./processActivity";
import { reducer as scenarios } from "./scenarios";
import { scenarioDraft } from "./scenarioDraft";
import { reducer as scenarioState } from "./scenarioState";
import type { SettingsState } from "./settings";
import { reducer as settings } from "./settings";
import type { ToolbarsStates } from "./toolbars";
import { toolbars } from "./toolbars";
import type { UiState } from "./ui";
import { reducer as ui } from "./ui";
import { userSettings } from "./userSettings";

export const rootReducer = combineReducers({
    httpErrorHandler,
    graphReducer,
    liveData,
    settings,
    ui,
    processActivity,
    backendNotifications,
    notifications: notifications as NotificationsReducer<Action>,
    toolbars,
    userSettings,
    nodeDetails,
    scenarioState,
    cloudData,
    scenarios,
    scenarioDraft,
    nodeWindowIdMap,
});

export type RootState = {
    httpErrorHandler: ReturnType<typeof httpErrorHandler>;
    graphReducer: GraphStateWithHistory;
    liveData: LiveData;
    settings: SettingsState;
    ui: UiState;
    processActivity: ProcessActivityState;
    backendNotifications: BackendNotificationState;
    notifications: NotificationsState;
    toolbars: ToolbarsStates;
    userSettings: ReturnType<typeof userSettings>;
    nodeDetails: NodeDetailsState;
    scenarioState: ProcessStateType;
    cloudData: ReturnType<typeof cloudData>;
    scenarios: ReturnType<typeof scenarios>;
    scenarioDraft: ReturnType<typeof scenarioDraft>;
    nodeWindowIdMap: ReturnType<typeof nodeWindowIdMap>;
};

export default rootReducer;
