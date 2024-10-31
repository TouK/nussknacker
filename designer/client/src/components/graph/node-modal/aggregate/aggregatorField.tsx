import { cx } from "@emotion/css";
import { Box } from "@mui/material";
import { get } from "lodash";
import React, { useCallback, useContext, useEffect, useMemo, useState } from "react";
import { useSelector } from "react-redux";
import { useArrayState } from "rooks";
import { v4 as uuid4 } from "uuid";
import { DndItems } from "../../../common/dndItems/DndItems";
import { FieldsRow } from "../fragment-input-definition/FieldsRow";
import { NodeRowFieldsProvider } from "../node-row-fields-provider";
import { getFindAvailableVariables } from "../NodeDetailsContent/selectors";
import { FieldWrapperProps } from "../ParameterExpressionField";
import { findParamDefinitionByName } from "../parameterHelpers";

import { AggregateContext } from "./aggregateContext";
import { AggregatorFieldsStack } from "./aggregatorFieldsStack";

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

export function AggregatorField({ parameterDefinitions, node, isEditMode, showValidation }: FieldWrapperProps) {
    const aggregators = useMemo(() => {
        const definition = findParamDefinitionByName(parameterDefinitions, "aggregator");
        return definition.editor.simpleEditor.possibleValues;
    }, [parameterDefinitions]);

    const { aggregator } = useContext(AggregateContext);
    const { values, onChange, isMarked, fieldErrors = [] } = aggregator;
    const [data, dataControls] = useArrayState<AggregateValue>(() => values);

    const onAdd = useCallback(() => {
        dataControls.push(
            appendUuid({
                name: "",
                agg: aggregators[0].expression,
                expression: "",
            }),
        );
    }, [aggregators, dataControls]);

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
    const variableTypes = useMemo(() => findAvailableVariables?.(node.id), [findAvailableVariables, node.id]);

    const errors = showValidation ? fieldErrors : [];

    const [hovered, setHovered] = useState<number | null>(null);
    const outputVariableName = useMemo(() => get(node, "outputVar"), [node]);

    const getFieldsRow = useCallback(
        (item: WithUuid<AggRow>, index: number) => (
            <FieldsRow uuid={item.uuid} index={index}>
                <AggregatorFieldsStack
                    value={item}
                    onChange={onChangeItem}
                    aggregators={aggregators}
                    variableTypes={variableTypes}
                    hovered={hovered === 0}
                    outputVariableName={outputVariableName}
                />
            </FieldsRow>
        ),
        [aggregators, hovered, onChangeItem, outputVariableName, variableTypes],
    );

    const items = useMemo(() => {
        return data.map((item, index) => ({
            item,
            el: getFieldsRow(item, index),
        }));
    }, [data, getFieldsRow]);

    useEffect(() => {
        onChange?.(data);
    }, [data, onChange]);

    return (
        <NodeRowFieldsProvider
            path={null}
            label="Aggregations"
            onFieldRemove={data.length > 1 && onRemove}
            onFieldAdd={onAdd}
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
