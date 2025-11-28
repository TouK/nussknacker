import React, { useCallback, useMemo } from "react";

import { CopyIconButton } from "../../../common/copyToClipboard/CopyIconButton";
import { useCopyClipboard } from "../../../common/copyToClipboard/useCopyToClipboard";
import { getUserSettings } from "../../../reducers/selectors/userSettings";
import { useAppSelector } from "../../../store/storeHelpers";
import { EditorType } from "./editors/expression/types";
import { FieldAddons } from "./fieldAddons";
import { GenerateNewEndpoint } from "./node-action-buttons/GenerateNewEndpoint";
import type { FieldWrapperProps, ParameterExpressionFieldProps } from "./ParameterExpressionField";

export function CopyEndpoint({
    parameter,
    parameterDefinitions,
}: Pick<ParameterExpressionFieldProps, "parameter" | "parameterDefinitions">) {
    const [isCopied, copy] = useCopyClipboard();

    const parameterDefinition = useMemo(() => parameterDefinitions.find(({ name }) => name === "Endpoint"), [parameterDefinitions]);
    const expression = parameter.expression.expression;

    const onClick = useCallback(() => {
        // this for is workaround for ts<5 to narrow type
        for (const editor of parameterDefinition.editors) {
            if (editor.type === EditorType.FIXED_VALUES_PARAMETER_EDITOR) {
                const selectedValue = editor.possibleValues.find((v) => v.expression === expression);
                copy(selectedValue.label);
                return;
            }
        }
    }, [parameterDefinition.editors, copy, expression]);

    return <CopyIconButton onClick={onClick} isCopied={isCopied} />;
}

export function EndpointFieldWrapper({
    children,
    node,
    listFieldPath,
    setProperty,
    isEditMode,
    showValidation,
    fieldErrors,
}: FieldWrapperProps) {
    const settings = useAppSelector(getUserSettings);

    if (!isEditMode) return <>{children}</>;
    if (!settings["node.showGenerateEndpointButton"]) return <>{children}</>;

    return (
        <>
            {children}
            <FieldAddons hasError={showValidation && fieldErrors.length > 0}>
                <GenerateNewEndpoint
                    node={node}
                    handleNewEndpointGenerated={(topic: string) => {
                        const expressionProperty = "expression.expression";
                        const expressionPath = `${listFieldPath}.${expressionProperty}`;

                        setProperty(expressionPath, topic);
                    }}
                />
            </FieldAddons>
        </>
    );
}
