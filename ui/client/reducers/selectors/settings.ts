import {RootState} from "../index"
import {SettingsState} from "../settings"
import {createSelector} from "reselect"
import {ProcessDefinitionData} from "../../types"
import ProcessUtils from "../../common/ProcessUtils"

const getSettings = (state: RootState): SettingsState => state.settings

export const getFeatureSettings = createSelector(getSettings, s => s.featuresSettings)
export const getLoggedUser = createSelector(getSettings, s => s.loggedUser)
export const getProcessDefinitionData = createSelector(getSettings, s => s.processDefinitionData || {} as ProcessDefinitionData)
export const getFilterCategories = createSelector(getLoggedUser, u => ProcessUtils.prepareFilterCategories(u.categories, u) || [])
