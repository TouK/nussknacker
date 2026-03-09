import React, { useMemo } from "react";

import { useUserSettings } from "../../../common/useUserSettings";
import { DataSampleFieldWrapper } from "./dataSampleFieldWrapper";
import { useParamKey } from "./editors/ParamKeyProvider";
import { getValidationErrorsForField } from "./editors/Validators";
import { CopyEndpoint, EndpointFieldWrapper } from "./endpointFieldWrapper";
import { FieldAddons } from "./fieldAddons";
import { HttpBodyDataMapper } from "./HttpBodyDataMapper";
import { ParameterExpressionField } from "./ParameterExpressionField";
import { OverrideKeys } from "./parameterHelpers";
import type { ParametersListProps, ParameterWithIndex } from "./parametersList";
import { SinkKafkaValueDataMapper } from "./SinkKafkaValueDataMapper";
import { WebSocketUrlFieldWrapper } from "./webSocketUrlFieldWrapper";

export type ParametersListFieldProps = ParametersListProps & {
    paramWithIndex: ParameterWithIndex;
};

export const ParametersListField = ({ getListFieldPath, paramWithIndex, ...props }: ParametersListFieldProps) => {
    const paramKey = useParamKey();
    const { index, param } = paramWithIndex;
    const [showDataMapper] = useUserSettings("node.showDataMapper");

    const listFieldPath = useMemo(() => getListFieldPath(index), [getListFieldPath, index]);

    const FieldWrapper = useMemo(() => {
        if (paramKey === OverrideKeys.SourceEndpoint) return EndpointFieldWrapper;
        if (paramKey === OverrideKeys.SourceDataSample) return DataSampleFieldWrapper;
        if (paramKey === OverrideKeys.WebSocketUrl) return WebSocketUrlFieldWrapper;
    }, [paramKey]);

    const endAdornment = useMemo(() => {
        if (paramKey === OverrideKeys.SourceEndpoint)
            return <CopyEndpoint parameter={param} parameterDefinitions={props.parameterDefinitions} />;
    }, [param, paramKey, props.parameterDefinitions]);

    const dataMapperAddon = useMemo(() => {
        if (paramKey === OverrideKeys.SinkKafkaValue && props.isEditMode && showDataMapper)
            return (
                <SinkKafkaValueDataMapper
                    node={props.node}
                    parameterDefinitions={props.parameterDefinitions}
                    valuePath={`${listFieldPath}.expression`}
                    setProperty={props.setProperty}
                />
            );
        if ((paramKey === OverrideKeys.HttpBody || paramKey === OverrideKeys.WebhookBody) && props.isEditMode && showDataMapper)
            return <HttpBodyDataMapper node={props.node} valuePath={`${listFieldPath}.expression`} setProperty={props.setProperty} />;
    }, [listFieldPath, paramKey, props.isEditMode, props.node, props.parameterDefinitions, props.setProperty, showDataMapper]);

    const fieldErrors = useMemo(() => getValidationErrorsForField(props.errors, param.name), [props.errors, param.name]);

    return (
        <>
            <ParameterExpressionField
                listFieldPath={listFieldPath}
                parameter={param}
                FieldWrapper={FieldWrapper}
                {...props}
                endAdornment={endAdornment}
            />
            {dataMapperAddon && <FieldAddons hasError={props.showValidation && fieldErrors.length > 0}>{dataMapperAddon}</FieldAddons>}
        </>
    );
};
