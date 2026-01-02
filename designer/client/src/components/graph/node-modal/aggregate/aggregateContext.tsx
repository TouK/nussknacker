import { get, isEqual, uniqBy } from "lodash";
import type { PropsWithChildren } from "react";
import React, { createContext, useCallback, useMemo, useState } from "react";

import type { NodeValidationError } from "../../../../types/validation";
import { withUuid } from "../appendUuid";
import { useParameterPath } from "../parameterHelpers";
import type { ParametersListProps } from "../parametersList";
import { useDiffMark } from "../PathsToMark";
import type { AggregateValue, AggRow } from "./aggregatorField";
import { useAggParamsSerializer, useGroupByParamsSerializer } from "./useAggParamsSerializer";

type AggregateContextProviderProps = PropsWithChildren<Pick<ParametersListProps, "node" | "setProperty" | "errors">>;

export const AggregateContextProvider = ({ children, node, setProperty, errors }: AggregateContextProviderProps) => {
    const { parameters } = node;

    const [deserializeGroupBy, serializeGroupBy] = useGroupByParamsSerializer();
    const [deserializeAggregate, serializeAggregate] = useAggParamsSerializer();

    const groupByPath = useParameterPath(parameters, "groupBy");
    const aggregatorPath = useParameterPath(parameters, "aggregator");
    const aggregateByPath = useParameterPath(parameters, "aggregateBy");

    const [aggValues = [], setAggValues] = useState(() => {
        const aggregatorParam = deserializeAggregate(get(node, aggregatorPath));
        const aggregateByParam = deserializeAggregate(get(node, aggregateByPath));

        const keys = Object.keys({ ...aggregateByParam, ...aggregatorParam });

        if (!keys.length) {
            return null;
        }

        return keys.map((name) =>
            withUuid<AggRow>({
                name,
                agg: aggregatorParam?.[name],
                expression: aggregateByParam?.[name],
            }),
        );
    });

    const onAggChange = useCallback(
        (values: AggregateValue[]) => {
            setAggValues((currentValues) => {
                if (isEqual(currentValues, values)) return currentValues;
                const validValues = values.filter(({ name, agg, expression }) => name && agg && expression);
                const aggregator = Object.fromEntries(validValues.map(({ name, agg }) => [name, agg]));
                const aggregateBy = Object.fromEntries(validValues.map(({ name, expression }) => [name, expression]));
                setProperty(aggregatorPath, serializeAggregate("aggregator", aggregator));
                setProperty(aggregateByPath, serializeAggregate("aggregateBy", aggregateBy));
                return values;
            });
        },
        [aggregateByPath, aggregatorPath, serializeAggregate, setProperty],
    );

    const [diffMark] = useDiffMark();

    const aggregator = useMemo(
        () => ({
            values: aggValues,
            onChange: onAggChange,
            isMarked: diffMark(aggregatorPath) || diffMark(aggregateByPath),
            fieldErrors: uniqBy(
                errors.filter(({ fieldName }) => ["aggregator", "aggregateBy"].includes(fieldName)),
                "message",
            ),
        }),
        [aggregateByPath, aggregatorPath, diffMark, errors, onAggChange, aggValues],
    );

    const [groupByValues, setGroupByValues] = useState<string[]>(() => {
        const value: string = get(node, groupByPath) || serializeGroupBy([]);
        return value ? deserializeGroupBy(value) : [];
    });

    const onGroupByChange = useCallback(
        (values: string[]) => {
            setGroupByValues((currentValues) => {
                if (isEqual(currentValues, values)) return currentValues;
                setProperty(groupByPath, serializeGroupBy(values));
                return values;
            });
        },
        [groupByPath, serializeGroupBy, setProperty],
    );

    const groupBy = useMemo(() => {
        const errorsByValue = new Map<string, NodeValidationError[]>();
        const fieldErrors = errors.filter(({ fieldName }) => ["groupBy"].includes(fieldName));
        const expression: string = serializeGroupBy(groupByValues);

        let cursor = 0;
        const ranges = groupByValues.map((value) => {
            const from = expression.indexOf(value, cursor);
            const to = from + value.length;
            cursor = from !== -1 ? to : cursor;
            return { value, from, to };
        });

        fieldErrors.forEach((e) => {
            const details = e.details;
            if (details?.type !== "CoordinatesBasedTextRange") return;

            const range = ranges.find((r) => r.from <= details.start.column && r.to >= details.end.column);
            if (!range) return;

            if (errorsByValue.has(range.value)) {
                errorsByValue.get(range.value).push(e);
            } else {
                errorsByValue.set(range.value, [e]);
            }
        });

        return {
            values: groupByValues,
            onChange: onGroupByChange,
            isMarked: diffMark(groupByPath),
            fieldErrors: fieldErrors,
            errorsByValue,
        };
    }, [diffMark, errors, groupByPath, groupByValues, onGroupByChange, serializeGroupBy]);

    return (
        <AggregateContext.Provider
            value={{
                aggregator,
                groupBy,
            }}
        >
            {children}
        </AggregateContext.Provider>
    );
};

type FieldContext<T> = {
    values: T[] | null;
    onChange?: (value: T[]) => void;
    isMarked?: boolean;
    fieldErrors?: NodeValidationError[];
};

export const AggregateContext = createContext<{
    aggregator: FieldContext<AggregateValue>;
    groupBy: FieldContext<string> & { errorsByValue: Map<string, NodeValidationError[]> };
}>({
    aggregator: { values: null },
    groupBy: { values: null, errorsByValue: new Map() },
});
