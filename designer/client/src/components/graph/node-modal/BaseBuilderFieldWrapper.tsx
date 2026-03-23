import type { ReactElement } from "react";
import React, { useMemo, useState } from "react";

import { useUserSettings } from "../../../common/useUserSettings";
import type { NodeType } from "../../../types/node";
import { ExpressionLang } from "./editors/expression/types";
import { BuilderIconButton } from "./node-action-buttons/StyledLoadingButton";
import type { FieldWrapperProps } from "./ParameterExpressionField";

export function getParamExpression(node: NodeType, paramName: string): string | undefined {
    type ParamList = Array<{ name: string; expression: { expression: string } }>;
    const params: ParamList | undefined =
        node.type === "Sink" || node.type === "Source"
            ? (node.ref as { parameters?: ParamList }).parameters
            : (node.parameters as ParamList | undefined) ??
              (node as unknown as { service?: { parameters?: ParamList } }).service?.parameters;
    return params?.find((p) => p.name === paramName)?.expression?.expression;
}

interface BaseBuilderFieldWrapperProps extends FieldWrapperProps {
    buttonLabel: string;
    renderBuilder: (props: {
        node: NodeType;
        onInsert: (spel: string) => void;
        initialExpression: string | undefined;
        paramName: string;
        paramDef: ReturnType<typeof useMemo<FieldWrapperProps["parameterDefinitions"][number] | undefined>>;
        open: boolean;
        onClose: () => void;
    }) => ReactElement;
}

export function BaseBuilderFieldWrapper({
    children,
    node,
    parameter,
    parameterDefinitions,
    listFieldPath,
    setProperty,
    isEditMode,
    buttonLabel,
    renderBuilder,
}: BaseBuilderFieldWrapperProps) {
    const [showDataMapper] = useUserSettings("node.showFieldExpressionBuilder");
    const [builderOpen, setBuilderOpen] = useState(false);

    const paramDef = useMemo(() => parameterDefinitions?.find((p) => p.name === parameter.name), [parameter.name, parameterDefinitions]);

    const onInsertExpression = useMemo(
        () => (spel: string) => setProperty(`${listFieldPath}.expression`, { expression: spel, language: ExpressionLang.SpEL }),
        [listFieldPath, setProperty],
    );

    if (!showDataMapper || !isEditMode) {
        return <>{children}</>;
    }

    return (
        <>
            {React.cloneElement(children as ReactElement, {
                inputAdornmentEnd: <BuilderIconButton onClick={() => setBuilderOpen(true)} label={buttonLabel} />,
            })}
            {renderBuilder({
                node,
                onInsert: onInsertExpression,
                initialExpression: getParamExpression(node, parameter.name),
                paramName: parameter.name,
                paramDef,
                open: builderOpen,
                onClose: () => setBuilderOpen(false),
            })}
        </>
    );
}
