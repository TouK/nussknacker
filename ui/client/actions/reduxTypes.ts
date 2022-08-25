import {AnyAction, Reducer as ReduxReducer} from "redux"
import {ThunkAction as TA, ThunkDispatch as TD} from "redux-thunk"

import {ActionTypes} from "./actionTypes"
import {
  DisplayProcessActivityAction,
  DisplayProcessCountsAction,
  HandleHTTPErrorAction,
  NodeActions,
  ReportEventAction,
  SelectionActions,
} from "./nk"
import {FeatureFlagsActions} from "./nk/featureFlags"
import {UserSettingsActions} from "./nk/userSettings"
import {UiActions} from "./nk/ui/uiActions"
import {SettingsActions} from "./settingsActions"
import {ToolbarActions} from "./nk/toolbars"
import {RootState} from "../reducers"
import {UndoRedoActions} from "./undoRedoActions"
import {NodeDetailsActions} from "./nk/nodeDetails"
import {NotificationActions} from "./nk/notifications"
import {DisplayTestResultsDetailsAction} from "./nk/displayTestResults"

type TypedAction =
  | HandleHTTPErrorAction
  | ReportEventAction
  | UiActions
  | SettingsActions
  | DisplayProcessActivityAction
  | NodeActions
  | ToolbarActions
  | NodeDetailsActions
  | UndoRedoActions
  | FeatureFlagsActions
  | UserSettingsActions
  | SelectionActions
  | NotificationActions
  | DisplayTestResultsDetailsAction
  | DisplayProcessCountsAction

interface UntypedAction extends AnyAction {
  type: Exclude<ActionTypes, TypedAction["type"]>,
}

export type Action = UntypedAction | TypedAction

type State = RootState

export type ThunkAction<R = void, S = State> = TA<R, S, undefined, Action>
export type ThunkDispatch<S = State> = TD<S, undefined, Action>
export type Reducer<S> = ReduxReducer<S, Action>
