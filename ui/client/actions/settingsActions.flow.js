// @flow
import type {LoggedUserAction, UiSettingsAction} from "./nk"
import type {AvailableQueryStatesAction} from "./nk/availableQueryStates"
import type {ProcessDefinitionDataAction} from "./nk/processDefinitionData"

export type SettingsActions =
    | LoggedUserAction
    | UiSettingsAction
    | ProcessDefinitionDataAction
    | AvailableQueryStatesAction