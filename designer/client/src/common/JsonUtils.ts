import type { IFlattened } from "flattenizer";
import { flatten } from "flattenizer";
import { isEqual, isObject, mapKeys, transform } from "lodash";

export function tryParseOrNull<T = any>(input: string): T | null {
    try {
        return JSON.parse(input) as T;
    } catch (e) {
        return null;
    }
}

//Flattenizer is nice, but unfortunately its array notation is different than Lodash's,
// so we use regex to make it consistent with Lodash'a array notation here
export function flattenObj<T>(obj: T): IFlattened<T> {
    const flatObj = flatten(obj);
    const flattenizerArrayNotation = new RegExp(/\.([0-9][0-9]*)\./);
    return mapKeys(flatObj, (value, key) => {
        const matchResult = key.match(flattenizerArrayNotation);
        if (matchResult) {
            const arrayIndex = matchResult[1];
            return key.replace(flattenizerArrayNotation, `[${arrayIndex}].`);
        }
        return key;
    });
}

type DeepPartial<T> = {
    [K in keyof T]?: T[K] extends object ? DeepPartial<T[K]> : T[K];
};

export function objectDiff<O, B>(object: O, base: B): DeepPartial<O> {
    return transform<any, any>(object, (result, value, key) => {
        if (base && !isEqual(value, base[key])) {
            result[key] = isObject(value) && isObject(base[key]) ? objectDiff(value, base[key]) : value;
        }
    });
}
