import {Reducer as ReduxReducer, AnyAction} from "redux"
import {ThunkAction as TA,ThunkDispatch as TD} from "redux-thunk"

import {ActionTypes} from "./actionTypes"
import {DisplayProcessActivityAction, ReportEventAction, NodeActions} from "./nk"
import {FeatureFlagsActions} from "./nk/featureFlags"
import {UserSettingsActions} from "./nk/userSettings"
import {UiActions} from "./nk/ui/uiActions"
import {SettingsActions} from "./settingsActions"
import {ToolbarActions} from "./nk/toolbars"
import {RootState} from "../reducers"
import {UndoRedoActions} from "./undoRedoActions"
import {NodeDetailsActions} from "./nk/nodeDetails"

type TypedAction =
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

interface UntypedAction extends AnyAction {
  type: Exclude<ActionTypes, TypedAction["type"]>,
}

export type Action = UntypedAction | TypedAction

type State = RootState

export type ThunkAction<R = void, S = State> = TA<R, S, undefined, Action>
export type ThunkDispatch<S = State> = TD<S, undefined, Action>
export type Reducer<S> = ReduxReducer<S, Action>
