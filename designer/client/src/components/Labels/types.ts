export type AvailableScenarioLabels = {
    labels: string[];
};

export type ScenarioLabelValidationError = {
    label: string;
    message: string;
}

export type ScenarioLabelsValidationResponse = {
    errors: ScenarioLabelValidationError[];
}
