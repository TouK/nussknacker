import {reducer as notifications} from "react-notification-system-redux"
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

export const reducer = combineReducers<RootState>({
  httpErrorHandler,
  graphReducer,
  settings,
  ui,
  processActivity,
  notifications,
  toolbars,
  featureFlags,
  userSettings,
  nodeDetails,
})

export type RootState = {
  httpErrorHandler: unknown,
  graphReducer: GraphState & {history: StateWithHistory<GraphState> },
  settings: SettingsState,
  ui: UiState,
  processActivity: ProcessActivityState,
  notifications: unknown,
  toolbars: ToolbarsStates,
  featureFlags: FeatureFlags,
  userSettings: UserSettings,
  nodeDetails: NodeDetailsState,
}

export default reducer
