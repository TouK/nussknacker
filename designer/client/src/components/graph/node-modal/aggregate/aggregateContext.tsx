import { get, uniqBy } from "lodash";
import React, { createContext, PropsWithChildren, useCallback, useMemo, useState } from "react";
import { NodeValidationError } from "../../../../types";
import { useParameterPath } from "../parameterHelpers";
import { ParametersListProps } from "../parametersList";
import { useDiffMark } from "../PathsToMark";
import { AggregateValue, AggRow, appendUuid } from "./aggregatorField";
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

        return keys
            .map<AggRow>((name) => ({
                name,
                agg: aggregatorParam?.[name],
                expression: aggregateByParam?.[name],
            }))
            .map(appendUuid);
    });

    const onAggChange = useCallback(
        (values: AggregateValue[]) => {
            setAggValues(values);
            const validValues = values.filter(({ name, agg, expression }) => name && agg && expression);
            const aggregator = Object.fromEntries(validValues.map(({ name, agg }) => [name, agg]));
            const aggregateBy = Object.fromEntries(validValues.map(({ name, expression }) => [name, expression]));
            setProperty(aggregatorPath, serializeAggregate("aggregator", aggregator));
            setProperty(aggregateByPath, serializeAggregate("aggregateBy", aggregateBy));
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
        const value: string = get(node, groupByPath) || serializeGroupBy("groupBy", []);
        return value ? deserializeGroupBy(value) : [];
    });

    const groupBy = useMemo(
        () => ({
            values: groupByValues,
            onChange: (values) => {
                setGroupByValues(values);
                setProperty(groupByPath, serializeGroupBy("groupBy", values));
            },
            isMarked: diffMark(groupByPath),
            fieldErrors: errors.filter(({ fieldName }) => ["groupBy"].includes(fieldName)),
        }),
        [diffMark, errors, groupByPath, groupByValues, serializeGroupBy, setProperty],
    );

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
    groupBy: FieldContext<string>;
}>({
    aggregator: { values: null },
    groupBy: { values: null },
});
