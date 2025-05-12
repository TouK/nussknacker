import { produce } from "immer";
import { set } from "lodash";

import type { NormalizePath, Paths, PathValue } from "./typeHelpers";

export function setImmutable<T extends object, P = unknown>(
    object: T,
    path: P extends Paths<T> ? P : Paths<T>,
    value: PathValue<T, P extends Paths<T> ? P : typeof path>,
): T {
    return produce(object, (draft) => {
        try {
            return set(draft, normalizePathString(path), value);
        } catch (e) {
            console.warn(`${e}, not changed.`);
            return draft;
        }
    });
}

function normalizePathString<P extends string>(path: P): NormalizePath<P> {
    if (path.match(/\[]./) || path.match(/\.\[\d+]/g) || path.includes(".#")) {
        throw "Invalid path: " + path;
    }
    return path.replace(/\[(\d+)]/g, ".$1").replace(/\[]$/g, "") as NormalizePath<P>;
}
