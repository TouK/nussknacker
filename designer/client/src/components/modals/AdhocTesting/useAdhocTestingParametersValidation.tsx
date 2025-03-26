import { useCallback, useEffect, useState } from "react";

import { validateAdhocTestParameters } from "../../../actions/nk/adhocTesting";
import type { NodeValidationError } from "../../../types";
import type { AdhocTestingParameters } from "./AdhocTestingDialog";
import type { ActionValues } from "./AdhocTestingFormContext";

export function useAdhocTestingParametersValidation(
    action: Pick<AdhocTestingParameters, "scenarioName" | "parameters" | "sourceId" | "scenarioGraph">,
    value: ActionValues,
    enabled = true,
): {
    adhocTestingIsValid: boolean;
    adhocTestingErrors: NodeValidationError[];
} {
    const { scenarioName, parameters, sourceId, scenarioGraph } = action;
    const [errors, setErrors] = useState<NodeValidationError[]>([]);

    const validate = useCallback(
        (value: ActionValues) => {
            if (enabled) {
                return validateAdhocTestParameters(
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
            } else {
                return;
            }
        },
        [parameters, scenarioName, scenarioGraph, sourceId, enabled],
    );

    useEffect(() => {
        validate(value);
    }, [validate, value]);

    return {
        adhocTestingErrors: errors,
        adhocTestingIsValid: errors.length < 1,
    };
}
