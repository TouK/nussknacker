import type { NotificationsState } from "react-notification-system-redux";
import { reducer as notifications } from "react-notification-system-redux";
import { combineReducers } from "redux";

import type { ProcessStateType } from "../components/Process/types";
import { reducer as cloudData } from "./cloudData";
import type { GraphStateWithHistory } from "./graph";
import { reducerWithUndo as graphReducer } from "./graph";
import { reducer as httpErrorHandler } from "./httpErrorHandler";
import type { NodeDetailsState } from "./nodeDetailsState";
import { reducer as nodeDetails } from "./nodeDetailsState";
import { nodeWindowIdMap } from "./nodeWindowIdMap";
import type { BackendNotificationState } from "./notifications";
import { backendNotifications } from "./notifications";
import type { ProcessActivityState } from "./processActivity";
import { reducer as processActivity } from "./processActivity";
import { reducer as scenarios } from "./scenarios";
import { reducer as scenarioState } from "./scenarioState";
import type { SettingsState } from "./settings";
import { reducer as settings } from "./settings";
import type { ToolbarsStates } from "./toolbars";
import { toolbars } from "./toolbars";
import type { UiState } from "./ui";
import { reducer as ui } from "./ui";
import type { UserSettings } from "./userSettings";
import { userSettings } from "./userSettings";

export const reducer = combineReducers<RootState>({
    httpErrorHandler,
    graphReducer,
    settings,
    ui,
    processActivity,
    backendNotifications,
    notifications,
    toolbars,
    userSettings,
    nodeDetails,
    scenarioState,
    cloudData,
    scenarios,
    nodeWindowIdMap,
});

export type RootState = {
    httpErrorHandler: ReturnType<typeof httpErrorHandler>;
    graphReducer: GraphStateWithHistory;
    settings: SettingsState;
    ui: UiState;
    processActivity: ProcessActivityState;
    backendNotifications: BackendNotificationState;
    notifications: NotificationsState;
    toolbars: ToolbarsStates;
    userSettings: UserSettings;
    nodeDetails: NodeDetailsState;
    scenarioState: ProcessStateType;
    cloudData: ReturnType<typeof cloudData>;
    scenarios: ReturnType<typeof scenarios>;
    nodeWindowIdMap: ReturnType<typeof nodeWindowIdMap>;
};

export default reducer;
