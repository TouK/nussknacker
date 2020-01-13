// @flow
import type {LoggedUserAction, UiSettingsAction} from "./esp"
import type {AvailableQueryStatesAction} from "./esp/availableQueryStates"
import type {ProcessDefinitionDataAction} from "./esp/processDefinitionData"

export type SettingsActions =
    | LoggedUserAction
    | UiSettingsAction
    | ProcessDefinitionDataAction
    | AvailableQueryStatesAction