import { defaults, isEqual, omitBy, uniq, without } from "lodash";
import * as queryString from "query-string";
import { ParseOptions } from "query-string";
import { useCallback, useMemo } from "react";
import { UnknownRecord } from "../../types/common";
import { useLocation, useNavigate } from "react-router-dom";
import { ensureArray } from "../../common/arrayUtils";

export function useSearchQuery<T extends UnknownRecord>(options?: ParseOptions): [T, (v: T) => void] {
    const navigate = useNavigate();
    const location = useLocation();

    const query = useMemo(() => {
        const parsedQuery = queryString.parse(location.search, defaults(options, { arrayFormat: defaultArrayFormat, parseBooleans: true }));
        return parsedQuery as T;
    }, [location.search, options]);

    const updateQuery = useCallback(
        (value: T) => {
            navigate({ search: setAndPreserveLocationParams(value) }, { replace: true });
        },
        [navigate],
    );

    return [query, updateQuery];
}

export function replaceSearchQuery(updateParams: (params: URLSearchParams) => URLSearchParams): void {
    const currentState = window.history.state || {};
    const currentURL = new URL(window.location.href);
    const currentSearchParams = new URLSearchParams(window.location.search);
    currentURL.search = updateParams(currentSearchParams).toString();
    window.history.replaceState(currentState, "", currentURL.href);
}

export function parseWindowsQueryParams<P extends Record<string, string | string[]>>(append: P, remove?: P): Record<string, string[]> {
    const query = queryString.parse(window.location.search, { arrayFormat: defaultArrayFormat });
    const keys = uniq(Object.keys({ ...append, ...remove }));
    return Object.fromEntries(
        keys.map((key) => {
            const current = ensureArray(query[key]).map(decodeURIComponent);
            const withAdded = uniq(current.concat(append?.[key]));
            const cleaned = without(withAdded, ...ensureArray(remove?.[key])).filter(Boolean);
            return [key, cleaned];
        }),
    );
}

export const defaultArrayFormat: ParseOptions["arrayFormat"] = "comma";

export function setAndPreserveLocationParams<T extends Record<string, any>>(params: T, arrayFormat = defaultArrayFormat): string {
    const queryParams = queryString.parse(window.location.search, { arrayFormat, parseNumbers: true });
    const merged = { ...queryParams, ...params };
    const resultParams = omitBy(merged, (value) => value === undefined || isEqual(value, []));
    return queryString.stringify(resultParams, { arrayFormat });
}
