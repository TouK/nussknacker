import { createHash } from "crypto";

import stringify from "fast-json-stable-stringify";

import type { Prettify } from "./useNodeTypeDetailsContentLogic";

export type WithTempId<T> = Prettify<T & { _id?: string }>;

function hashObject(obj: unknown): string {
    return createHash("sha256").update(stringify(obj)).digest("hex");
}

export const withTempId = <T,>(obj: WithTempId<T>, index: number): WithTempId<T> => {
    if (obj._id) return obj;
    return { ...obj, _id: hashObject({ obj, index }) };
};

export const withoutTempId = <T,>({ _id, ...obj }: WithTempId<T>): T => {
    return obj as T;
};
