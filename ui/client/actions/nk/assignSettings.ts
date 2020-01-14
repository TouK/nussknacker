import {$TodoType} from "../migrationTypes"

type MetricsType = {
  url: string;
  defaultDashboard: string;
  processingTypeToDashboard: {};
}

type FeaturesType = {
  counts: boolean;
  attachments: boolean;
  signals: boolean;
  search: { url: string };
  metrics: MetricsType;
  remoteEnvironment: { targetEnvironmentId: "development" | string };
  environmentAlert: { content: string; "cssClass": string };
  commentSettings: { matchExpression: string; link: string };
  intervalTimeSettings: { processes: number; healthCheck: number };
  deploySettings: $TodoType;
}

type SettingsData = {
  features: FeaturesType;
  authentication: {
    backend: string;
    authorizeUrl: string;
  };
  analytics: $TodoType;
}

export type UiSettingsAction = {
  type: "UI_SETTINGS";
  settings: SettingsData;
}

export function assignSettings(settings: SettingsData): UiSettingsAction {
  return {
    type: "UI_SETTINGS",
    settings: settings,
  }
}
