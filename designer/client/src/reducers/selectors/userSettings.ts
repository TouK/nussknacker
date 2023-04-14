import {omitBy} from "lodash"
import {RootState} from "../index"
import {UserSettings} from "../userSettings"

export const getUserSettings = (state: RootState): UserSettings => omitBy(state.userSettings, (v, k) => k.startsWith("_"))
