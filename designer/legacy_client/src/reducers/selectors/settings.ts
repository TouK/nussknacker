import {createSelector} from "reselect"
import {SettingsState} from "../settings"
import {map} from "lodash"
import {useSelector} from "react-redux"

const getAuthenticationSettings = (s: SettingsState) => s.authenticationSettings
const getLoggedUser = (s: SettingsState) => s.loggedUser
const getBaseIntervalTime = (s: SettingsState) => s.featuresSettings?.intervalTimeSettings?.processes || 15000
const getHealthcheckIntervalTime = (s: SettingsState) => s.featuresSettings?.intervalTimeSettings?.healthCheck || 40000
const getFilterCategories = createSelector(
  getLoggedUser,
  u => map(
    (u.categories || []).filter(c => u.canRead(c)),
    (e) => ({value: e, label: e})
  ) || []
)

export const useAuthenticationSettings = () => useSelector(getAuthenticationSettings)
export const useLoggedUser = () => useSelector(getLoggedUser)
export const useBaseIntervalTime = () => useSelector(getBaseIntervalTime)
export const useHealthcheckIntervalTime = () => useSelector(getHealthcheckIntervalTime)
export const useFilterCategories = () => useSelector(getFilterCategories)
