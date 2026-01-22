import { useMemo } from "react";

import { determineComponentId } from "../../../common/componentUtils";
import type { UIParameter } from "../../../types/definition";
import type { NodeType, Parameter } from "../../../types/node";

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

export const OverrideKeys = {
    SourceEndpoint: "source-webhook/Endpoint",
    SourceDataSample: "source-webhook/Data sample",
    DecisionTableMatch: "service-decision-table/Match condition",
    AggregateEndSession: "custom-aggregate-session/endSessionCondition",
    HttpQueryParameters: "service-http/Query Parameters",
    HttpHeaders: "service-http/Headers",
} as const;

export type ParamKeys = (typeof OverrideKeys)[keyof typeof OverrideKeys] | (string & NonNullable<unknown>);

export function determineParameterKey(node: NodeType, param: Parameter | UIParameter): ParamKeys {
    const componentId = determineComponentId(node);
    const paramName = param?.name;
    return componentId && paramName ? `${componentId}/${paramName}` : null;
}
