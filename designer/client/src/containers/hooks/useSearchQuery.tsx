import { isEqual, omitBy, uniq, without } from "lodash";
import type { ParsedQuery, ParseOptions } from "query-string";
import { parse, stringify } from "query-string";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useLocation } from "react-router-dom";

import { ensureArray } from "../../common/arrayUtils";

type QueryParamValue = string | boolean | number;

type QueryRecord = ParsedQuery<QueryParamValue>;

const DEFAULT_ARRAY_FORMAT: ParseOptions["arrayFormat"] = "comma";

export function parseQuery(searchString = window.location.search): QueryRecord {
    const parsedRawQuery = parse(searchString, {
        arrayFormat: DEFAULT_ARRAY_FORMAT,
        parseNumbers: true,
        parseBooleans: true,
        decode: false,
    });

    return Object.fromEntries(
        Object.entries(parsedRawQuery).map(([key, value]) => {
            const decodedKey = decodeURIComponent(key);
            if (value === null) {
                return [decodedKey, null];
            }
            if (Array.isArray(value)) {
                return [decodedKey, value.map((v) => decodeQueryParamValue(v))];
            }

            return [decodedKey, decodeQueryParamValue(value)];
        }),
    );
}

function decodeQueryParamValue(value: QueryParamValue): QueryParamValue {
    if (typeof value === "string") {
        return decodeURIComponent(value);
    } else {
        return value;
    }
}

function encodeQueryParamValue(value: QueryParamValue): QueryParamValue {
    if (typeof value === "string") {
        return encodeURIComponent(value);
    } else {
        return value;
    }
}

export function stringifyQuery(params: QueryRecord): string {
    const resultParams = omitBy(params, (value) => value === undefined || isEqual(value, []));
    // Manual encoding due to issues with 'query-string' library.
    // The library does not encode query params correctly when the parameter value contains the array delimiter.
    // When the array delimiter (comma in our case) is used in the parameter value, it's not encoded, and the parameter value is split into multiple values
    const encodedParams = Object.fromEntries(
        Object.entries(resultParams).map(([key, value]) => {
            if (Array.isArray(value)) {
                return [encodeURIComponent(key), value.map((v) => encodeQueryParamValue(v))];
            }
            return [encodeURIComponent(key), encodeQueryParamValue(value)];
        }),
    );
    return stringify(encodedParams, {
        arrayFormat: DEFAULT_ARRAY_FORMAT,
        encode: false,
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
