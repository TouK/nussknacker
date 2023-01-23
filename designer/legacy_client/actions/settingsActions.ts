import {UiSettingsAction} from "./nk/assignSettings"
import {LoggedUserAction} from "./nk/assignUser"
import {ProcessDefinitionDataAction} from "./nk/processDefinitionData"

export type SettingsActions =
    | LoggedUserAction
    | UiSettingsAction
    | ProcessDefinitionDataAction
