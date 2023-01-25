import {combineReducers} from "redux"
import {reducer as settings, SettingsState} from "./settings"

export const reducer = combineReducers<RootState>({
  settings,
})

export type RootState = {
  settings: SettingsState,
}

export default reducer
