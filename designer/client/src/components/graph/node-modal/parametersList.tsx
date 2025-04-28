import { Box } from "@mui/material";
import React from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";

import { CopyIconButton, useCopyClipboard } from "../../../common/copyToClipboard";
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

export const ParametersList = ({ parameters = [], getListFieldPath, ...props }: ParametersListProps) => {
    const { node } = props;
    const scenarioState = useSelector(getProcessState);
    const { t } = useTranslation();
    const [isCopied, copy] = useCopyClipboard();

    return (
        <>
            {parameters.map((paramWithIndex) => (
                <React.Fragment key={node.id + paramWithIndex.param.name + paramWithIndex.index}>
                    <ParameterExpressionField
                        listFieldPath={getListFieldPath(paramWithIndex.index)}
                        parameter={paramWithIndex.param}
                        endAdornment={
                            paramWithIndex.param.name === "Endpoint" && (
                                <CopyIconButton
                                    onClick={() => {
                                        const possibleValues = props.parameterDefinitions.find(
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
                    {paramWithIndex.param.name === "Endpoint" && (
                        <Box display={"flex"} justifyContent={"flex-end"}>
                            <GenerateNewEndpoint node={node} />
                        </Box>
                    )}
                    {paramWithIndex.param.name === "Data sample" && (
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
                </React.Fragment>
            ))}
        </>
    );
};
