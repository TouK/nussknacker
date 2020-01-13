import {Reducer as ReduxReducer, Store as ReduxStore} from "redux"
import {ActionTypes} from "./actionTypes"
import {$FlowTODO} from "./migrationTypes"
import {ReportEventAction} from "./nk"
import {DisplayProcessActivityAction} from "./nk/displayProcessActivity"
import {UiActions} from "./nk/ui/uiActions.flow"
import {SettingsActions} from "./settingsActions"

export type Action =
    | ReportEventAction
    | UiActions
    | SettingsActions
    | DisplayProcessActivityAction

type A = { type: ActionTypes } | Action

type State = $FlowTODO;
type Store = ReduxStore<State, Action>;

type GetState = () => State;
type PromiseAction = Promise<A>;
type Dispatch = (action: A | ThunkAction | PromiseAction) => any;

export type ThunkAction = (dispatch: Dispatch, getState: GetState) => any;
export type Reducer<S> = ReduxReducer<S, A>;