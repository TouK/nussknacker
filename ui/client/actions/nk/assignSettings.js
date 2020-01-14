// @flow
type MetricsType = {
  url: string,
  defaultDashboard: string,
  processingTypeToDashboard: {
    [key: string]: string,
  },
}

type FeaturesType = {
  counts: boolean,
  attachments: boolean,
  signals: boolean,
  search: { url: string },
  metrics: MetricsType,
  remoteEnvironment: { targetEnvironmentId: "development" | string },
  environmentAlert: { content: string, "cssClass": string },
  commentSettings: { matchExpression: string, link: string },
  intervalTimeSettings: { processes: number, healthCheck: number },
  deploySettings: $FlowTODO,
}

type SettingsData = {
  features: FeaturesType,
  authentication: {
    backend: string,
    authorizeUrl: ?string,
  },
  analytics: $FlowTODO,
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