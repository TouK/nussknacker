import { Box } from "@mui/material";
import React, { useCallback } from "react";
import { useTranslation } from "react-i18next";

import { CopyIconButton, useCopyClipboard } from "../../../common/copyToClipboard";
import { useUserSettings } from "../../../common/userSettings";
import { getIsRunning } from "../../../reducers/selectors/scenarioState";
import { useAppSelector } from "../../../store/storeHelpers";
import { getValidationErrorsForField } from "./editors/Validators";
import { GenerateNewEndpoint } from "./node-action-buttons/GenerateNewEndpoint";
import { SendRequestButton } from "./node-action-buttons/SendRequestButton";
import { ParameterExpressionField } from "./ParameterExpressionField";
import type { ParametersListProps, ParameterWithIndex } from "./parametersList";

interface Props extends ParametersListProps {
    paramWithIndex: ParameterWithIndex;
}

export const ParametersListField = (props: Props) => {
    const { node, getListFieldPath, paramWithIndex, parameterDefinitions, setProperty } = props;
    const handleGetListFieldPath = useCallback(
        (index: number) => {
            return getListFieldPath(index);
        },
        [getListFieldPath],
    );

    const isRunning = useAppSelector(getIsRunning);
    const { t } = useTranslation();
    const [isCopied, copy] = useCopyClipboard();
    const [settings] = useUserSettings();

    if (paramWithIndex.param.name === "Endpoint") {
        const displayCopyValueEndAdornment = paramWithIndex.param.name === "Endpoint" && node.type === "Source";
        const displayGenerateEndpointButton =
            paramWithIndex.param.name === "Endpoint" && node.type === "Source" && settings["node.showGenerateEndpointButton"];

        return (
            <>
                <ParameterExpressionField
                    listFieldPath={handleGetListFieldPath(paramWithIndex.index)}
                    parameter={paramWithIndex.param}
                    endAdornment={
                        displayCopyValueEndAdornment && (
                            <CopyIconButton
                                onClick={() => {
                                    const possibleValues = parameterDefinitions.find(
                                        (parameterDefinition) => parameterDefinition.name === "Endpoint",
                                    ).editors[0].possibleValues;

                                    const selectedValue = possibleValues.find(
                                        (possibleValue) => possibleValue.expression === paramWithIndex.param.expression.expression,
                                    );
                                    copy(selectedValue.label);
                                }}
                                isCopied={isCopied}
                            />
                        )
                    }
                    {...props}
                />
                {/*
                 * TODO: Remove it when the backend is ready and action buttons will be send by default
                 */}
                {displayGenerateEndpointButton && (
                    <Box display={"flex"} justifyContent={"flex-end"}>
                        <GenerateNewEndpoint
                            node={node}
                            handleNewEndpointGenerated={(topic: string) => {
                                const expressionProperty = "expression.expression";
                                const expressionPath = `${getListFieldPath(paramWithIndex.index)}.${expressionProperty}`;

                                setProperty(expressionPath, topic);
                            }}
                        />
                    </Box>
                )}
            </>
        );
    }

    const displaySendRequestButton =
        paramWithIndex.param.name === "Data sample" &&
        node.type === "Source" &&
        node.id === "HTTP Endpoint" &&
        settings["node.showSendRequestButton"];

    return (
        <>
            <ParameterExpressionField
                listFieldPath={handleGetListFieldPath(paramWithIndex.index)}
                parameter={paramWithIndex.param}
                {...props}
            />
            {displaySendRequestButton && (
                <Box display={"flex"} justifyContent={"flex-end"}>
                    <SendRequestButton
                        disabled={getValidationErrorsForField(props.errors, paramWithIndex.param.name).length > 0 || !isRunning}
                        infoTooltip={!isRunning && t("node.actions.sendRequest.tooltip.deployScenarioFirst", "Deploy your scenario first")}
                        expression={paramWithIndex.param.expression.expression}
                        node={node}
                    />
                </Box>
            )}
        </>
    );
};
