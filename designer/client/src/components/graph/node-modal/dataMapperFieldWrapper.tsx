import React, { useMemo } from "react";

import { typingResultToSample } from "../../builderComponents/typeUtils";
import { fieldsFromSample } from "../../dataMapper/dataMapperUtils";
import { BaseBuilderFieldWrapper } from "./BaseBuilderFieldWrapper";
import { DataMapperComponent } from "./DataMapperComponent";
import type { FieldWrapperProps } from "./ParameterExpressionField";

export function DataMapperFieldWrapper({ parameter, parameterDefinitions, ...restProps }: FieldWrapperProps) {
    const initialFields = useMemo(() => {
        const paramDef = parameterDefinitions?.find((p) => p.name === parameter.name);
        const fields = paramDef?.typ ? fieldsFromSample(typingResultToSample(paramDef.typ)) : undefined;
        if (fields?.length) return fields;
        // Fallback: derive from individual field params (avro/json schema dynamic mode)
        const KAFKA_SYSTEM_PARAMS = new Set([
            "Topic",
            "Schema version",
            "Key",
            "Raw editor",
            "Content type",
            "Value validation mode",
            parameter.name,
        ]);
        const fieldParams = parameterDefinitions?.filter((p) => !KAFKA_SYSTEM_PARAMS.has(p.name));
        if (fieldParams?.length) {
            const schema = Object.fromEntries(fieldParams.map((p) => [p.name, typingResultToSample(p.typ)]));
            const derived = fieldsFromSample(schema);
            return derived.length ? derived : undefined;
        }
        return undefined;
    }, [parameterDefinitions, parameter.name]);

    return (
        <BaseBuilderFieldWrapper
            parameter={parameter}
            parameterDefinitions={parameterDefinitions}
            {...restProps}
            buttonLabel="Data Mapper"
            renderBuilder={({ node, onInsert, initialExpression, open, onClose }) => (
                <DataMapperComponent
                    node={node}
                    onInsert={onInsert}
                    initialExpression={initialExpression}
                    initialFields={initialFields}
                    open={open}
                    onClose={onClose}
                />
            )}
        />
    );
}
