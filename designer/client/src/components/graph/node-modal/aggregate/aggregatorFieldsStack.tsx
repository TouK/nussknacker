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

const PRESETS = [
    {
        label: "(Count)",
        value: "@COUNT",
        agg: "#AGG.sum",
        expression: "1",
    },
];

export function AggregatorFieldsStack({
    value: { agg, name, uuid, expression },
    onChange,
    aggregators,
    variableTypes,
    hovered,
    outputVariableName,
}: AggregatorFieldsStackProps) {
    const { readOnly } = useFieldsContext();
    const options = useMemo<{ value: string; label: string; expression?: string }[]>(() => {
        const values = aggregators.map(({ expression: value, label }) => ({
            value,
            label,
        }));
        return [...values, ...PRESETS];
    }, [aggregators]);

    return (
        <>
            <DynamicLabel
                flexBasis="35%"
                label={`${outputVariableName ? `#${outputVariableName}` : "output variable"} field`}
                hovered={hovered}
            >
                <Input
                    onChange={(e) => {
                        onChange(uuid, { name: e.target.value.replaceAll(/["]/g, "") });
                    }}
                    value={name}
                    disabled={readOnly}
                />
            </DynamicLabel>
            <DynamicLabel flexBasis="35%" label="aggregator" hovered={hovered}>
                <TypeSelect
                    onChange={(value) => {
                        const preset = PRESETS.find((p) => p.value === value);
                        if (preset) {
                            return onChange(uuid, preset);
                        }
                        onChange(uuid, { agg: value });
                    }}
                    value={options.find(({ value }) => value === agg)}
                    options={options}
                    readOnly={readOnly}
                />
            </DynamicLabel>
            <DynamicLabel flexBasis="70%" label="aggregator input" hovered={hovered}>
                <EditableEditor
                    variableTypes={variableTypes}
                    expressionObj={{
                        expression,
                        language: ExpressionLang.SpEL,
                    }}
                    onValueChange={(value) => {
                        onChange(uuid, { expression: value });
                    }}
                    readOnly={readOnly}
                />
            </DynamicLabel>
        </>
    );
}
