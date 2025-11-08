import { Box } from "@mui/material";
import React, { useCallback, useMemo } from "react";

import { CopyIconButton } from "../../../common/copyToClipboard/CopyIconButton";
import { useCopyClipboard } from "../../../common/copyToClipboard/useCopyToClipboard";
import { getUserSettings } from "../../../reducers/selectors/userSettings";
import { useAppSelector } from "../../../store/storeHelpers";
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
        const possibleValues = parameterDefinition.editors[0].possibleValues;
        const selectedValue = possibleValues.find((v) => v.expression === expression);
        copy(selectedValue.label);
    }, [parameterDefinition.editors, copy, expression]);

    return <CopyIconButton onClick={onClick} isCopied={isCopied} />;
}

export function EndpointFieldWrapper({ children, node, listFieldPath, setProperty }: FieldWrapperProps) {
    const settings = useAppSelector(getUserSettings);

    if (!settings["node.showGenerateEndpointButton"]) return <>{children}</>;

    return (
        <>
            {children}
            {
                <Box display={"flex"} justifyContent={"flex-end"}>
                    <GenerateNewEndpoint
                        node={node}
                        handleNewEndpointGenerated={(topic: string) => {
                            const expressionProperty = "expression.expression";
                            const expressionPath = `${listFieldPath}.${expressionProperty}`;

                            setProperty(expressionPath, topic);
                        }}
                    />
                </Box>
            }
        </>
    );
}
