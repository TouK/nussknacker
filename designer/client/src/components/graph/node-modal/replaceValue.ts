import { get, has, set } from "lodash";

import type { Paths, PathValue } from "./typeHelpers";
import { normalizePathString } from "./typeHelpers";

export function replaceValue<T extends object, P = unknown>(
    object: T,
    path: P extends Paths<T> ? P : Paths<T>,
    setter: (currentValue: PathValue<T, P extends Paths<T> ? P : typeof path>) => typeof currentValue,
    ignoreNonExisting = true,
): T {
    try {
        const normalizedPath = normalizePathString(path);
        if (ignoreNonExisting && !has(object, normalizedPath)) return object;
        const currentValue = get(object, normalizedPath);
        return set(object, normalizedPath, setter(currentValue));
    } catch (e) {
        console.warn(`${e}, not changed.`);
        return object;
    }
}
