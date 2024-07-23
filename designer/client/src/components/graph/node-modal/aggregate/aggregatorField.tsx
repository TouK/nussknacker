import { FieldWrapperProps } from "../ParameterExpressionField";
import React, { useCallback, useEffect, useMemo } from "react";
import { AggMapLikeLexer, AggMapLikeParser } from "./aggMapLikeParser";
import { findParamDefinitionByName, useParameter } from "../parameterHelpers";
import { useArrayState } from "rooks";
import { NodeRowFieldsProvider } from "../node-row-fields-provider";
import { v4 as uuid4 } from "uuid";
import { AggregatorFieldsStack } from "./aggregatorFieldsStack";
import { useSelector } from "react-redux";
import { getFindAvailableVariables } from "../NodeDetailsContent/selectors";
import { padStart } from "lodash";
import { FieldsRow } from "../fragment-input-definition/FieldsRow";
import { DndItems } from "../../../common/dndItems/DndItems";
import { useDiffMark } from "../PathsToMark";
import { cx } from "@emotion/css";

export type AggRow = {
    name: string;
    agg: string;
    value: string;
};

export type WithUuid<T extends NonNullable<unknown>> = Omit<T, "uuid"> & {
    uuid: string;
};

export function appendUuid<T extends NonNullable<unknown>>(o: T): WithUuid<T> {
    return { uuid: uuid4(), ...o };
}

export function AggregatorField({ parameterDefinitions, node: { id, parameters }, setProperty, ...props }: FieldWrapperProps) {
    const parser = useMemo(() => new AggMapLikeParser(), []);

    const deserialize = useCallback(
        (text: string): Record<string, string> => {
            const lexingResult = AggMapLikeLexer.tokenize(text);
            parser.input = lexingResult.tokens;
            return parser.object() || null;
        },
        [parser],
    );

    const serialize = useCallback((paramName: string, map: Record<string, string>): string => {
        const entries = Object.entries(map || {}).map(([key, value]) => {
            const trimmedKey = key.trim();
            return [/^[^a-zA-Z]|\W/.test(trimmedKey) ? `"${trimmedKey}"` : trimmedKey, value];
        });

        const keyLength = entries.reduce((value, [key]) => Math.max(value, key.length), 0);
        const content = entries.map(([key, value]) => `  ${padStart(key, keyLength, " ")}: ${value}`).join(",\n");

        switch (paramName) {
            case "aggregator":
                return `#AGG.map({\n${content}\n})`;
            case "aggregateBy":
                return `{\n${content}\n}`;
        }
    }, []);

    const aggregators = useMemo(() => {
        const definition = findParamDefinitionByName(parameterDefinitions, "aggregator");
        return definition.editor.simpleEditor.possibleValues;
    }, [parameterDefinitions]);

    const [aggregator, aggregatorPath] = useParameter(parameters, "aggregator");
    const [aggregateBy, aggregateByPath] = useParameter(parameters, "aggregateBy");

    const [diffMark] = useDiffMark();

    const isMarked = useMemo(() => diffMark(aggregatorPath) || diffMark(aggregateByPath), [aggregateByPath, aggregatorPath, diffMark]);
    const errors = useMemo(() => props.errors.filter(({ fieldName }) => ["aggregator", "aggregateBy"].includes(fieldName)), [props.errors]);

    const parsed = useMemo(() => {
        const aggregatorParam = deserialize(aggregator?.expression.expression);
        const aggregateByParam = deserialize(aggregateBy?.expression.expression);

        const keys = Object.keys({ ...aggregateByParam, ...aggregatorParam });

        return keys
            .map<AggRow>((name) => ({
                name,
                agg: aggregatorParam[name],
                value: aggregateByParam[name],
            }))
            .map(appendUuid);
    }, [aggregateBy?.expression.expression, aggregator?.expression.expression, deserialize]);

    const [data, dataControls] = useArrayState<WithUuid<AggRow>>(() => parsed);

    useEffect(() => {
        const filtered = data.filter(({ name, agg, value }) => name && agg && value);
        const aggregator = Object.fromEntries(filtered.map(({ name, agg }) => [name, agg]));
        const aggregateBy = Object.fromEntries(filtered.map(({ name, value }) => [name, value]));
        setProperty(aggregatorPath, serialize("aggregator", aggregator));
        setProperty(aggregateByPath, serialize("aggregateBy", aggregateBy));
    }, [aggregateByPath, aggregatorPath, data, serialize, setProperty]);

    const onAdd = useCallback(() => {
        dataControls.push(
            appendUuid({
                name: "",
                agg: aggregators[0].expression,
                value: "",
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
    const variableTypes = useMemo(() => findAvailableVariables?.(id), [findAvailableVariables, id]);

    const items = useMemo(() => {
        return data.map((item, index) => ({
            item,
            el: (
                <FieldsRow uuid={item.uuid} index={index}>
                    <AggregatorFieldsStack value={item} onChange={onChangeItem} aggregators={aggregators} variableTypes={variableTypes} />
                </FieldsRow>
            ),
        }));
    }, [aggregators, onChangeItem, data, variableTypes]);

    useEffect(() => {
        if (data.length < 1) {
            onAdd();
        }
    }, [data.length, onAdd]);

    const readOnly = !props.isEditMode;
    return (
        <NodeRowFieldsProvider
            path=""
            label="aggregator"
            onFieldRemove={data.length > 1 && onRemove}
            onFieldAdd={onAdd}
            readOnly={readOnly}
            errors={props.showValidation ? errors : []}
        >
            <div className={cx({ marked: isMarked })}>
                <DndItems disabled={readOnly || data.length <= 1} items={items} onChange={dataControls.setArray} />
            </div>
        </NodeRowFieldsProvider>
    );
}
