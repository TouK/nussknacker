import { NotificationsState, reducer as notifications } from "react-notification-system-redux";
import { combineReducers } from "redux";
import { StateWithHistory } from "redux-undo";
import { ProcessStateType } from "../components/Process/types";
import { reducer as cloudData } from "./cloudData";
import { GraphState, reducerWithUndo as graphReducer } from "./graph";
import { reducer as httpErrorHandler } from "./httpErrorHandler";
import { NodeDetailsState, reducer as nodeDetails } from "./nodeDetailsState";
import { backendNotifications, BackendNotificationState } from "./notifications";
import { ProcessActivityState, reducer as processActivity } from "./processActivity";
import { reducer as scenarioState } from "./scenarioState";
import { reducer as settings, SettingsState } from "./settings";
import { toolbars, ToolbarsStates } from "./toolbars";
import { reducer as ui, UiState } from "./ui";
import { UserSettings, userSettings } from "./userSettings";

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
});

export type RootState = {
    httpErrorHandler: ReturnType<typeof httpErrorHandler>;
    graphReducer: GraphState & { history: StateWithHistory<GraphState> };
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
};

export default reducer;
