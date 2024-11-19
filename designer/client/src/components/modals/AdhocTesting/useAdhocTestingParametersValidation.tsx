import { NodeValidationError } from "../../../types";
import { useCallback, useEffect, useState } from "react";
import { ActionValues } from "./AdhocTestingFormContext";
import { validateAdhocTestParameters } from "../../../actions/nk/adhocTesting";
import { AdhocTestingParameters } from "./AdhocTestingDialog";

export function useAdhocTestingParametersValidation(
    action: Pick<AdhocTestingParameters, "scenarioName" | "parameters" | "sourceId" | "scenarioGraph">,
    value: ActionValues,
): {
    isValid: boolean;
    errors: NodeValidationError[];
} {
    const { scenarioName, parameters, sourceId, scenarioGraph } = action;
    const [errors, setErrors] = useState<NodeValidationError[]>([]);

    const validate = useCallback(
        (value: ActionValues) => {
            validateAdhocTestParameters(
                scenarioName,
                {
                    sourceId,
                    parameterExpressions: parameters.reduce(
                        (obj, param) => ({
                            ...obj,
                            [param.name]: value[param.name],
                        }),
                        {},
                    ),
                },
                scenarioGraph,
                ({ validationErrors }) => setErrors(validationErrors),
            );
        },
        [parameters, scenarioName, scenarioGraph, sourceId],
    );

    useEffect(() => {
        validate(value);
    }, [validate, value]);

    return {
        errors,
        isValid: errors.length < 1,
    };
}
