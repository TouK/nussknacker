import React, { ChangeEvent, useCallback, useMemo } from "react";
import { VariableTypes } from "../../../../types";
import { EditableEditor } from "../editors/EditableEditor";
import { ExpressionLang, ExpressionObj } from "../editors/expression/types";
import Input from "../editors/field/Input";
import { FieldError } from "../editors/Validators";
import { TypeSelect } from "../fragment-input-definition/TypeSelect";
import { useFieldsContext } from "../node-row-fields-provider";
import { AggRow, WithUuid } from "./aggregatorField";
import { DynamicLabel } from "./dynamicLabel";

export type PossibleValue = {
    expression: string;
    label: string;
};

type AggregatorFieldsStackProps = {
    value: WithUuid<AggRow>;
    onChange: (uuid: string, updated: Partial<AggRow>) => void;
    aggregators: PossibleValue[];
    variableTypes: VariableTypes;
    hovered?: boolean;
    outputVariableName?: string;
};

type Preset = {
    label: string;
    agg: string;
    expression: string;
};

const PRESETS: Preset[] = [
    {
        label: "Count",
        agg: "#AGG.sum",
        expression: "1",
    },
];

type TypeOption = {
    value: string;
    label: string;
    preset?: Preset;
};

// use existing method to display only red border without any message
const EMPTY_REQUIRED_ERROR: FieldError = {
    message: "",
    description: "",
};

export function AggregatorFieldsStack({
    value: { agg, name, uuid, expression },
    onChange,
    aggregators,
    variableTypes,
    hovered,
    outputVariableName,
}: AggregatorFieldsStackProps) {
    const { readOnly } = useFieldsContext();
    const options = useMemo<TypeOption[]>(() => {
        const values = aggregators.map(({ expression: value, label }) => ({
            value,
            label,
        }));
        const presets = PRESETS.map((preset) => ({
            value: preset.label,
            label: preset.label,
            preset,
        }));

        return [...values, ...presets];
    }, [aggregators]);

    const onChangeName = useCallback(
        ({ target }: ChangeEvent<HTMLInputElement>) => {
            onChange(uuid, { name: target.value.replaceAll(/["]/g, "") });
        },
        [onChange, uuid],
    );

    const selectedType = useMemo<TypeOption>(() => {
        return (
            options.find(({ preset }) => preset && preset.agg === agg && preset.expression === expression) ||
            options.find(({ value }) => value === agg)
        );
    }, [options, agg, expression]);

    const onChangeType = useCallback(
        (value: string) => {
            const option = options.find((o) => o.value === value);
            if (option.preset) {
                return onChange(uuid, {
                    agg: option.preset.agg,
                    expression: option.preset.expression,
                });
            }
            if (selectedType.preset) {
                return onChange(uuid, {
                    agg: option.value,
                    expression: "",
                });
            }
            return onChange(uuid, { agg: value });
        },
        [onChange, options, selectedType.preset, uuid],
    );

    const expressionObj: ExpressionObj = useMemo(
        () => ({
            expression,
            language: ExpressionLang.SpEL,
        }),
        [expression],
    );

    const onChangeExpression = useCallback(
        (value: string) => {
            onChange(uuid, { expression: value });
        },
        [onChange, uuid],
    );

    return (
        <>
            <DynamicLabel
                flexBasis="35%"
                label={`${outputVariableName ? `#${outputVariableName}` : "output variable"} field`}
                hovered={hovered}
            >
                <Input
                    onChange={onChangeName}
                    value={name}
                    disabled={readOnly}
                    showValidation
                    fieldErrors={name ? [] : [EMPTY_REQUIRED_ERROR]}
                    autoFocus={!name}
                />
            </DynamicLabel>
            <DynamicLabel flexBasis="35%" label="aggregator" hovered={hovered}>
                <TypeSelect onChange={onChangeType} value={selectedType} options={options} readOnly={readOnly} />
            </DynamicLabel>
            <DynamicLabel flexBasis="70%" label="aggregator input" hovered={hovered}>
                {selectedType.preset ? (
                    <Input disabled value="" />
                ) : (
                    <EditableEditor
                        variableTypes={variableTypes}
                        expressionObj={expressionObj}
                        onValueChange={onChangeExpression}
                        readOnly={readOnly}
                        showValidation
                        fieldErrors={expression ? [] : [EMPTY_REQUIRED_ERROR]}
                    />
                )}
            </DynamicLabel>
        </>
    );
}
