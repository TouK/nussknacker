import {UserSettings} from "../../reducers/userSettings"

type ToggleSettingsAction = {type: "TOGGLE_SETTINGS", settings: Array<keyof UserSettings>}
type SetSettingsAction = {type: "SET_SETTINGS", settings: UserSettings}

export function toggleSettings(settings: Array<keyof UserSettings>): ToggleSettingsAction {
  return {
    type: "TOGGLE_SETTINGS",
    settings,
  }
}
export function setSettings(settings: UserSettings): SetSettingsAction {
  return {
    type: "SET_SETTINGS",
    settings,
  }
}

export type UserSettingsActions =
  | ToggleSettingsAction
  | SetSettingsAction
