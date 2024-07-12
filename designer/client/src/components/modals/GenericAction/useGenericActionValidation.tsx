import { NodeValidationError } from "../../../types";
import { useCallback, useEffect, useState } from "react";
import { ActionValues } from "./GenericActionFormContext";
import { validateGenericActionParameters } from "../../../actions/nk/genericAction";
import { GenericAction } from "./GenericActionDialog";

export function useGenericActionValidation(
    action: Pick<GenericAction, "processingType" | "parameters" | "variableTypes">,
    value: ActionValues,
): {
    isValid: boolean;
    errors: NodeValidationError[];
} {
    const { processingType, parameters, variableTypes } = action;
    const [errors, setErrors] = useState<NodeValidationError[]>([]);

    const validate = useCallback(
        (value: ActionValues) => {
            validateGenericActionParameters(
                processingType,
                {
                    parameters: parameters.map((param) => ({
                        ...param,
                        expression: value[param.name],
                    })),
                    variableTypes,
                },
                ({ validationErrors }) => setErrors(validationErrors),
            );
        },
        [parameters, processingType, variableTypes],
    );

    useEffect(() => {
        validate(value);
    }, [validate, value]);

    return {
        errors,
        isValid: errors.length < 1,
    };
}
