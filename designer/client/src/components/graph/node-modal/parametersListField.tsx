import React, { useMemo, useState } from "react";

import { useUserSettings } from "../../../common/useUserSettings";
import type { NodeType } from "../../../types/node";
import { typingResultToSample } from "../../builderComponents/typeUtils";
import { fieldsFromSample } from "../../dataMapper/dataMapperUtils";
import { SpelExpressionPickerComponent } from "../../spelExpressionPicker/SpelExpressionPickerComponent";
import { DataMapperComponent } from "./DataMapperComponent";
import { DataSampleFieldWrapper } from "./dataSampleFieldWrapper";
import { ExpressionLang } from "./editors/expression/types";
import { useParamKey } from "./editors/ParamKeyProvider";
import { CopyEndpoint, EndpointFieldWrapper } from "./endpointFieldWrapper";
import { BuilderIconButton } from "./node-action-buttons/StyledLoadingButton";
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
    const [showDataMapper] = useUserSettings("node.showFieldExpressionBuilder");
    const [builderOpen, setBuilderOpen] = useState(false);

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

    const hasBuilder = useMemo(() => {
        if (!isEditMode) return false;
        if (paramKey === OverrideKeys.ForEachElements && showDataMapper) return true;
        if (
            (paramKey === OverrideKeys.SinkKafkaValue ||
                paramKey === OverrideKeys.HttpBody ||
                paramKey === OverrideKeys.WebhookBody ||
                paramKey === OverrideKeys.SourceEventGeneratorValue) &&
            showDataMapper
        )
            return true;
        return false;
    }, [isEditMode, paramKey, showDataMapper]);

    const builderLabel = paramKey === OverrideKeys.ForEachElements ? "Expression Picker" : "Data Mapper";

    const inputAdornmentEnd = useMemo(
        () => (hasBuilder ? <BuilderIconButton onClick={() => setBuilderOpen(true)} label={builderLabel} /> : undefined),
        [hasBuilder, builderLabel],
    );

    const paramDef = useMemo(
        () => props.parameterDefinitions?.find((p) => p.name === param.name),
        [param.name, props.parameterDefinitions],
    );

    return (
        <>
            <ParameterExpressionField
                listFieldPath={listFieldPath}
                parameter={param}
                FieldWrapper={FieldWrapper}
                {...props}
                endAdornment={endAdornment}
                inputAdornmentEnd={inputAdornmentEnd}
            />
            {paramKey === OverrideKeys.ForEachElements && isEditMode && (
                <SpelExpressionPickerComponent
                    node={node}
                    onInsert={onInsertExpression}
                    initialExpression={getParamExpression(node, param.name)}
                    paramName={param.name}
                    targetTypeDisplay={paramDef?.typ?.display}
                    open={builderOpen}
                    onClose={() => setBuilderOpen(false)}
                />
            )}
            {(paramKey === OverrideKeys.SinkKafkaValue ||
                paramKey === OverrideKeys.HttpBody ||
                paramKey === OverrideKeys.WebhookBody ||
                paramKey === OverrideKeys.SourceEventGeneratorValue) &&
                isEditMode && (
                    <DataMapperComponent
                        node={node}
                        onInsert={onInsertExpression}
                        initialExpression={getParamExpression(node, param.name)}
                        initialFields={(() => {
                            const fields = paramDef?.typ ? fieldsFromSample(typingResultToSample(paramDef.typ)) : undefined;
                            return fields?.length ? fields : undefined;
                        })()}
                        open={builderOpen}
                        onClose={() => setBuilderOpen(false)}
                    />
                )}
        </>
    );
};
