import React from "react";

import type { Parameter } from "../../../types";
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
    return (
        <>
            {parameters.map((paramWithIndex) => (
                <ParameterExpressionField
                    key={node.id + paramWithIndex.param.name + paramWithIndex.index}
                    listFieldPath={getListFieldPath(paramWithIndex.index)}
                    parameter={paramWithIndex.param}
                    {...props}
                />
            ))}
        </>
    );
};
