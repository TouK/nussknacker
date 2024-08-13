import { Parameter } from "../../../types";
import { ParameterExpressionField, ParameterExpressionFieldProps } from "./ParameterExpressionField";
import React from "react";

type ParametersListItemProps = Omit<ParameterExpressionFieldProps, "listFieldPath" | "parameter">;

export type ParametersListProps = ParametersListItemProps & {
    parameters: Parameter[];
    getListFieldPath: (index: number) => string;
};

export const ParametersList = ({ parameters = [], getListFieldPath, ...props }: ParametersListProps) => {
    const { node } = props;
    return (
        <>
            {parameters.map((param, index) => (
                <ParameterExpressionField
                    key={node.id + param.name + index}
                    listFieldPath={getListFieldPath(index)}
                    parameter={param}
                    {...props}
                />
            ))}
        </>
    );
};
