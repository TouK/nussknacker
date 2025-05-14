import { Box, Skeleton } from "@mui/material";
import React, { Fragment, useCallback } from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";

import { CopyIconButton, useCopyClipboard } from "../../../common/copyToClipboard";
import { useUserSettings } from "../../../common/userSettings";
import { getProcessState } from "../../../reducers/selectors/scenarioState";
import type { Parameter } from "../../../types";
import { getValidationErrorsForField } from "./editors/Validators";
import { GenerateNewEndpoint } from "./node-action-buttons/GenerateNewEndpoint";
import { SendRequestButton } from "./node-action-buttons/SendRequestButton";
import type { ParameterExpressionFieldProps } from "./ParameterExpressionField";
import { ParameterExpressionField } from "./ParameterExpressionField";

type ParametersListItemProps = Omit<ParameterExpressionFieldProps, "listFieldPath" | "parameter">;

export type ParameterWithIndex = {
    index: number;
    param: Parameter;
};

export type ParametersListProps = ParametersListItemProps & {
    parameters: ParameterWithIndex[];
    getListFieldPath: (index: number) => string;
};

export const ParametersList = (ppp: ParametersListProps) => {
    const { parameters = [], getListFieldPath, ...props } = ppp;
    const { node } = props;
    const isDynamicParametersLoading = node.isLoading;

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

    return (
        <>
            {parameters.map((paramWithIndex) => (
                <Fragment key={node.id + paramWithIndex.param.name + paramWithIndex.index}>
                    {paramWithIndex.param.name === "Endpoint" ? (
                        <>
                            <ParameterExpressionField
                                listFieldPath={handleGetListFieldPath(paramWithIndex.index)}
                                parameter={paramWithIndex.param}
                                endAdornment={
                                    paramWithIndex.param.name === "Endpoint" && (
                                        <CopyIconButton
                                            onClick={() => {
                                                const possibleValues = props.parameterDefinitions.find(
                                                    (parameterDefinition) => parameterDefinition.name === "Endpoint",
                                                ).editors[0].possibleValues;

                                                const selectedValue = possibleValues.find(
                                                    (possibleValue) =>
                                                        possibleValue.expression === paramWithIndex.param.expression.expression,
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

                                            props.setProperty(expressionPath, topic);
                                        }}
                                    />
                                </Box>
                            )}
                        </>
                    ) : (
                        <>
                            {isDynamicParametersLoading ? (
                                <Box display={"flex"} justifyContent={"space-between"}>
                                    <Skeleton variant="rectangular" height={15} width={"100%"} sx={{ flexBasis: "10%", mt: "9px" }} />
                                    <Skeleton variant="rectangular" height={35} width={"100%"} sx={{ mb: 2, flexBasis: "80%" }} />
                                </Box>
                            ) : (
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
                            )}
                        </>
                    )}
                </Fragment>
            ))}
        </>
    );
};
