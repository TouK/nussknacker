import {SettingsData} from "./nk"
import User from "../common/models/User"

export type Action =
  | { type: "LOGGED_USER", user: User }
  | { type: "UI_SETTINGS", settings: SettingsData }
