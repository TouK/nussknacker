import {UiSettingsAction} from "./nk/assignSettings"
import {LoggedUserAction} from "./nk/assignUser"
import {AvailableQueryStatesAction} from "./nk/availableQueryStates"
import {ProcessDefinitionDataAction} from "./nk/processDefinitionData"

export type SettingsActions =
    | LoggedUserAction
    | UiSettingsAction
    | ProcessDefinitionDataAction
    | AvailableQueryStatesAction
