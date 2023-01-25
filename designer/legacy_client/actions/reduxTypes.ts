import {AnyAction} from "redux"

import {ActionTypes} from "./actionTypes"
import {LoggedUserAction, UiSettingsAction} from "./nk"

type TypedAction =
  | LoggedUserAction
  | UiSettingsAction

interface UntypedAction extends AnyAction {
  type: Exclude<ActionTypes, TypedAction["type"]>,
}

export type Action = UntypedAction | TypedAction
