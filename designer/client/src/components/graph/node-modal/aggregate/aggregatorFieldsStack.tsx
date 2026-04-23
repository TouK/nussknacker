import { sortBy } from "lodash";
import type { ChangeEvent } from "react";
import React, { useCallback, useMemo } from "react";

import type { VariableTypes } from "../../../../types/validation";
import type { WithUuid } from "../appendUuid";
import { EditableEditor } from "../editors/EditableEditor";
import type { ExpressionObj } from "../editors/expression/types";
import { ExpressionLang } from "../editors/expression/types";
import Input from "../editors/field/Input";
import { EMPTY_REQUIRED_ERROR } from "../editors/Validators";
import { TypeSelect } from "../fragment-input-definition/TypeSelect";
import { useFieldsControl } from "../node-row-fields-provider/FieldsControl";
import type { AggRow } from "./aggregatorField";
import { RowFieldLabel } from "./rowFieldLabel";

export type PossibleValue = {
    expression: string;
    label: string;
};

type AggregatorFieldsStackProps = {
    value: WithUuid<AggRow>;
    onChange: (uuid: string, updated: Partial<AggRow>) => void;
    aggregators: PossibleValue[];
    variableTypes: VariableTypes;
    showLabels?: boolean;
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

export function AggregatorFieldsStack({
    value: { agg, name, uuid, expression },
    onChange,
    aggregators,
    variableTypes,
    showLabels,
    outputVariableName,
}: AggregatorFieldsStackProps) {
    const { readOnly } = useFieldsControl();
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

        return sortBy([...values, ...presets], "label");
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
        ({ expression }: ExpressionObj) => {
            onChange(uuid, { expression });
        },
        [onChange, uuid],
    );

    const fieldErrors = useMemo(() => (expression ? [] : [EMPTY_REQUIRED_ERROR]), [expression]);
    return (
        <>
            <RowFieldLabel
                flexBasis="35%"
                label={`${outputVariableName ? `#${outputVariableName}` : "output variable"} field`}
                showLabel={showLabels}
            >
                <Input
                    onChange={onChangeName}
                    value={name}
                    disabled={readOnly}
                    showValidation
                    fieldErrors={name ? [] : [EMPTY_REQUIRED_ERROR]}
                    autoFocus={!name}
                />
            </RowFieldLabel>
            <RowFieldLabel flexBasis="35%" label="aggregator" showLabel={showLabels}>
                <TypeSelect onChange={onChangeType} value={selectedType} options={options} readOnly={readOnly} />
            </RowFieldLabel>
            <RowFieldLabel flexBasis="70%" label="aggregator input" showLabel={showLabels}>
                {selectedType.preset ? (
                    <Input disabled value="" />
                ) : (
                    <EditableEditor
                        variableTypes={variableTypes}
                        expressionObj={expressionObj}
                        onValueChange={onChangeExpression}
                        readOnly={readOnly}
                        showValidation
                        fieldErrors={fieldErrors}
                        isValidating={false}
                        showSwitch={false}
                    />
                )}
            </RowFieldLabel>
        </>
    );
}
