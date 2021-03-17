import {UserSettings} from "../../reducers/userSettings"

type ToggleSettingsAction = {type: "TOGGLE_SETTINGS", settings: Array<keyof UserSettings>}

export function toggleSettings(settings: Array<keyof UserSettings>): ToggleSettingsAction {
  return {
    type: "TOGGLE_SETTINGS",
    settings,
  }
}

export type UserSettingsActions =
  | ToggleSettingsAction
