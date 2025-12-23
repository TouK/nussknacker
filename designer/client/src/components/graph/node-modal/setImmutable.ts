import { produce } from "immer";
import { get, isEqual, set } from "lodash";

import type { Paths, PathValue } from "./typeHelpers";
import { normalizePathString } from "./typeHelpers";

export function setImmutable<T extends object, P = unknown>(
    object: T,
    path: P extends Paths<T> ? P : Paths<T>,
    value: PathValue<T, P extends Paths<T> ? P : typeof path>,
): T {
    return produce(object, (draft) => {
        try {
            const pathString = normalizePathString(path);
            const current = get(draft, pathString);
            if (!isEqual(current, value)) {
                set(draft, pathString, value);
            }
        } catch (e) {
            console.warn(`${e}, not changed.`);
        }
    });
}
