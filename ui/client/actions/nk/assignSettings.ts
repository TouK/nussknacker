import {DynamicTabData} from "../../containers/DynamicTab"
import {AuthenticationSettings} from "../../reducers/settings"
import {UnknownRecord} from "../../types/common"

export type MetricsType = {
  url: string,
  defaultDashboard: string,
  processingTypeToDashboard: UnknownRecord,
}

export type FeaturesSettings = {
  counts: boolean,
  attachments: boolean,
  search: { url: string },
  metrics: MetricsType,
  remoteEnvironment: { targetEnvironmentId: string },
  environmentAlert: { content: string, cssClass: string },
  commentSettings: { matchExpression: string, link: string },
  intervalTimeSettings: { processes: number, healthCheck: number },
  deploySettings: { requireComment: boolean },
  tabs: DynamicTabData[],
  testDataSettings?: TestDataSettings
}

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
