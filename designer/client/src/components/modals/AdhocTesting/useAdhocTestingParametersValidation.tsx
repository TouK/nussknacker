import { useCallback, useEffect, useState } from "react";

import { validateAdhocTestParameters } from "../../../actions/nk/adhocTesting";
import { getProcessName, getScenarioGraph } from "../../../reducers/selectors/graph";
import { useAppSelector } from "../../../store/storeHelpers";
import type { NodeValidationError } from "../../../types/validation";
import type { AdhocTestingParameters } from "./AdhocTestingDialog";
import type { ActionValues } from "./AdhocTestingFormContext";

export function useAdhocTestingParametersValidation(
    action: Pick<AdhocTestingParameters, "parameters" | "sourceId">,
    value: ActionValues,
): {
    isValid: boolean;
    errors: NodeValidationError[];
} {
    const { parameters, sourceId } = action;
    const scenarioName = useAppSelector(getProcessName);
    const scenarioGraph = useAppSelector(getScenarioGraph);
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
