import { isEqual, omitBy, uniq, without } from "lodash";
import * as queryString from "query-string";
import { ParsedQuery, ParseOptions } from "query-string";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useLocation } from "react-router-dom";
import { ensureArray } from "../../common/arrayUtils";

type QueryRecord = ParsedQuery<string | boolean | number>;

const DEFAULT_ARRAY_FORMAT: ParseOptions["arrayFormat"] = "comma";

function parseQuery(searchString = window.location.search): QueryRecord {
    return queryString.parse(searchString, {
        arrayFormat: DEFAULT_ARRAY_FORMAT,
        parseNumbers: true,
        parseBooleans: true,
        decode: true,
    });
}

function stringifyQuery(params: QueryRecord): string {
    const resultParams = omitBy(params, (value) => value === undefined || isEqual(value, []));
    return queryString.stringify(resultParams, {
        arrayFormat: DEFAULT_ARRAY_FORMAT,
        encode: true,
    });
}

export function useSearchQuery<T extends QueryRecord>(): [T, (v: T) => void] {
    const location = useLocation();

    const [queryState, setQueryState] = useState(location.search);

    const parsed = useMemo(() => parseQuery(queryState) as T, [queryState]);

    const updateQuery = useCallback((value: T) => {
        setQueryState(() => setAndPreserveLocationParams(value));
    }, []);

    useEffect(() => {
        replaceSearchQuery(parsed);
    }, [parsed]);

    return [parsed, updateQuery];
}

export function replaceSearchQuery(value: QueryRecord): void;
export function replaceSearchQuery(update: (current: QueryRecord) => QueryRecord): void;
export function replaceSearchQuery(callback: ((current: QueryRecord) => QueryRecord) | QueryRecord): void {
    const { history, location } = window;
    const currentState = history.state || {};
    const currentURL = new URL(location.href);
    const params = typeof callback === "function" ? callback(parseQuery()) : callback;
    currentURL.search = stringifyQuery(params);
    history.replaceState(currentState, "", currentURL.href);
}

export function parseWindowsQueryParams<P extends Record<string, string | string[]>>(append: P, remove?: P): Record<string, string[]> {
    const query = parseQuery();
    const keys = uniq(Object.keys({ ...append, ...remove }));
    return Object.fromEntries(
        keys.map((key) => {
            const current = ensureArray(query[key]).map(String);
            const withAdded = uniq(current.concat(append?.[key]));
            const cleaned = without(withAdded, ...ensureArray(remove?.[key])).filter(Boolean);
            return [key, cleaned];
        }),
    );
}

export function setAndPreserveLocationParams<T extends Record<string, unknown>>(params: T): string {
    const currentParams = parseQuery();
    return stringifyQuery({ ...currentParams, ...params });
}
