import { Box } from "@mui/material";
import React from "react";
import { useSelector } from "react-redux";

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

    return (
        <>
            {parameters.map((paramWithIndex) => (
                <React.Fragment key={node.id + paramWithIndex.param.name + paramWithIndex.index}>
                    <ParameterExpressionField
                        listFieldPath={getListFieldPath(paramWithIndex.index)}
                        parameter={paramWithIndex.param}
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
