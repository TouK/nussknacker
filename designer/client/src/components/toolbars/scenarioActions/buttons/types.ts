export enum ScenarioActionResultType {
    Success = "SUCCESS",
    ValidationError = "VALIDATION_ERROR",
    UnhandledError = "UNHANDLED_ERROR",
    DeploySuccess = "DEPLOY_SUCCESS",
}

interface ScenarioActionMessage {
    msg: string;
}

export type ScenarioActionValidationError = ScenarioActionMessage & {
    scenarioActionResultType: ScenarioActionResultType.ValidationError;
};

export type ScenarioActionUnhandledError = ScenarioActionMessage & {
    scenarioActionResultType: ScenarioActionResultType.UnhandledError;
};

export type ScenarioActionResultSuccess = ScenarioActionMessage & {
    scenarioActionResultType: ScenarioActionResultType.Success;
};

export type ScenarioActionResultDeploySuccess = {
    scenarioActionResultType: ScenarioActionResultType.DeploySuccess;
    deployedScenarioVersionId: number;
};

export type ScenarioActionResult =
    | ScenarioActionValidationError
    | ScenarioActionUnhandledError
    | ScenarioActionResultSuccess
    | ScenarioActionResultDeploySuccess;
