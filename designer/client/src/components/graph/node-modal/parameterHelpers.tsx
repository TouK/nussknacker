import { useMemo } from "react";

import type { Parameter, UIParameter } from "../../../types";

export function getParamIndex(parameters: Parameter[], paramName: string) {
    return parameters?.findIndex((param) => param.name === paramName);
}

export function useParameterPath(parameters: Parameter[], paramName: string): string {
    const index = useMemo(() => getParamIndex(parameters, paramName), [paramName, parameters]);
    return useMemo(() => `parameters[${index}].expression.expression`, [index]);
}

export function findParamDefinitionByName(definitions: UIParameter[], paramName: string): UIParameter {
    return definitions?.find((param) => param.name === paramName);
}
