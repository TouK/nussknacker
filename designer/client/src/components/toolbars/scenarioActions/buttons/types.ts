export enum ScenarioActionResultType {
    Success = "SUCCESS",
    ValidationError = "VALIDATION_ERROR",
    UnhandledError = "UNHANDLED_ERROR",
}

export type ScenarioActionResult = {
    scenarioActionResultType: ScenarioActionResultType;
    msg: string;
};
