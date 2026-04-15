import type { DynamicTabData } from "../../containers/DynamicTab";
import type { EnvironmentTagColor } from "../../containers/EnvironmentTag";
import type { AuthenticationSettings } from "../../reducers/settings";

export type MetricsType = {
    url: string;
    defaultDashboard: string;
    scenarioTypeToDashboard: Record<string, string>;
};

export type UsageStatisticsReports = {
    enabled: boolean;
    errorReportsEnabled: boolean;
    fingerprint: string;
};

export type SurveySettings = {
    link: string;
    text: string;
    key: string;
};

export interface EnvironmentTagSettings {
    content?: string;
    color?: EnvironmentTagColor;
}

export type FeaturesSettings = {
    assistant: {
        enabled: boolean;
    };
    counts: boolean;
    search: { url: string };
    metrics: MetricsType;
    remoteEnvironment: { targetEnvironmentId: string };
    environmentAlert: EnvironmentTagSettings;
    commentSettings: { substitutionPattern: string; substitutionLink: string };
    deploymentCommentSettings?: { exampleComment: string } | null;
    intervalTimeSettings: { processes: number; healthCheck: number }; // TODO: verify usage
    tabs: DynamicTabData[];
    testDataSettings?: TestDataSettings;
    redirectAfterArchive: boolean;
    usageStatisticsReports: UsageStatisticsReports;
    surveySettings: SurveySettings;
    stickyNotesSettings: StickyNotesSettings;
    testCases: { multipleEnabled: boolean };
};

export type StickyNotesSettings = {
    maxContentLength: number;
    maxNotesCount?: number;
    enabled: boolean;
};

export type TestDataSettings = {
    maxSamplesCount: number;
    testDataMaxLength: number;
};

type EngineData = {
    actionTooltips: Record<string, string>;
    actionMessages: Record<string, string>;
    actionNames: Record<string, string>;
    actionIcons: Record<string, URL>;
};

export interface SettingsData {
    features: FeaturesSettings;
    authentication: AuthenticationSettings;
    engines: Record<string, EngineData>;
    analytics?: $TodoType;
}

export type UiSettingsAction = {
    type: "UI_SETTINGS";
    settings: SettingsData;
};

export function assignSettings(settings: SettingsData): UiSettingsAction {
    return {
        type: "UI_SETTINGS",
        settings: settings,
    };
}
