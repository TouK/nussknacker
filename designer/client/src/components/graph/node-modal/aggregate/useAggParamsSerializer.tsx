import { useCallback } from "react";

import { parseToList, parseToObject } from "./pareserHelpers";

export function useAggParamsSerializer(): [
    (text: string) => Record<string, string>,
    (paramName: string, map: Record<string, string>) => string,
] {
    const serialize = useCallback((paramName: string, map: Record<string, string>): string => {
        const entries = Object.entries(map || {}).map(([key, value]) => {
            const trimmedKey = key.trim();
            return [/^[^a-zA-Z]|\W/.test(trimmedKey) ? `"${trimmedKey}"` : trimmedKey, value];
        });

        const content = entries.map(([key, value]) => `${key}: ${value}`).join(",");

        switch (paramName) {
            case "aggregator":
                return `#AGG.map({${content}})`;
            case "aggregateBy":
                return `{${content}}`;
        }
    }, []);

    return [parseToObject, serialize];
}

export function useGroupByParamsSerializer(): [(text: string) => string[], (arr: string[]) => string] {
    const serialize = useCallback((arr: string[]): string => {
        const entries = arr.map((value) => {
            return value?.trim();
        });
        const content = entries.join(",");

        if (!content) return "";

        return `{${content}}`;
    }, []);

    const deserialize = useCallback((input: string) => {
        const parsed = parseToList(input);
        if (parsed !== null) return parsed;
        return [input];
    }, []);

    return [deserialize, serialize];
}
