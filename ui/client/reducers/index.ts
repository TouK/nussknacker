import {NotificationsState, reducer as notifications} from "react-notification-system-redux"
import {combineReducers} from "redux"
import {GraphState, reducerWithUndo as graphReducer} from "./graph"
import {reducer as httpErrorHandler} from "./httpErrorHandler"
import {ProcessActivityState, reducer as processActivity} from "./processActivity"
import {reducer as settings, SettingsState} from "./settings"
import {toolbars, ToolbarsStates} from "./toolbars"
import {NodeDetailsState, reducer as nodeDetails} from "./nodeDetailsState"
import {reducer as ui, UiState} from "./ui"
import {FeatureFlags, featureFlags} from "./featureFlags"
import {UserSettings, userSettings} from "./userSettings"
import {StateWithHistory} from "redux-undo"
import {backendNotifications, BackendNotificationState} from "./notifications";
import {reducer as scenarioState, ScenarioStateState} from "./scenarioState"

export const reducer = combineReducers<RootState>({
  httpErrorHandler,
  graphReducer,
  settings,
  ui,
  processActivity,
  backendNotifications,
  notifications,
  toolbars,
  featureFlags,
  userSettings,
  nodeDetails,
  scenarioState
})

export type RootState = {
  httpErrorHandler: ReturnType<typeof httpErrorHandler>,
  graphReducer: GraphState & { history: StateWithHistory<GraphState> },
  settings: SettingsState,
  ui: UiState,
  processActivity: ProcessActivityState,
  backendNotifications: BackendNotificationState,
  notifications: NotificationsState,
  toolbars: ToolbarsStates,
  featureFlags: FeatureFlags,
  userSettings: UserSettings,
  nodeDetails: NodeDetailsState,
  scenarioState: ScenarioStateState
}

export default reducer
