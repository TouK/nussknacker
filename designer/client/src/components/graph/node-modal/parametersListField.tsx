import React, { useMemo } from "react";

import type { NodeType, Parameter } from "../../../types/node";
import { DataSampleFieldWrapper } from "./dataSampleFieldWrapper";
import { CopyEndpoint, EndpointFieldWrapper } from "./endpointFieldWrapper";
import { ParameterExpressionField } from "./ParameterExpressionField";
import type { ParametersListProps, ParameterWithIndex } from "./parametersList";

export type ParametersListFieldProps = ParametersListProps & {
    paramWithIndex: ParameterWithIndex;
};

function isSourceEndpoint(node: NodeType, param: Parameter) {
    return node.type === "Source" && param.name === "Endpoint";
}

function isSourceDataSample(node: NodeType, param: Parameter) {
    return node.type === "Source" && param.name === "Data sample";
}

export const ParametersListField = ({ getListFieldPath, paramWithIndex, ...props }: ParametersListFieldProps) => {
    const { node } = props;
    const { index, param } = paramWithIndex;

    const listFieldPath = useMemo(() => getListFieldPath(index), [getListFieldPath, index]);

    const FieldWrapper = useMemo(() => {
        if (isSourceEndpoint(node, param)) return EndpointFieldWrapper;
        if (isSourceDataSample(node, param)) return DataSampleFieldWrapper;
    }, [node, param]);

    const endAdornment = useMemo(() => {
        if (isSourceEndpoint(node, param)) return <CopyEndpoint parameter={param} parameterDefinitions={props.parameterDefinitions} />;
    }, [node, param, props.parameterDefinitions]);

    return (
        <ParameterExpressionField
            listFieldPath={listFieldPath}
            parameter={param}
            FieldWrapper={FieldWrapper}
            {...props}
            endAdornment={endAdornment}
        />
    );
};
