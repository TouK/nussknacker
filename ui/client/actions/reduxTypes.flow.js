// @flow

import type {Reducer as ReduxReducer, Store as ReduxStore} from "redux"
import type {ActionTypes} from "./actionTypes.flow"
import type {ReportEventAction} from "./nk"
import type {DisplayProcessActivityAction} from "./nk/displayProcessActivity"
import type {UiActions} from "./nk/ui/uiActions.flow"
import type {SettingsActions} from "./settingsActions.flow"

export type Action =
    | ReportEventAction
    | UiActions
    | SettingsActions
    | DisplayProcessActivityAction

type A = $Shape<{ type: ActionTypes, ... }> | Action

type State = $FlowTODO;
type Store = ReduxStore<State, Action>;

type GetState = () => State;
type PromiseAction = Promise<A>;
type Dispatch = (action: A | ThunkAction | PromiseAction) => any;

export type ThunkAction = (dispatch: Dispatch, getState: GetState) => any;
export type Reducer<S> = ReduxReducer<S, A>;