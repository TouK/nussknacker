import React, { useMemo } from "react";

import { ConditionBuilderFieldWrapper } from "./conditionBuilderFieldWrapper";
import { DataMapperFieldWrapper } from "./dataMapperFieldWrapper";
import { DataSampleFieldWrapper } from "./dataSampleFieldWrapper";
import { useParamKey } from "./editors/ParamKeyProvider";
import { CopyEndpoint, EndpointFieldWrapper } from "./endpointFieldWrapper";
import { NamedParamsMapperFieldWrapper } from "./namedParamsMapperFieldWrapper";
import { ParameterExpressionField } from "./ParameterExpressionField";
import type { ParamKeys } from "./parameterHelpers";
import { OverrideKeys } from "./parameterHelpers";
import type { ParametersListProps, ParameterWithIndex } from "./parametersList";
import { SpelExpressionBuilderFieldWrapper } from "./spelExpressionBuilderFieldWrapper";
import { WebSocketUrlFieldWrapper } from "./webSocketUrlFieldWrapper";

export type ParametersListFieldProps = ParametersListProps & {
    paramWithIndex: ParameterWithIndex;
};

function pickFieldWrapper(paramKey: ParamKeys) {
    switch (paramKey) {
        case OverrideKeys.SourceEndpoint:
            return EndpointFieldWrapper;
        case OverrideKeys.SourceDataSample:
            return DataSampleFieldWrapper;
        case OverrideKeys.WebSocketUrl:
            return WebSocketUrlFieldWrapper;
        case OverrideKeys.DecisionTableMatch:
            return ConditionBuilderFieldWrapper;
        case OverrideKeys.ForEachElements:
            return SpelExpressionBuilderFieldWrapper;
        case OverrideKeys.SinkKafkaValue:
        case OverrideKeys.SinkKafkaValue2:
        case OverrideKeys.HttpBody:
        case OverrideKeys.WebhookBody:
        case OverrideKeys.SourceEventGeneratorValue:
            return DataMapperFieldWrapper;
    }
}

export const ParametersListField = ({ getListFieldPath, paramWithIndex, ...props }: ParametersListFieldProps) => {
    const paramKey = useParamKey();
    const { index, param } = paramWithIndex;

    const listFieldPath = useMemo(() => getListFieldPath(index), [getListFieldPath, index]);

    const isKafkaValueParam = useMemo(
        () =>
            param.name === "Value" &&
            props.parameterDefinitions?.some((p) => p.name === "Raw editor") &&
            props.parameterDefinitions?.some((p) => p.name === "Schema version"),
        [param.name, props.parameterDefinitions],
    );
    const FieldWrapper = useMemo(
        () => pickFieldWrapper(paramKey) || (isKafkaValueParam ? DataMapperFieldWrapper : NamedParamsMapperFieldWrapper),
        [isKafkaValueParam, paramKey],
    );

    const endAdornment = useMemo(() => {
        switch (paramKey) {
            case OverrideKeys.SourceEndpoint:
                return <CopyEndpoint parameter={param} parameterDefinitions={props.parameterDefinitions} />;
        }
    }, [param, paramKey, props.parameterDefinitions]);

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
