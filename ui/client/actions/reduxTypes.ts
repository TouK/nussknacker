import {Reducer as ReduxReducer, Store as ReduxStore} from "redux"
import {ActionTypes} from "./actionTypes"
import {DisplayProcessActivityAction} from "./nk/displayProcessActivity"
import {ReportEventAction} from "./nk/reportEvent"
import {UiActions} from "./nk/ui/uiActions"
import {SettingsActions} from "./settingsActions"
import {ToolbarActions} from "./nk/toolbars"

export type Action =
    | ReportEventAction
    | UiActions
    | SettingsActions
    | DisplayProcessActivityAction
    | ToolbarActions

type A = { type: ActionTypes } | Action

type State = $TodoType
type Store = ReduxStore<State, Action>

type GetState = () => State
type PromiseAction = Promise<A>
type Dispatch = (action: A | ThunkAction | PromiseAction) => $TodoType

export type ThunkAction = (dispatch: Dispatch, getState: GetState) => $TodoType
export type Reducer<S> = ReduxReducer<S, A>
