/* eslint-disable i18next/no-literal-string */
import { isEqual, omitBy } from "lodash";
import * as queryString from "query-string";
import { ParseOptions } from "query-string";

export const defaultArrayFormat: ParseOptions["arrayFormat"] = "comma";

export function normalizeParams<T extends Record<any, any>>(object: T) {
    return queryString.parse(queryString.stringify(object, { arrayFormat: defaultArrayFormat })) as Record<keyof T, string>;
}

export function setAndPreserveLocationParams<T extends Record<string, any>>(params: T, arrayFormat = defaultArrayFormat): string {
    const queryParams = queryString.parse(window.location.search, { arrayFormat, parseNumbers: true });
    const merged = { ...queryParams, ...params };
    const resultParams = omitBy(merged, (value) => value === undefined || isEqual(value, []));
    return queryString.stringify(resultParams, { arrayFormat });
}
