import type { Draft } from "immer";
import { produce } from "immer";
import { v4 as uuid4 } from "uuid";

import type { WithId } from "../../../types/common";
import type { NodeType } from "../../../types/node";

export type WithUuid<T> = WithId<T, "uuid">;

const appendUuid = <T extends NonNullable<unknown>>(field: Draft<T & { uuid?: string }>): void => {
    field.uuid ||= uuid4();
};

export function withUuid<T extends NonNullable<unknown>>(field: T & { uuid?: string }): WithUuid<T> {
    return produce(field, appendUuid) as WithUuid<T>;
}

export function appendUuidToArrayElements<T extends NonNullable<unknown>[]>(draft: Draft<T>): void {
    draft?.forEach(appendUuid);
}

export const appendUuidToParameters = (draft: Draft<NodeType>): void => {
    appendUuidToArrayElements(draft?.parameters);
    appendUuidToArrayElements(draft?.fields);
};
