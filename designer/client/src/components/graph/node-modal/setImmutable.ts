import { toPath } from "lodash";

import type { NormalizePath, Paths, PathValue } from "./typeHelpers";

export function setImmutable<T extends object, P = unknown>(
    object: T,
    path: P extends Paths<T> ? P : Paths<T>,
    value: PathValue<T, P extends Paths<T> ? P : typeof path>,
): T {
    try {
        return setImpl(object, normalizePathString(path) as string, value);
    } catch (e) {
        console.warn(`${e}, not changed.`);
        return object;
    }
}

function setImpl<T, P extends string, V>(object: T, path: P, value: V): T {
    const keys = toPath(path);
    if (keys.length === 0) return object;

    const [key, ...rest] = keys;
    const indexKey = isNaN(Number(key)) ? key : Number(key);
    const currentVal = (object as any)?.[indexKey];

    const nextVal = rest.length === 0 ? value : setImpl(currentVal ?? (isNaN(Number(rest[0])) ? {} : []), rest.join("."), value);

    return {
        ...object,
        [indexKey]: nextVal,
    } as T;
}

function normalizePathString<P extends string>(path: P): NormalizePath<P> {
    if (path.match(/\[]./) || path.includes(".#")) {
        throw "Invalid path: " + path;
    }
    return path.replace(/\[(\d+)]/g, ".$1").replace(/\[]$/g, "") as NormalizePath<P>;
}
