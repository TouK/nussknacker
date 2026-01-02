import { produce } from "immer";
import type { Scenario } from "src/components/Process/types";
import { v4 as uuid4 } from "uuid";

import type { WithUuid } from "../../../types/common";
import type { NodeType } from "../../../types/node";

export function appendUUID<T extends NonNullable<unknown>>(field: T & { uuid?: string }): WithUuid<T> {
    return produce(field, (draft) => {
        draft.uuid ||= uuid4();
    }) as WithUuid<T>;
}

export const generateUUIDs = produce((draft: NodeType) => {
    draft.parameters?.forEach(appendUUID);
    draft.fields?.forEach(appendUUID);
});

export function getNodeId(scenario: Scenario, node: NodeType): string {
    return scenario.isFragment ? node.id.replace(`${scenario.name}-`, "") : node.id;
}
