import {Reducer as ReduxReducer, Store as ReduxStore} from "redux"
import {ActionTypes} from "./actionTypes"
import {$TodoType} from "./migrationTypes"
import {DisplayProcessActivityAction} from "./nk/displayProcessActivity"
import {ReportEventAction} from "./nk/reportEvent"
import {UiActions} from "./nk/ui/uiActions.flow"
import {SettingsActions} from "./settingsActions"

export type Action =
    | ReportEventAction
    | UiActions
    | SettingsActions
    | DisplayProcessActivityAction

type A = { type: ActionTypes } | Action

type State = $TodoType;
type Store = ReduxStore<State, Action>;

type GetState = () => State;
type PromiseAction = Promise<A>;
type Dispatch = (action: A | ThunkAction | PromiseAction) => $TodoType;

export type ThunkAction = (dispatch: Dispatch, getState: GetState) => $TodoType;
export type Reducer<S> = ReduxReducer<S, A>;
