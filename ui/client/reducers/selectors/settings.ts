import {createSelector} from "reselect"
import {MetricsType} from "../../actions/nk"
import ProcessUtils from "../../common/ProcessUtils"
import {DynamicTabData} from "../../containers/DynamicTab"
import {ProcessDefinitionData} from "../../types"
import {RootState} from "../index"
import {AuthenticationSettings, SettingsState} from "../settings"

export const getSettings = (state: RootState): SettingsState => state.settings

export const getAuthenticationSettings = createSelector(getSettings, (s): AuthenticationSettings => s.authenticationSettings)
export const getFeatureSettings = createSelector(getSettings, s => s.featuresSettings)
export const getTabs = createSelector(getFeatureSettings, (s): DynamicTabData[] => s.tabs ?
  [
    ...s.tabs,
    {type: "Remote", id: "tools", title: "Tools", url: "nkTools/nkTab@http://localhost:5001/remoteEntry.js"},
  ] :
  [])
export const getTargetEnvironmentId = createSelector(getFeatureSettings, s => s?.remoteEnvironment?.targetEnvironmentId)
export const getLoggedUser = createSelector(getSettings, s => s.loggedUser)
export const getProcessDefinitionData = createSelector(getSettings, s => s.processDefinitionData || {} as ProcessDefinitionData)
export const getCategories = createSelector(getLoggedUser, u => u.categories || [])
export const getWritableCategories = createSelector(getLoggedUser, getCategories, (user, categories) => categories.filter(c => user.canWrite(c)))
export const getFilterCategories = createSelector(getLoggedUser, u => ProcessUtils.prepareFilterCategories(u.categories, u) || [])
export const getBaseIntervalTime = createSelector(getFeatureSettings, settings => settings?.intervalTimeSettings?.processes || 15000)
export const getHealthcheckIntervalTime = createSelector(getFeatureSettings, settings => settings?.intervalTimeSettings?.healthCheck || 40000)
export const getMetricsSettings = createSelector(getFeatureSettings, settings => settings?.metrics || {} as MetricsType)
export const getCustomActions = createSelector(getProcessDefinitionData, def => def.customActions || [])
