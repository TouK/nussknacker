import React from "react";

import type { NodeType } from "../../../types/node";
import { DataMapperComponent } from "./DataMapperComponent";
import { ExpressionLang } from "./editors/expression/types";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

interface Props {
    node: NodeType;
    paramName: string;
    valuePath: string;
    setProperty: SetProperty;
}

function getParamExpression(node: NodeType, paramName: string): string | undefined {
    const params =
        node.type === "Sink" || node.type === "Source"
            ? (node.ref as { parameters?: Array<{ name: string; expression: { expression: string } }> }).parameters
            : (node as unknown as { service?: { parameters?: Array<{ name: string; expression: { expression: string } }> } }).service
                  ?.parameters;
    return params?.find((p) => p.name === paramName)?.expression?.expression;
}

export function ParameterDataMapper({ node, paramName, valuePath, setProperty }: Props): React.JSX.Element | null {
    return (
        <DataMapperComponent
            node={node}
            onInsert={(spel) => setProperty(valuePath, { expression: spel, language: ExpressionLang.SpEL })}
            initialExpression={getParamExpression(node, paramName)}
        />
    );
}
