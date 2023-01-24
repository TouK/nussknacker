import {createSelector} from "reselect"
import {RootState} from "../index"
import {AuthenticationSettings, SettingsState} from "../settings"
import {map} from "lodash"

export const getSettings = (state: RootState): SettingsState => state.settings

export const getAuthenticationSettings = createSelector(getSettings, (s): AuthenticationSettings => s.authenticationSettings)
export const getFeatureSettings = createSelector(getSettings, s => s.featuresSettings)
export const getLoggedUser = createSelector(getSettings, s => s.loggedUser)
export const getFilterCategories = createSelector(
  getLoggedUser,
  u => map(
    (u.categories || []).filter(c => u.canRead(c)),
    (e) => ({value: e, label: e})
  ) || []
)
export const getBaseIntervalTime = createSelector(getFeatureSettings, settings => settings?.intervalTimeSettings?.processes || 15000)
export const getHealthcheckIntervalTime = createSelector(getFeatureSettings, settings => settings?.intervalTimeSettings?.healthCheck || 40000)
