import { Box } from "@mui/material";
import React from "react";

import type { Parameter } from "../../../types";
import { getValidationErrorsForField } from "./editors/Validators";
import { GenerateNewEndpoint } from "./GenerateNewEndpoint";
import type { ParameterExpressionFieldProps } from "./ParameterExpressionField";
import { ParameterExpressionField } from "./ParameterExpressionField";
import { SendRequestButton } from "./SendRequestButton";

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

    return (
        <>
            {parameters.map((paramWithIndex) => (
                <>
                    <ParameterExpressionField
                        key={node.id + paramWithIndex.param.name + paramWithIndex.index}
                        listFieldPath={getListFieldPath(paramWithIndex.index)}
                        parameter={paramWithIndex.param}
                        {...props}
                    />
                    {paramWithIndex.param.name === "Endpoint" && (
                        <Box display={"flex"} justifyContent={"flex-end"}>
                            <GenerateNewEndpoint nodeId={props.node.id} />
                        </Box>
                    )}
                    {paramWithIndex.param.name === "Data sample" && (
                        <Box display={"flex"} justifyContent={"flex-end"}>
                            <SendRequestButton
                                disabled={getValidationErrorsForField(props.errors, paramWithIndex.param.name).length > 0}
                                expression={paramWithIndex.param.expression.expression}
                                nodeId={node.id}
                            />
                        </Box>
                    )}
                </>
            ))}
        </>
    );
};
