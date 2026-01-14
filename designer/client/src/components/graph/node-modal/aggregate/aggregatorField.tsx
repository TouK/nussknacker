import { cx } from "@emotion/css";
import { Box } from "@mui/material";
import { get, isEqual } from "lodash";
import React, { useCallback, useContext, useMemo, useState } from "react";

import { useAppSelector } from "../../../../store/storeHelpers";
import { DndItems } from "../../../common/dndItems/DndItems";
import type { WithUuid } from "../appendUuid";
import { withUuid } from "../appendUuid";
import { EditorType } from "../editors/expression/types";
import { FieldsRow } from "../fragment-input-definition/FieldsRow";
import { NodeRowFieldsProvider } from "../node-row-fields-provider/NodeRowFieldsProvider";
import { getFindAvailableVariables } from "../NodeDetailsContent/selectors";
import type { FieldWrapperProps } from "../ParameterExpressionField";
import { findParamDefinitionByName } from "../parameterHelpers";
import { AggregateContext } from "./aggregateContext";
import { AggregatorFieldsStack } from "./aggregatorFieldsStack";

export type AggRow = {
    name: string;
    agg: string;
    expression: string;
};

export type AggregateValue = WithUuid<AggRow>;

export function AggregatorField({ parameterDefinitions, node, isEditMode, showValidation }: FieldWrapperProps) {
    const aggregators = useMemo(() => {
        const { editors } = findParamDefinitionByName(parameterDefinitions, "aggregator");
        // this for is workaround for ts<5 to narrow type
        for (const editor of editors) {
            if (editor.type === EditorType.FIXED_VALUES_PARAMETER_EDITOR) {
                return editor.possibleValues;
            }
        }
        return [];
    }, [parameterDefinitions]);

    const { aggregator } = useContext(AggregateContext);
    const { values, onChange, isMarked, fieldErrors = [] } = aggregator;
    const [data, setData] = useState<AggregateValue[]>(values);

    const setAggregatorData = useCallback(
        (value: ((prevData: AggregateValue[]) => AggregateValue[]) | AggregateValue[]) => {
            setData((prev) => {
                const next = typeof value === "function" ? value(prev) : value;
                if (isEqual(prev, next)) return prev;
                onChange?.(next);
                return next;
            });
        },
        [onChange],
    );

    const onAdd = useCallback(() => {
        setAggregatorData((prevData) => [
            ...prevData,
            withUuid({
                name: "",
                agg: aggregators[0].expression,
                expression: "",
            }),
        ]);
    }, [aggregators, setAggregatorData]);

    const onRemove = useCallback(
        (_: string, uuid: string) => {
            setAggregatorData((prevData) => prevData.filter((item) => item.uuid !== uuid));
        },
        [setAggregatorData],
    );

    const onChangeItem = useCallback(
        (uuid: string, updated: Partial<AggRow>) => {
            setAggregatorData((prevData) => prevData.map((item) => (item.uuid === uuid ? { ...item, ...updated } : item)));
        },
        [setAggregatorData],
    );

    const findAvailableVariables = useAppSelector(getFindAvailableVariables);
    const variableTypes = useMemo(() => findAvailableVariables?.(node.id), [findAvailableVariables, node.id]);

    const errors = showValidation ? fieldErrors : [];

    const [hovered, setHovered] = useState<number | null>(null);
    const outputVariableName = useMemo(() => get(node, "outputVar"), [node]);

    const getFieldsRow = useCallback(
        (item: WithUuid<AggRow>, index: number) => (
            <FieldsRow key={item.uuid} uuid={item.uuid} index={index}>
                <AggregatorFieldsStack
                    value={item}
                    onChange={onChangeItem}
                    aggregators={aggregators}
                    variableTypes={variableTypes}
                    showLabels={hovered == 0}
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
                    onChange={setAggregatorData}
                    onDestinationChange={setHovered}
                />
            </Box>
        </NodeRowFieldsProvider>
    );
}
