import { isFunction } from "lodash";
import { useCallback, useEffect, useRef, useState } from "react";
import { useDebounceFn, useThrottle } from "rooks";

type UseStateLike<T> = [T, React.Dispatch<React.SetStateAction<T>>];

interface Options {
    mode?: "debounce" | "throttle";
    delay?: number;
}

export function useSyncedState<T>(remoteState: UseStateLike<T>, options: Options = {}): UseStateLike<T> {
    const { mode = "debounce", delay = 300 } = options;
    const [remoteValue, setRemoteValue] = remoteState;

    const [localValue, setLocalValue] = useState<T>(remoteValue);

    const localVersion = useRef(0);
    const remoteVersion = useRef(0);

    const [pushDebounced] = useDebounceFn((val: T, version: number) => {
        setRemoteValue(val);
        remoteVersion.current = version;
    }, delay);

    const [pushThrottled] = useThrottle((val: T, version: number) => {
        setRemoteValue(val);
        remoteVersion.current = version;
    }, delay);

    const pushUpdate = mode === "debounce" ? pushDebounced : pushThrottled;

    const setSyncedValue = useCallback(
        (value: React.SetStateAction<T>) => {
            setLocalValue((currentValue) => {
                const nextValue = isFunction(value) ? value(currentValue) : value;
                pushUpdate(nextValue, ++localVersion.current);
                return nextValue;
            });
        },
        [pushUpdate],
    );

    useEffect(() => {
        if (remoteVersion.current < localVersion.current) return;
        setLocalValue(remoteValue);
    }, [remoteValue]);

    return [localValue, setSyncedValue];
}
