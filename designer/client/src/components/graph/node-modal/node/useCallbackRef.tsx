import type { MutableRefObject } from "react";
import { useCallback, useEffect, useRef } from "react";

export function useCallbackRef<C extends (...args: any) => any>(callback: C, deps: any[]): [MutableRefObject<C>, C] {
    // added to additionalHooks of react-hooks/exhaustive-deps
    // eslint-disable-next-line react-hooks/exhaustive-deps
    const _callback = useCallback(callback, deps);
    const callbackRef = useRef(_callback);

    useEffect(() => {
        callbackRef.current = _callback;
    }, [_callback]);

    return [callbackRef, _callback];
}

export function useStableCallback(fn) {
    const ref = useRef(fn);
    ref.current = fn;

    return useCallback((...args) => {
        return ref.current?.(...args);
    }, []);
}
