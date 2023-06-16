import { UnknownRecord } from "./common";

type MetricsType = {
    url: string;
    defaultDashboard: string;
    scenarioTypeToDashboard: UnknownRecord;
};

type UsageStatisticsReports = {
    enabled: boolean;
    url: string;
};

type SurveySettings = {
    link: string;
    text: string;
    key: string;
};

export type FeaturesSettings = {
    counts: boolean;
    search: { url: string };
    metrics: MetricsType;
    remoteEnvironment: { targetEnvironmentId: string };
    environmentAlert: { content: string; cssClass: string };
    commentSettings: { substitutionPattern: string; substitutionLink: string };
    deploymentCommentSettings?: { exampleComment: string };
    intervalTimeSettings: { processes: number; healthCheck: number };
    testDataSettings?: TestDataSettings;
    redirectAfterArchive: boolean;
    usageStatisticsReports: UsageStatisticsReports;
    surveySettings: SurveySettings;
    backendCodeSuggestions?: boolean;
};

type TestDataSettings = {
    maxSamplesCount: number;
    testDataMaxLength: number;
};
