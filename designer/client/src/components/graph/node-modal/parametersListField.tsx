import { Box } from "@mui/material";
import React, { useCallback } from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";

import { CopyIconButton, useCopyClipboard } from "../../../common/copyToClipboard";
import { useUserSettings } from "../../../common/userSettings";
import { getProcessState } from "../../../reducers/selectors/scenarioState";
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

    const scenarioState = useSelector(getProcessState);
    const { t } = useTranslation();
    const [isCopied, copy] = useCopyClipboard();
    const [settings] = useUserSettings();

    if (paramWithIndex.param.name === "Endpoint") {
        return (
            <>
                <ParameterExpressionField
                    listFieldPath={handleGetListFieldPath(paramWithIndex.index)}
                    parameter={paramWithIndex.param}
                    endAdornment={
                        paramWithIndex.param.name === "Endpoint" && (
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
                {settings["node.showGenerateEndpointButton"] && (
                    <Box display={"flex"} justifyContent={"flex-end"}>
                        <GenerateNewEndpoint
                            node={node}
                            handleNewEndpointGenerated={(topic: string) => {
                                const expressionProperty = "expression.expression";
                                const expressionPath = `${getListFieldPath(paramWithIndex.index)}${expressionProperty}`;

                                setProperty(expressionPath, topic);
                            }}
                        />
                    </Box>
                )}
            </>
        );
    }

    return (
        <>
            <ParameterExpressionField
                listFieldPath={handleGetListFieldPath(paramWithIndex.index)}
                parameter={paramWithIndex.param}
                {...props}
            />
            {paramWithIndex.param.name === "Data sample" && settings["node.showSendRequestButton"] && (
                <Box display={"flex"} justifyContent={"flex-end"}>
                    <SendRequestButton
                        disabled={
                            getValidationErrorsForField(props.errors, paramWithIndex.param.name).length > 0 ||
                            scenarioState.status.name !== "RUNNING"
                        }
                        infoTooltip={
                            scenarioState.status.name !== "RUNNING" &&
                            t("node.actions.sendRequest.tooltip.deployScenarioFirst", "Deploy your scenario first")
                        }
                        expression={paramWithIndex.param.expression.expression}
                        node={node}
                    />
                </Box>
            )}
        </>
    );
};
