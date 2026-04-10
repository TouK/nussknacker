import { uniqBy } from "lodash";
import { createSelector } from "reselect";

import type { MetricsType } from "../../actions/nk/assignSettings";
import type { DynamicTabData } from "../../containers/DynamicTab";
import type { RootState } from "../index";
import type { AuthenticationSettings, SettingsState } from "../settings";

export const getSettings = (state: RootState): SettingsState => state.settings;

export const getAuthenticationSettings = createSelector(
    getSettings,
    (s): AuthenticationSettings => s.authenticationSettings || ({} as AuthenticationSettings),
);
export const getFeatureSettings = createSelector(getSettings, (s) => s.featuresSettings);
export const getEnvironmentAlert = createSelector(getFeatureSettings, (s) => s?.environmentAlert || {});
export const getTabs = createSelector(getFeatureSettings, (s): DynamicTabData[] => uniqBy(s.tabs || [], (t) => t.id));
export const getTargetEnvironmentId = createSelector(getFeatureSettings, (s) => s?.remoteEnvironment?.targetEnvironmentId);
export const getSurveySettings = createSelector(getFeatureSettings, (s) => s?.surveySettings);
export const getStickyNotesSettings = createSelector(getFeatureSettings, (s) => s?.stickyNotesSettings);
export const getLoggedUser = createSelector(getSettings, (s) => s.loggedUser);
export const getLoggedUserId = createSelector(getLoggedUser, (s) => s.id);
export const getCategories = createSelector(getLoggedUser, (u) => u.categories || []);
export const getWritableCategories = createSelector(getLoggedUser, getCategories, (user, categories) =>
    categories.filter((c) => user.canWrite(c)),
);
export const getMetricsSettings = createSelector(getFeatureSettings, (settings) => settings?.metrics || ({} as MetricsType));
export const getMaxTestingRecords = createSelector(getSettings, (s) => s.featuresSettings.testDataSettings.maxSamplesCount);

export const getIsAssitantEnabled = createSelector(getFeatureSettings, (featureSettings) => featureSettings?.assistant.enabled);
export const getFlinkSqlTemplateEditorEnabled = createSelector(getFeatureSettings, (s) => s?.flinkSqlTemplateEditor ?? false);
