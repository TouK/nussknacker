// @flow
import type {LoggedUserAction, UiSettingsAction} from "./esp"
import type {AvailableQueryStatesAction} from "./esp/process/availableQueryStates"
import type {ProcessDefinitionDataAction} from "./esp/process/processDefinitionData"

export type SettingsActions =
    | LoggedUserAction
    | UiSettingsAction
    | ProcessDefinitionDataAction
    | AvailableQueryStatesAction