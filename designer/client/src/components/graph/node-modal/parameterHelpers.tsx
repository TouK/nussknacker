import { Parameter, UIParameter } from "../../../types";
import { useMemo } from "react";

export function useParameter(parameters: Parameter[], paramName: string): [Parameter, string] {
    const index = useMemo(() => parameters?.findIndex((param) => param.name === paramName), [paramName, parameters]);
    const parameter = useMemo(() => parameters[index], [index, parameters]);
    const path = useMemo(() => `parameters[${index}].expression.expression`, [index]);

    return [parameter, path];
}

export function findParamDefinitionByName(definitions: UIParameter[], paramName: string): UIParameter {
    return definitions?.find((param) => param.name === paramName);
}
