import {RootState} from "../../../reducers/index"
import {SettingsState} from "../../../reducers/settings"
import {createSelector} from "reselect"

const getSettings = (state: RootState): SettingsState => state.settings

export const getFeatureSettings = createSelector(getSettings, s => s.featuresSettings)
export const getLoggedUser = createSelector(getSettings, s => s.loggedUser)
