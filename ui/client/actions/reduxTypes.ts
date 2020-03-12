import {Reducer as ReduxReducer, Store as ReduxStore} from "redux"
import {ActionTypes} from "./actionTypes"
import {DisplayProcessActivityAction} from "./nk/displayProcessActivity"
import {ReportEventAction} from "./nk/reportEvent"
import {UiActions} from "./nk/ui/uiActions"
import {SettingsActions} from "./settingsActions"

export type Action =
    | ReportEventAction
    | UiActions
    | SettingsActions
    | DisplayProcessActivityAction

type A = { type: ActionTypes } | Action

type State = $TodoType
type Store = ReduxStore<State, Action>

type GetState<S> = () => S
type PromiseAction = Promise<A>
type Dispatch = (action: A | ThunkAction | PromiseAction) => $TodoType

export type ThunkAction<S = State> = (dispatch: Dispatch, getState: GetState<S>) => $TodoType
export type Reducer<S> = ReduxReducer<S, A>
