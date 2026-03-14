import React, { useMemo } from "react";

import { useUserSettings } from "../../../common/useUserSettings";
import type { NodeType } from "../../../types/node";
import { SpelExpressionPickerComponent } from "../../spelExpressionPicker/SpelExpressionPickerComponent";
import { DataMapperComponent } from "./DataMapperComponent";
import { DataSampleFieldWrapper } from "./dataSampleFieldWrapper";
import { ExpressionLang } from "./editors/expression/types";
import { useParamKey } from "./editors/ParamKeyProvider";
import { getValidationErrorsForField } from "./editors/Validators";
import { CopyEndpoint, EndpointFieldWrapper } from "./endpointFieldWrapper";
import { FieldAddons } from "./fieldAddons";
import { ParameterExpressionField } from "./ParameterExpressionField";
import { OverrideKeys } from "./parameterHelpers";
import type { ParametersListProps, ParameterWithIndex } from "./parametersList";
import { WebSocketUrlFieldWrapper } from "./webSocketUrlFieldWrapper";

function getParamExpression(node: NodeType, paramName: string): string | undefined {
    type ParamList = Array<{ name: string; expression: { expression: string } }>;
    const params: ParamList | undefined =
        node.type === "Sink" || node.type === "Source"
            ? (node.ref as { parameters?: ParamList }).parameters
            : (node.parameters as ParamList | undefined) ??
              (node as unknown as { service?: { parameters?: ParamList } }).service?.parameters;
    return params?.find((p) => p.name === paramName)?.expression?.expression;
}

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

    const { isEditMode, node, setProperty } = props;
    const onInsertExpression = useMemo(
        () => (spel: string) => setProperty(`${listFieldPath}.expression`, { expression: spel, language: ExpressionLang.SpEL }),
        [listFieldPath, setProperty],
    );
    const dataMapperAddon = useMemo(() => {
        if (!isEditMode) return undefined;
        if (paramKey === OverrideKeys.ForEachElements) {
            const paramDef = props.parameterDefinitions?.find((p) => p.name === param.name);
            return (
                <SpelExpressionPickerComponent
                    node={node}
                    onInsert={onInsertExpression}
                    initialExpression={getParamExpression(node, param.name)}
                    paramName={param.name}
                    targetTypeDisplay={paramDef?.typ?.display}
                />
            );
        }
        if (
            (paramKey === OverrideKeys.SinkKafkaValue || paramKey === OverrideKeys.HttpBody || paramKey === OverrideKeys.WebhookBody) &&
            showDataMapper
        )
            return (
                <DataMapperComponent node={node} onInsert={onInsertExpression} initialExpression={getParamExpression(node, param.name)} />
            );
    }, [param.name, paramKey, isEditMode, node, onInsertExpression, props.parameterDefinitions, showDataMapper]);

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
