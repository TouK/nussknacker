import {NotificationsState, reducer as notifications} from "react-notification-system-redux"
import {combineReducers} from "redux"
import {GraphState, reducerWithUndo as graphReducer} from "./graph"
import {reducer as httpErrorHandler} from "./httpErrorHandler"
import {reducer as settings, SettingsState} from "./settings"
import {UserSettings, userSettings} from "./userSettings"
import {StateWithHistory} from "redux-undo"
import {backendNotifications, BackendNotificationState} from "./notifications"
import {reducer as scenarioState, ScenarioStateState} from "./scenarioState"

export const reducer = combineReducers<RootState>({
  httpErrorHandler,
  graphReducer,
  settings,
  backendNotifications,
  notifications,
  userSettings,
  scenarioState,
})

export type RootState = {
  httpErrorHandler: ReturnType<typeof httpErrorHandler>,
  graphReducer: GraphState & { history: StateWithHistory<GraphState> },
  settings: SettingsState,
  backendNotifications: BackendNotificationState,
  notifications: NotificationsState,
  userSettings: UserSettings,
  scenarioState: ScenarioStateState,
}

export default reducer
