export type ProcessVersionValidationResponse = {
    processName: string;
    isLatest: boolean;
    localVersion: number;
    latestVersion: number;
};
