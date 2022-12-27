import {DynamicTabData} from "../../containers/DynamicTab"
import {AuthenticationSettings} from "../../reducers/settings"
import {UnknownRecord} from "../../types/common"

export type MetricsType = {
  url: string,
  defaultDashboard: string,
  scenarioTypeToDashboard: UnknownRecord,
}

export type UsageStatisticsReports = {
  enabled: boolean,
  url: string,
}

export type SurveySettings ={
  link: string,
  text: string,
  key: string,
}

export type FeaturesSettings = {
  counts: boolean,
  search: { url: string },
  metrics: MetricsType,
  remoteEnvironment: { targetEnvironmentId: string },
  environmentAlert: { content: string, cssClass: string },
  commentSettings: { substitutionPattern: string, substitutionLink: string },
  deploymentCommentSettings?: { exampleComment: string },
  intervalTimeSettings: { processes: number, healthCheck: number },
  tabs: DynamicTabData[],
  testDataSettings?: TestDataSettings,
  redirectAfterArchive: boolean,
  usageStatisticsReports: UsageStatisticsReports,
  surveySettings: SurveySettings,
};

export type TestDataSettings = {
  maxSamplesCount: number,
  testDataMaxBytes: number
}

type EngineData = {
  actionTooltips: Record<string, string>,
  actionMessages: Record<string, string>,
  actionNames: Record<string, string>,
  actionIcons: Record<string, URL>,
}

export interface SettingsData {
  features: FeaturesSettings,
  authentication: AuthenticationSettings,
  engines: Record<string, EngineData>,
  analytics?: $TodoType,
}

export type UiSettingsAction = {
  type: "UI_SETTINGS",
  settings: SettingsData,
}

export function assignSettings(settings: SettingsData): UiSettingsAction {
  return {
    type: "UI_SETTINGS",
    settings: settings,
  }
}
