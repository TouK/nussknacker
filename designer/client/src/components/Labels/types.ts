export type AvailableScenarioLabels = {
    labels: string[];
};

export type ScenarioLabelValidationError = {
    label: string;
    messages: string[];
};

export type ScenarioLabelsValidationResponse = {
    validationErrors: ScenarioLabelValidationError[];
};
