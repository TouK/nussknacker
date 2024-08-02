import React, { createContext, PropsWithChildren, useCallback, useMemo, useState } from "react";
import { ParametersListProps } from "../parametersList";
import { useParameterPath } from "../parameterHelpers";
import { get, padStart, uniqBy } from "lodash";
import { AggregateValue, AggRow, appendUuid } from "./aggregatorField";
import { useDiffMark } from "../PathsToMark";
import { NodeValidationError } from "../../../../types";
import { AggMapLikeLexer, AggMapLikeParser } from "./aggMapLikeParser";

function useAggParamsSerializer(): [(text: string) => Record<string, string>, (paramName: string, map: Record<string, string>) => string] {
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

        if (!content) return "";

        switch (paramName) {
            case "aggregator":
                return `#AGG.map({\n${content}\n})`;
            case "aggregateBy":
                return `{\n${content}\n}`;
        }
    }, []);
    return [deserialize, serialize];
}

type AggregateContextProviderProps = PropsWithChildren<Pick<ParametersListProps, "node" | "setProperty" | "errors">>;

export const AggregateContextProvider = ({ children, node, setProperty, errors }: AggregateContextProviderProps) => {
    const { parameters } = node;

    const [deserialize, serialize] = useAggParamsSerializer();

    const aggregatorPath = useParameterPath(parameters, "aggregator");
    const aggregateByPath = useParameterPath(parameters, "aggregateBy");

    const [values = [], setValues] = useState(() => {
        const aggregatorParam = deserialize(get(node, aggregatorPath));
        const aggregateByParam = deserialize(get(node, aggregateByPath));

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

    const onChange = useCallback(
        (values: AggregateValue[]) => {
            setValues(values);
            const aggregator = Object.fromEntries(values.map(({ name, agg }) => (name && agg ? [name, agg] : null)).filter(Boolean));
            const aggregateBy = Object.fromEntries(
                values.map(({ name, expression }) => (name && expression ? [name, expression] : null)).filter(Boolean),
            );
            setProperty(aggregatorPath, serialize("aggregator", aggregator));
            setProperty(aggregateByPath, serialize("aggregateBy", aggregateBy));
        },
        [aggregateByPath, aggregatorPath, serialize, setProperty],
    );

    const [diffMark] = useDiffMark();
    const isMarked = useMemo(() => diffMark(aggregatorPath) || diffMark(aggregateByPath), [aggregateByPath, aggregatorPath, diffMark]);

    const fieldErrors = useMemo(() => {
        const fieldErrors = errors.filter(({ fieldName }) => ["aggregator", "aggregateBy"].includes(fieldName));
        return uniqBy(fieldErrors, "message");
    }, [errors]);

    const value = useMemo(
        () => ({
            values,
            onChange,
            isMarked,
            fieldErrors,
        }),
        [fieldErrors, isMarked, onChange, values],
    );
    return <AggregateContext.Provider value={value}>{children}</AggregateContext.Provider>;
};

export const AggregateContext = createContext<{
    values: AggregateValue[];
    onChange?: (value: AggregateValue[]) => void;
    isMarked?: boolean;
    fieldErrors?: NodeValidationError[];
}>({ values: null });
