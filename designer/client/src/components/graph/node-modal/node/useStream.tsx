import type { Emitter, Stream } from "kefir";
import { stream } from "kefir";
import { isEqual } from "lodash";
import { useEffect, useMemo, useRef, useState } from "react";

type Update<T> = T | ((prev: T) => T);

export function useStream<T, E = unknown>(
    initialValue?: T | ((c?: T) => T),
    autoUpdate?: boolean,
): [Stream<T, E>, (value: Update<T>) => void, T] {
    const [currentValue, setValue] = useState<T>(initialValue);
    const initialRef = useRef(currentValue);

    const { $values, emit } = useMemo(() => {
        let emitter: Emitter<Update<T>, E>;
        const input = stream<Update<T>, E>((_emitter) => {
            emitter = _emitter;
        });

        const $values = input
            .scan((prev, next) => (typeof next === "function" ? (next as (p: T) => T)(prev) : next), initialRef.current)
            .skipDuplicates(isEqual);

        const emit = (v: Update<T>) => emitter?.value(v);

        return { $values, emit };
    }, []);

    useEffect(() => {
        if (!autoUpdate) return;
        emit((current) => {
            const nextValue = typeof initialValue === "function" ? (initialValue as (c?: T) => T)(current) : initialValue;
            return isEqual(current, nextValue) ? current : nextValue;
        });
    }, [autoUpdate, emit, initialValue]);

    useEffect(() => $values.observe(setValue).unsubscribe, [$values]);

    return [$values, emit, currentValue];
}
