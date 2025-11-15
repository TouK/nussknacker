import { createHash } from "crypto";

import { isEqual } from "lodash";
import type { Dispatch, SetStateAction } from "react";
import { useCallback, useEffect, useState } from "react";

export type WithTempId<T> = T & { _id?: string };

function hashObject(obj: unknown): string {
    return createHash("sha256").update(JSON.stringify(obj)).digest("hex");
}

export const withTempId = <T,>(obj: WithTempId<T>): WithTempId<T> => {
    if (obj._id) return obj;
    return { ...obj, _id: hashObject(obj) };
};

export const withoutTempId = <T,>({ _id, ...obj }: WithTempId<T>): T => {
    return obj as T;
};

export function useStateWithTempId<T>(
    value: T[],
    onChange: (edges: T[]) => void,
): [WithTempId<T>[], Dispatch<SetStateAction<WithTempId<T>[]>>] {
    const [edges, _setEdges] = useState(() => value.map(withTempId));
    const setEdges = useCallback<typeof _setEdges>(
        (val) =>
            _setEdges((current) => {
                const next = typeof val === "function" ? val(current) : val;
                if (current === next) return current;
                onChange?.(next?.map(withoutTempId));
                return next;
            }),
        [onChange],
    );

    useEffect(() => {
        _setEdges((current) => (isEqual(current.map(withoutTempId), value) ? current : value));
    }, [value]);

    return [edges, setEdges];
}
