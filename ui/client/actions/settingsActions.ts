import {AvailableQueryStatesAction, LoggedUserAction, ProcessDefinitionDataAction, UiSettingsAction} from "./nk"

export type SettingsActions =
    | LoggedUserAction
    | UiSettingsAction
    | ProcessDefinitionDataAction
    | AvailableQueryStatesAction