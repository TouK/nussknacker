import {RootState} from "../index"
import {SettingsState} from "../settings"
import {createSelector} from "reselect"

const getSettings = (state: RootState): SettingsState => state.settings

export const getProcessDefinitionData = createSelector(getSettings, s => s.processDefinitionData || {})
