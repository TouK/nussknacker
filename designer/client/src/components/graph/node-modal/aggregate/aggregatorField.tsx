import { FieldWrapperProps } from "../ParameterExpressionField";
import React, { useCallback, useContext, useEffect, useMemo, useState } from "react";
import { findParamDefinitionByName } from "../parameterHelpers";
import { useArrayState } from "rooks";
import { NodeRowFieldsProvider } from "../node-row-fields-provider";
import { v4 as uuid4 } from "uuid";
import { AggregatorFieldsStack } from "./aggregatorFieldsStack";
import { useSelector } from "react-redux";
import { getFindAvailableVariables } from "../NodeDetailsContent/selectors";
import { FieldsRow } from "../fragment-input-definition/FieldsRow";
import { DndItems } from "../../../common/dndItems/DndItems";
import { cx } from "@emotion/css";

import { AggregateContext } from "./aggregateContext";
import { Box } from "@mui/material";

export type AggRow = {
    name: string;
    agg: string;
    expression: string;
};

export type WithUuid<T extends NonNullable<unknown>> = Omit<T, "uuid"> & {
    uuid: string;
};

export function appendUuid<T extends NonNullable<unknown>>(o: T): WithUuid<T> {
    return { uuid: uuid4(), ...o };
}

export type AggregateValue = WithUuid<AggRow>;

export function AggregatorField({ parameterDefinitions, node: { id }, isEditMode, showValidation }: FieldWrapperProps) {
    const aggregators = useMemo(() => {
        const definition = findParamDefinitionByName(parameterDefinitions, "aggregator");
        return definition.editor.simpleEditor.possibleValues;
    }, [parameterDefinitions]);

    const { values, onChange, isMarked, fieldErrors = [] } = useContext(AggregateContext);
    const [data, dataControls] = useArrayState<AggregateValue>(() => values);

    const onAdd = useCallback(
        (value?: Partial<AggRow>) => {
            dataControls.push(
                appendUuid({
                    name: "",
                    agg: aggregators[0].expression,
                    expression: "",
                    ...value,
                }),
            );
        },
        [aggregators, dataControls],
    );

    const onRemove = useCallback(
        (_: string, uuid: string) => {
            const i = data.findIndex((o) => o.uuid === uuid);
            dataControls.removeItemAtIndex(i);
        },
        [data, dataControls],
    );

    const onChangeItem = useCallback(
        (uuid: string, updated: Partial<AggRow>) => {
            const i = data.findIndex((o) => o.uuid === uuid);
            dataControls.updateItemAtIndex(i, {
                ...data[i],
                ...updated,
            });
        },
        [data, dataControls],
    );

    const findAvailableVariables = useSelector(getFindAvailableVariables);
    const variableTypes = useMemo(() => findAvailableVariables?.(id), [findAvailableVariables, id]);

    const errors = showValidation ? fieldErrors : [];

    const [hovered, setHovered] = useState<number | null>(null);

    const items = useMemo(() => {
        return data.map((item, index) => ({
            item,
            el: (
                <FieldsRow uuid={item.uuid} index={index}>
                    <AggregatorFieldsStack
                        value={item}
                        onChange={onChangeItem}
                        aggregators={aggregators}
                        variableTypes={variableTypes}
                        hovered={hovered === 0}
                    />
                </FieldsRow>
            ),
        }));
    }, [data, onChangeItem, aggregators, variableTypes, hovered]);

    useEffect(() => {
        onChange?.(data);
    }, [data, onChange]);

    return (
        <NodeRowFieldsProvider
            path={null}
            label="aggregator"
            onFieldRemove={data.length > 1 && onRemove}
            onFieldAdd={() => onAdd()}
            readOnly={!isEditMode}
            errors={errors}
        >
            <Box sx={{ paddingTop: 2 }} className={cx({ marked: isMarked })}>
                <DndItems
                    disabled={!isEditMode || data.length <= 1}
                    items={items}
                    onChange={dataControls.setArray}
                    onDestinationChange={setHovered}
                />
            </Box>
        </NodeRowFieldsProvider>
    );
}
