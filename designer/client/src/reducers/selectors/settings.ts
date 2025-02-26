import { createSelector, createSelectorCreator, defaultMemoize } from "reselect";
import { MetricsType } from "../../actions/nk";
import { DynamicTabData } from "../../containers/DynamicTab";
import { ComponentGroup, ProcessDefinitionData } from "../../types";
import { RootState } from "../index";
import { AuthenticationSettings, SettingsState } from "../settings";
import { isEqual, uniqBy } from "lodash";

const createDeepEqualSelector = createSelectorCreator(defaultMemoize, isEqual);

export const getSettings = (state: RootState): SettingsState => state.settings;

export const getAuthenticationSettings = createSelector(getSettings, (s): AuthenticationSettings => s.authenticationSettings);
export const getFeatureSettings = createSelector(getSettings, (s) => s.featuresSettings);
export const getEnvironmentAlert = createSelector(getFeatureSettings, (s) => s?.environmentAlert || {});
export const getTabs = createSelector(getFeatureSettings, (s): DynamicTabData[] => uniqBy(s.tabs || [], (t) => t.id));
export const getTargetEnvironmentId = createSelector(getFeatureSettings, (s) => s?.remoteEnvironment?.targetEnvironmentId);
export const getSurveySettings = createSelector(getFeatureSettings, (s) => s?.surveySettings);
export const getStickyNotesSettings = createSelector(getFeatureSettings, (s) => s?.stickyNotesSettings);
export const getLoggedUser = createSelector(getSettings, (s) => s.loggedUser);
export const getLoggedUserId = createSelector(getLoggedUser, (s) => s.id);
export const getProcessDefinitionData = createDeepEqualSelector(
    getSettings,
    (s) => s.processDefinitionData || ({} as ProcessDefinitionData),
);
export const getComponentGroups = createSelector(getProcessDefinitionData, (p) => p.componentGroups || []);
export const getCategories = createSelector(getLoggedUser, (u) => u.categories || []);
export const getWritableCategories = createSelector(getLoggedUser, getCategories, (user, categories) =>
    categories.filter((c) => user.canWrite(c)),
);
export const getMetricsSettings = createSelector(getFeatureSettings, (settings) => settings?.metrics || ({} as MetricsType));
