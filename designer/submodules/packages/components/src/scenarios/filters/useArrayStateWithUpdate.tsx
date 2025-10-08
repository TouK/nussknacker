import { useCallback, useEffect, useMemo, useRef } from "react";
import { useArrayState } from "rooks";

function isUpdater<T>(value: T | ((prev: T) => T)): value is (prev: T) => T {
    return typeof value === "function";
}

export function useArrayStateWithUpdate<T>(initial: T[] | (() => T[])) {
    const [array, arrayControls] = useArrayState<T>(initial);
    const elementsRef = useRef(array);
    useEffect(() => {
        elementsRef.current = array;
    }, [array]);

    const updateItemAtIndex = useCallback(
        (i: number, next: T | ((prevState: T) => T)) =>
            arrayControls.updateItemAtIndex(i, isUpdater(next) ? next(elementsRef.current[i]) : next),
        [arrayControls],
    );

    const controls = useMemo(() => ({ ...arrayControls, updateItemAtIndex }), [arrayControls, updateItemAtIndex]);
    return useMemo<[T[], typeof controls]>(() => [array, controls], [array, controls]);
}

export type ArrayStateControls<T> = ReturnType<typeof useArrayStateWithUpdate<T>>[1];
