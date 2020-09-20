import {UnknownRecord} from "../../types/common"

type MetricsType = {
  url: string,
  defaultDashboard: string,
  processingTypeToDashboard: UnknownRecord,
}

type FeaturesSettings = {
  counts: boolean,
  attachments: boolean,
  signals: boolean,
  search: { url: string },
  metrics: MetricsType,
  remoteEnvironment: { targetEnvironmentId: string },
  environmentAlert: { content: string, cssClass: string },
  commentSettings: { matchExpression: string, link: string },
  intervalTimeSettings: { processes: number, healthCheck: number },
  deploySettings: $TodoType,
}

type AuthenticationSettings = {
  backend: string,
  authorizeUrl?: URL,
}

type EngineData = {
  actionTooltips: Record<string, string>,
  actionMessages: Record<string, string>,
  actionNames: Record<string, string>,
  actionIcons: Record<string, URL>,
}

type SettingsData = {
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
