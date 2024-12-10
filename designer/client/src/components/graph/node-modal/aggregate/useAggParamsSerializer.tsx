import { padStart } from "lodash";
import { useCallback, useMemo } from "react";
import { AggMapLikeParser } from "./aggMapLikeParser";

export function useAggParamsSerializer(): [
    (text: string) => Record<string, string>,
    (paramName: string, map: Record<string, string>) => string,
] {
    const parser = useMemo(() => new AggMapLikeParser(), []);

    const deserialize = useCallback((input: string) => parser.parseObject(input), [parser]);

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

    return [deserialize, serialize];
}

export function useGroupByParamsSerializer(): [(text: string) => string[], (paramName: string, arr: string[]) => string] {
    const parser = useMemo(() => new AggMapLikeParser(), []);

    const deserialize = useCallback((input: string) => parser.parseList(input), [parser]);

    const serialize = useCallback((paramName: string, arr: string[]): string => {
        const entries = arr.map((value) => {
            return value?.trim();
        });

        const content = entries.join(", ");

        if (!content) return "";

        switch (paramName) {
            case "groupBy":
                return `{ ${content} }.toString`;
        }
    }, []);

    return [deserialize, serialize];
}
