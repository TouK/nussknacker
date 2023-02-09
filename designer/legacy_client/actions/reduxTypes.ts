import {AnyAction, Reducer as ReduxReducer} from "redux"
import {ThunkAction as TA, ThunkDispatch as TD} from "redux-thunk"

import {ActionTypes} from "./actionTypes"
import {DisplayProcessActivityAction, DisplayProcessCountsAction, HandleHTTPErrorAction, ReportEventAction} from "./nk"
import {FeatureFlagsActions} from "./nk/featureFlags"
import {UserSettingsActions} from "./nk/userSettings"
import {SettingsActions} from "./settingsActions"
import {RootState} from "../reducers"
import {UndoRedoActions} from "./undoRedoActions"
import {NotificationActions} from "./nk/notifications"

type TypedAction =
  | HandleHTTPErrorAction
  | ReportEventAction
  | SettingsActions
  | DisplayProcessActivityAction
  | UndoRedoActions
  | FeatureFlagsActions
  | UserSettingsActions
  | NotificationActions
  | DisplayProcessCountsAction

interface UntypedAction extends AnyAction {
  type: Exclude<ActionTypes, TypedAction["type"]>,
}

export type Action = UntypedAction | TypedAction

type State = RootState

export type ThunkAction<R = void, S = State> = TA<R, S, undefined, Action>
export type ThunkDispatch<S = State> = TD<S, undefined, Action>
export type Reducer<S> = ReduxReducer<S, Action>
