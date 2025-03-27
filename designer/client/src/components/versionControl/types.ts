export type ProcessVersionValidationResponse = {
    processName: string;
    isLatest: boolean;
    localVersion: number;
    latestDeployedVersion: number | null;
    latestVersion: number;
    modifiedBy: string;
};
