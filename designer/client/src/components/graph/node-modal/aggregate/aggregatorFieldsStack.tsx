import React, { useMemo } from "react";
import { AggRow, WithUuid } from "./aggregatorField";
import { css } from "@emotion/css";
import Input from "../editors/field/Input";
import { TypeSelect } from "../fragment-input-definition/TypeSelect";
import { EditableEditor } from "../editors/EditableEditor";
import { ExpressionLang } from "../editors/expression/types";
import { VariableTypes } from "../../../../types";
import { useFieldsContext } from "../node-row-fields-provider";

export type PossibleValue = {
    expression: string;
    label: string;
};

type AggregatorFieldsStackProps = {
    value: WithUuid<AggRow>;
    onChange: (uuid: string, updated: Partial<AggRow>) => void;
    aggregators: PossibleValue[];
    variableTypes: VariableTypes;
};

export function AggregatorFieldsStack({
    value: { agg, name, uuid, value },
    onChange,
    aggregators,
    variableTypes,
}: AggregatorFieldsStackProps) {
    const { readOnly } = useFieldsContext();
    const options = useMemo(() => {
        return aggregators.map(({ expression: value, label }) => ({
            value,
            label,
        }));
    }, [aggregators]);
    return (
        <>
            <Input
                placeholder="output variable field"
                onChange={(e) => {
                    onChange(uuid, { name: e.target.value.replaceAll(/["]/g, "") });
                }}
                value={name}
                className={css({
                    flexBasis: "35%",
                })}
                disabled={readOnly}
            />
            <TypeSelect
                onChange={(value) => {
                    onChange(uuid, { agg: value });
                }}
                value={options.find(({ value }) => value === agg)}
                options={options}
                readOnly={readOnly}
            />
            <EditableEditor
                placeholder="source"
                variableTypes={variableTypes}
                expressionObj={{
                    expression: value,
                    language: ExpressionLang.SpEL,
                }}
                onValueChange={(value) => {
                    onChange(uuid, { value });
                }}
                valueClassName={css({
                    flexBasis: "70%",
                })}
                readOnly={readOnly}
            />
        </>
    );
}
