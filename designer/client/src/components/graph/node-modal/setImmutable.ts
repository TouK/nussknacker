import { produce } from "immer";
import { set } from "lodash";

import type { Paths, PathValue } from "./typeHelpers";
import { normalizePathString } from "./typeHelpers";

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
