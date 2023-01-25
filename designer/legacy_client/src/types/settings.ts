import {AuthenticationSettings} from "../reducers/settings"
import {UnknownRecord} from "./common"

type MetricsType = {
  url: string,
  defaultDashboard: string,
  scenarioTypeToDashboard: UnknownRecord,
}

type UsageStatisticsReports = {
  enabled: boolean,
  url: string,
}

type SurveySettings = {
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
  testDataSettings?: TestDataSettings,
  redirectAfterArchive: boolean,
  usageStatisticsReports: UsageStatisticsReports,
  surveySettings: SurveySettings,
}

type TestDataSettings = {
  maxSamplesCount: number,
  testDataMaxLength: number,
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

