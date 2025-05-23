import type { Scenario } from "../components/Process/types";
import type { NodeResults, ValidationErrors, ValidationResult } from "../types";

export const getValidationResult = (scenario: Scenario): ValidationResult =>
    scenario?.validationResult || {
        validationErrors: [],
        validationWarnings: [],
        nodeResults: {},
        errors: {
            globalErrors: [],
            processPropertiesErrors: [],
            invalidNodes: {},
        },
    };

export const getValidationErrors = (scenario: Scenario): ValidationErrors => getValidationResult(scenario).errors;

export const getNodeResults = (scenario: Scenario): NodeResults => getValidationResult(scenario).nodeResults;
