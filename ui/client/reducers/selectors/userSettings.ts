import {RootState} from "../index"
import {UserSettings} from "../userSettings"

export const userSettings = (state: RootState): UserSettings => state.userSettings
