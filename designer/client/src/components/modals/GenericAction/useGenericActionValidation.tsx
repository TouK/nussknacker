import { NodeValidationError } from "../../../types";
import { useCallback, useEffect, useState } from "react";
import { ActionValues } from "./GenericActionFormContext";
import { validateGenericActionParameters } from "../../../actions/nk/genericAction";
import { GenericAction } from "./GenericActionDialog";
import { SourceWithParametersTest } from "../../../http/HttpService";

export function useGenericActionValidation(
    action: Pick<GenericAction, "scenarioName" | "parameters" | "sourceId" | "scenarioGraph">,
    value: ActionValues,
): {
    isValid: boolean;
    errors: NodeValidationError[];
} {
    const { scenarioName, parameters, sourceId, scenarioGraph } = action;
    const [errors, setErrors] = useState<NodeValidationError[]>([]);

    const validate = useCallback(
        (value: ActionValues) => {
            validateGenericActionParameters(
                scenarioName,
                {
                    sourceParameters: {
                        sourceId,
                        parameterExpressions: parameters.reduce(
                            (obj, param) => ({
                                ...obj,
                                [param.name]: value[param.name],
                            }),
                            {},
                        ),
                    },
                    scenarioGraph: scenarioGraph,
                },
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
