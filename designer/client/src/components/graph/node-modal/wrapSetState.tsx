import { identity } from "lodash";
import type { SetStateAction } from "react";

export function wrapSetState<T>(action: SetStateAction<T>, transform: (value: T) => T = identity): SetStateAction<T> {
    function isPlainValue<T>(action: SetStateAction<T>): action is T {
        return typeof action !== "function";
    }

    return (prev) => {
        return isPlainValue(action) ? transform(action) : action(transform(prev));
    };
}
