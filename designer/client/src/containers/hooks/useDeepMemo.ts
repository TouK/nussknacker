import { isEqual } from "lodash";
import React, { useRef } from "react";

export function useDeepMemo<T>(factory: () => T, deps: React.DependencyList): T {
    const ref = useRef<{ value: T; deps: React.DependencyList }>();

    if (!ref.current || !isEqual(deps, ref.current.deps)) {
        ref.current = { value: factory(), deps };
    }

    return ref.current.value;
}
